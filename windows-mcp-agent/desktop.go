package main

import (
 "bytes"
 "context"
 "crypto/rand"
 "encoding/hex"
 "encoding/json"
 _ "embed"
 "errors"
 "fmt"
 "os"
 "os/exec"
 "path/filepath"
 "runtime"
 "strings"
 "time"
)

//go:embed desktop_worker.ps1
var desktopWorker string

type desktopRequest struct {
 SnapshotID string `json:"snapshot_id"`
 ElementID string `json:"element_id"`
 Action string `json:"action"`
 Text *string `json:"text,omitempty"`
}
type desktopElement struct {
 ID string `json:"id"`
 Identity json.RawMessage `json:"identity"`
 Name string `json:"name"`
 Type string `json:"type"`
 AutomationID string `json:"automation_id"`
 Patterns []string `json:"patterns"`
}
type desktopSnapshot struct {
 ID string
 Created time.Time
 Window json.RawMessage
 Elements []desktopElement
}
type desktopOutput struct {
 OK bool `json:"ok"`
 Error string `json:"error,omitempty"`
 Window json.RawMessage `json:"window,omitempty"`
 Elements []desktopElement `json:"elements,omitempty"`
 Truncated bool `json:"truncated,omitempty"`
 Dispatched bool `json:"dispatched,omitempty"`
 Verified bool `json:"verified,omitempty"`
 Action string `json:"action,omitempty"`
}
func decodeDesktop(raw json.RawMessage) (desktopRequest,error) {
 var p desktopRequest
 if len(raw)==0 {raw=json.RawMessage("{}")}
 d:=json.NewDecoder(bytes.NewReader(raw));d.DisallowUnknownFields()
 if err:=d.Decode(&p);err!=nil {return p,errors.New("invalid desktop parameters; coordinates and arbitrary keys are not accepted")}
 if len([]rune(stringValue(p.Text)))>4096 {return p,errors.New("text exceeds 4096 characters")}
 return p,nil
}
func stringValue(p *string) string {if p==nil{return ""};return *p}
func desktopTarget(s *desktopSnapshot,p desktopRequest,now time.Time)(json.RawMessage,error){
 if s==nil||s.ID!=p.SnapshotID||now.Before(s.Created)||now.Sub(s.Created)>90*time.Second{return nil,errors.New("unknown or expired desktop snapshot; inspect again")}
 if p.Action!="invoke"&&p.Action!="set_text"&&p.Action!="select"&&p.Action!="toggle"{return nil,errors.New("unsupported desktop action")}
 if p.Action=="set_text"&&p.Text==nil{return nil,errors.New("set_text requires text")}
 if p.Action!="set_text"&&p.Text!=nil{return nil,errors.New("text only accepted for set_text")}
 var target json.RawMessage
 count:=0
 for _,e:=range s.Elements {if e.ID==p.ElementID{target=e.Identity;count++}}
 if count!=1{return nil,errors.New("missing or ambiguous element ID")}
 return target,nil
}
func (a *Agent) startDesktopTask(operation string,raw json.RawMessage)(any,error){
 if runtime.GOOS!="windows" {return nil,errors.New("desktop control requires Windows")}
 if os.Getenv("JARVIS_DESKTOP_ENABLED")!="1"{return nil,errors.New("desktop control disabled")}
 if operation!="desktop_inspect"&&operation!="desktop_action"{return nil,errors.New("unsupported desktop operation")}
 p,err:=decodeDesktop(raw);if err!=nil{return nil,err}
 if operation=="desktop_inspect"&&(p.SnapshotID!=""||p.ElementID!=""||p.Action!=""||p.Text!=nil){return nil,errors.New("inspect takes no action parameters")}
 a.mu.Lock();defer a.mu.Unlock()
 if a.task!=nil&&a.task.Status=="running"{return nil,errors.New("a task is already running")}
 input:=map[string]any{"operation":operation}
 if operation=="desktop_action"{
  s:=a.desktop
  // Any action attempt consumes the snapshot before dispatch. Never retry uncertainty.
  a.desktop=nil
  target,err:=desktopTarget(s,p,time.Now());if err!=nil{return nil,err}
  input["window"]=s.Window;input["target"]=target;input["action"]=p.Action
  if p.Text!=nil{input["text"]=*p.Text}
 } else {a.desktop=nil}
 a.next++
 ctx,cancel:=context.WithTimeout(context.Background(),15*time.Second)
 t:=&Task{ID:fmt.Sprintf("task-%d",a.next),Name:operation,Status:"running",StartedAt:time.Now().Format(time.RFC3339),cancel:cancel}
 a.task=t
 go func(){
  defer cancel()
  out,err:=runDesktopWorker(ctx,input)
  if err!=nil{
   state:="failed";if ctx.Err()!=nil{state="cancelled"}
   // An interrupted UIA call may already have acted: do not infer no action.
   a.completeGoTask(t,state,"desktop outcome uncertain or unavailable; inspect before any further action")
   return
  }
  if !out.OK {msg:="desktop refused: "+out.Error;if out.Error=="provider_error_or_uncertain_outcome"{msg="desktop outcome uncertain; inspect and verify before any further action"};a.completeGoTask(t,"failed",msg);return}
  result:=map[string]any{"ok":true,"operation":operation,"dispatched":out.Dispatched,"verified":out.Verified,"action":out.Action}
  if operation=="desktop_inspect"{
   token:=make([]byte,16);if _,err=rand.Read(token);err!=nil{a.completeGoTask(t,"failed","cannot create snapshot");return}
   s:=&desktopSnapshot{ID:hex.EncodeToString(token),Created:time.Now(),Window:out.Window,Elements:out.Elements}
   a.mu.Lock();a.desktop=s;a.mu.Unlock()
   // Identity internals are kept only in memory; returned IDs select, never match by position/name.
   elements:=[]map[string]any{}
   for _,e:=range out.Elements{elements=append(elements,map[string]any{"id":e.ID,"name":e.Name,"type":e.Type,"automation_id":e.AutomationID,"patterns":e.Patterns})}
   result["snapshot_id"]=s.ID;result["expires_seconds"]=90;result["window"]=out.Window;result["elements"]=elements;result["truncated"]=out.Truncated
  }
  b,_:=json.Marshal(result);a.completeGoTask(t,"completed",string(b))
 }()
 return map[string]any{"task_id":t.ID,"operation":operation,"status":"running"},nil
}
type desktopBoundedOutput struct { b bytes.Buffer; overflow bool }
func (w *desktopBoundedOutput) Write(p []byte)(int,error){
 n:=len(p);space:=65536-w.b.Len();if len(p)>space{p=p[:space];w.overflow=true};_,_=w.b.Write(p);return n,nil
}
func runDesktopWorker(ctx context.Context,input any)(desktopOutput,error){
 var out desktopOutput
 dir,err:=os.MkdirTemp("","jarvis-uia-");if err!=nil{return out,err};defer os.RemoveAll(dir)
 path:=filepath.Join(dir,"worker.ps1")
 if err=os.WriteFile(path,[]byte(desktopWorker),0600);err!=nil{return out,err}
 ps:=filepath.Join(os.Getenv("SystemRoot"),"System32","WindowsPowerShell","v1.0","powershell.exe")
 cmd:=exec.CommandContext(ctx,ps,"-NoLogo","-NoProfile","-NonInteractive","-STA","-ExecutionPolicy","Bypass","-File",path)
 data,err:=json.Marshal(input);if err!=nil{return out,err}
 cmd.Stdin=bytes.NewReader(data)
 // Do not pass agent credentials/configuration to the UI worker.
 for _,e:=range os.Environ(){key,_,_:=strings.Cut(e,"=");switch strings.ToUpper(key){case "SYSTEMROOT","WINDIR","USERPROFILE","TEMP","TMP","LOCALAPPDATA","APPDATA","PATH","COMSPEC":cmd.Env=append(cmd.Env,e)}}
 output:=&desktopBoundedOutput{};cmd.Stdout=output;cmd.Stderr=&bytes.Buffer{}
 cmd.WaitDelay=time.Second
 if err=cmd.Run();err!=nil||output.overflow{return out,errors.New("desktop worker failed or output exceeded limit")}
 if err=json.Unmarshal(output.b.Bytes(),&out);err!=nil{return out,errors.New("invalid desktop worker response")}
 return out,nil
}
