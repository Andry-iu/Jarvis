package main

import (
 "crypto/sha256"
 "crypto/subtle"
 "encoding/hex"
 "errors"
 "fmt"
 "os"
 "os/exec"
 "path/filepath"
 "regexp"
 "runtime"
 "strings"
 "sync"
)

var updateIDPattern=regexp.MustCompile("^[a-fA-F0-9]{32}$")
var updateHashPattern=regexp.MustCompile("^[a-fA-F0-9]{64}$")

type updateRequest struct {
 UpdateID string `json:"update_id"`
 SHA256 string `json:"sha256"`
 Content string `json:"content"`
}
type preparedUpdate struct { ID, Hash, Path string }
var updates=struct{
 sync.Mutex
 pending *preparedUpdate
}{}

func updateRoot()(string,error){
 home:=os.Getenv("JARVIS_HOME")
 if home=="" { home=`D:\Jarvis` }
 if !filepath.IsAbs(home) { return "",errors.New("JARVIS_HOME must be an absolute path") }
 return filepath.Clean(home),nil
}
func prepareUpdate(r updateRequest)(any,error){
 if runtime.GOOS!="windows" {return nil,errors.New("updates require Windows")}
 if !updateIDPattern.MatchString(r.UpdateID)||!updateHashPattern.MatchString(r.SHA256){return nil,errors.New("invalid update identifier or SHA-256")}
 raw:=[]byte(r.Content)
 if len(raw)<1||len(raw)>262144{return nil,errors.New("batch size out of range")}
 prefix:="REM JARVIS-UPDATE-ID: "+r.UpdateID+"
"
 if !strings.HasPrefix(r.Content,prefix) {return nil,errors.New("invalid first REM line or line endings")}
 if strings.ContainsRune(r.Content,0){return nil,errors.New("NUL byte forbidden")}
 hash:=sha256.Sum256(raw)
 want,_:=hex.DecodeString(r.SHA256)
 if subtle.ConstantTimeCompare(hash[:],want)!=1{return nil,errors.New("SHA-256 mismatch")}
 root,err:=updateRoot();if err!=nil{return nil,err}
 dir:=filepath.Join(root,"Scripts","updates")
 if err=os.MkdirAll(dir,0700);err!=nil{return nil,err}
 path:=filepath.Join(dir,r.UpdateID+".cmd")
 updates.Lock();defer updates.Unlock()
 f,err:=os.OpenFile(path,os.O_WRONLY|os.O_CREATE|os.O_EXCL,0600)
 if err!=nil{return nil,fmt.Errorf("cannot stage batch (update ID must be new): %w",err)}
 n,werr:=f.Write(raw);closeErr:=f.Close()
 if werr!=nil||closeErr!=nil||n!=len(raw){_ = os.Remove(path);return nil,errors.New("failed to write entire batch")}
 written,err:=os.ReadFile(path)
 if err!=nil||subtle.ConstantTimeCompare(written,raw)!=1{_ = os.Remove(path);return nil,errors.New("staged bytes differ")}
 updates.pending=&preparedUpdate{r.UpdateID,strings.ToLower(r.SHA256),path}
 return map[string]any{"state":"prepared","update_id":r.UpdateID,"sha256":strings.ToLower(r.SHA256),"path":path},nil
}
func executeUpdate(id,expected string)(any,error){
 if runtime.GOOS!="windows" {return nil,errors.New("updates require Windows")}
 updates.Lock();defer updates.Unlock()
 p:=updates.pending
 if p==nil||id!=p.ID||!updateHashPattern.MatchString(expected){return nil,errors.New("no matching prepared update")}
 if subtle.ConstantTimeCompare([]byte(strings.ToLower(expected)),[]byte(p.Hash))!=1{return nil,errors.New("wrong expected hash")}
 raw,err:=os.ReadFile(p.Path);if err!=nil{return nil,err}
 hash:=sha256.Sum256(raw)
 if subtle.ConstantTimeCompare([]byte(hex.EncodeToString(hash[:])),[]byte(p.Hash))!=1{return nil,errors.New("staged batch altered")}
 if !strings.HasPrefix(string(raw),"REM JARVIS-UPDATE-ID: "+p.ID+"
"){return nil,errors.New("REM update ID mismatch")}
 // Use START without /B so the batch gets a separate console and survives agent exit.
 // The script must implement stop, backup, install, restart, health test and rollback.
 cmd:=exec.Command("cmd.exe","/C","start","""","/MIN","cmd.exe","/D","/C",p.Path)
 cmd.Dir=filepath.Dir(p.Path)
 if err=cmd.Start();err!=nil{return nil,err}
 pid:=cmd.Process.Pid
 _=cmd.Process.Release()
 updates.pending=nil
 return map[string]any{"state":"launched","update_id":id,"launcher_pid":pid,"message":"Batch launch requested; verify new agent after reconnect"},nil
}
