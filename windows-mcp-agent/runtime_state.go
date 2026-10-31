package main

import (
 "crypto/rand"
 "crypto/sha256"
 "encoding/hex"
 "encoding/json"
 "errors"
 "fmt"
 "os"
 "path/filepath"
 "regexp"
 "strings"
 "sync"
 "time"
)

// Runtime state is private to one agent instance. An OS lock prevents competing
// instances from accepting the same request. A process crash releases the lock.
type Runtime struct {
 mu sync.Mutex
 dir, MachineID string
 lock *os.File
 Jobs map[string]*Job
 codex *codexClient
}
type Job struct {
 ID string `json:"id"`
 MachineID string `json:"machine_id"`
 Project string `json:"project"`
 Kind string `json:"kind"`
 Status string `json:"status"`
 CreatedAt string `json:"created_at"`
 FinishedAt string `json:"finished_at,omitempty"`
 Output string `json:"output,omitempty"`
 DroppedBytes int64 `json:"dropped_bytes,omitempty"`
 ExitCode *int `json:"exit_code,omitempty"`
 cancel func()
}
type Receipt struct {
 ID string `json:"request_id"`
 Digest string `json:"digest"`
 Operation string `json:"operation"`
 Project string `json:"project"`
 Path string `json:"path,omitempty"`
 Destination string `json:"destination,omitempty"`
 Before string `json:"before,omitempty"`
 After string `json:"after,omitempty"`
 Backup string `json:"backup,omitempty"`
 CreatedPaths []string `json:"created_paths,omitempty"`
 Status string `json:"status"`
 Result json.RawMessage `json:"result,omitempty"`
 Error string `json:"error,omitempty"`
 At string `json:"at"`
}
var requestIDPattern=regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9_-]{15,63}$`)
func newID() string { b:=make([]byte,16); if _,e:=rand.Read(b);e!=nil{panic("OS random source unavailable")};return hex.EncodeToString(b) }
func digest(b []byte) string { h:=sha256.Sum256(b);return hex.EncodeToString(h[:]) }
func utcNow() string{return time.Now().UTC().Format(time.RFC3339Nano)}
func atomicJSON(path string,value any) error {
 b,e:=json.Marshal(value);if e!=nil{return e}
 f,e:=os.CreateTemp(filepath.Dir(path),".pending-");if e!=nil{return e}
 n:=f.Name();defer os.Remove(n)
 if e=f.Chmod(0600);e==nil{_,e=f.Write(b)}
 if e==nil{e=f.Sync()};ce:=f.Close();if e==nil{e=ce};if e!=nil{return e}
 return os.Rename(n,path)
}
func newRuntime(dir string)(*Runtime,error){
 if !filepath.IsAbs(dir){return nil,errors.New("runtime state directory must be absolute")}
 if root,e:=projectsRoot();e==nil{rel,err:=filepath.Rel(root,dir);if err==nil&&rel!=".."&&!strings.HasPrefix(rel,".."+string(filepath.Separator)){return nil,errors.New("runtime state must remain outside the authorized projects root")}}
 if e:=os.MkdirAll(dir,0700);e!=nil{return nil,e}
 if e:=protectRuntimeDirectory(dir);e!=nil{return nil,e}
 lock,e:=lockRuntime(filepath.Join(dir,"agent.lock"));if e!=nil{return nil,e}
 r:=&Runtime{dir:dir,lock:lock,Jobs:map[string]*Job{}}
 ok:=false;defer func(){if !ok{lock.Close()}}()
 for _,n:=range []string{"requests","jobs","backups","threads","events"}{if e=os.MkdirAll(filepath.Join(dir,n),0700);e!=nil{return nil,e}}
 idpath:=filepath.Join(dir,"machine.json")
 var identity struct{ ID string `json:"machine_id"` }
 b,e:=os.ReadFile(idpath)
 if os.IsNotExist(e){identity.ID=newID();e=atomicJSON(idpath,identity)}else if e==nil{e=json.Unmarshal(b,&identity)}
 if e!=nil||!requestIDPattern.MatchString(identity.ID){return nil,errors.New("invalid or unavailable machine identity; do not silently replace state")}
 r.MachineID=identity.ID
 entries,e:=os.ReadDir(filepath.Join(dir,"jobs"));if e!=nil{return nil,e}
 for _,de:=range entries{
  if de.IsDir()||filepath.Ext(de.Name())!=".json"{continue}
  var j Job;b,e=os.ReadFile(filepath.Join(dir,"jobs",de.Name()));if e!=nil{return nil,e};if e=json.Unmarshal(b,&j);e!=nil{return nil,fmt.Errorf("corrupt job state: %s",de.Name())}
  if j.Status=="running"||j.Status=="queued"{j.Status="interrupted";j.FinishedAt=utcNow();j.Output+="\nAgent restarted; execution outcome requires verification. No automatic replay.";if e=r.saveJob(&j);e!=nil{return nil,e}}
  r.Jobs[j.ID]=&j
 }
 ok=true;return r,nil
}
func (a *Agent)getRuntime()(*Runtime,error){
 a.runtimeMu.Lock();defer a.runtimeMu.Unlock()
 if a.runtime!=nil{return a.runtime,nil}
 dir:=os.Getenv("JARVIS_STATE_DIR");if dir==""{home:=os.Getenv("JARVIS_HOME");if home==""{return nil,errors.New("JARVIS_HOME unavailable")};dir=filepath.Join(home,"State","runtime-v1")}
 r,e:=newRuntime(dir);if e==nil{a.runtime=r};return r,e
}
func(a *Agent)closeRuntime(){a.runtimeMu.Lock();defer a.runtimeMu.Unlock();if a.runtime!=nil{a.runtime.Close();a.runtime=nil}}
func(r *Runtime)Close(){r.mu.Lock();for _,j:=range r.Jobs{if j.cancel!=nil{j.cancel()}};c:=r.codex;r.mu.Unlock();if c!=nil{c.Close()};if r.lock!=nil{r.lock.Close();r.lock=nil}}
func(r *Runtime)saveJob(j *Job)error{return atomicJSON(filepath.Join(r.dir,"jobs",j.ID+".json"),j)}
func(r *Runtime)saveReceipt(x *Receipt)error{return atomicJSON(filepath.Join(r.dir,"requests",x.ID+".json"),x)}
// Write-ahead receipt: once dispatched, the ID is never retried automatically,
// including when a process dies between the effect and recording its result.
func(r *Runtime)mutation(p runtimeRequest,fn func(*Receipt)(any,error))(any,error){
 if !requestIDPattern.MatchString(p.RequestID){return nil,errors.New("a unique request_id of 16-64 safe characters is required")}
 r.mu.Lock();defer r.mu.Unlock()
 b,_:=json.Marshal(p);h:=digest(b);path:=filepath.Join(r.dir,"requests",p.RequestID+".json")
 if b,e:=os.ReadFile(path);e==nil{var old Receipt;if json.Unmarshal(b,&old)!=nil{return nil,errors.New("corrupt receipt")};if old.Digest!=h{return nil,errors.New("request_id reused with different arguments")};if old.Status=="completed"{var v any;json.Unmarshal(old.Result,&v);return v,nil};return nil,fmt.Errorf("request already recorded as %s: %s; inspect receipt before proceeding",old.Status,old.Error)}else if !os.IsNotExist(e){return nil,e}
 x:=&Receipt{ID:p.RequestID,Digest:h,Operation:p.Operation,Project:p.Project,Path:p.Path,Destination:p.Destination,Status:"dispatched",At:utcNow()}
 if e:=r.saveReceipt(x);e!=nil{return nil,e}
 v,e:=fn(x)
 if e!=nil{x.Status="uncertain_or_refused";x.Error=e.Error()}else{x.Status="completed";x.Result,_=json.Marshal(v)}
 if se:=r.saveReceipt(x);se!=nil{return nil,errors.New("result persistence failed; outcome uncertain; do not replay")}
 return v,e
}
