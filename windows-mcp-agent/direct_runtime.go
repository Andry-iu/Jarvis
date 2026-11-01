package main

import (
 "context"
 "encoding/json"
 "errors"
 "fmt"
 "os"
 "path/filepath"
 "strings"
 "time"
)

var directToolSpecs=[]runtimeTool{
 {"maintenance_status","Read log/batch retention, 24-hour schedule, last check and cleanup counts. Prepared/running update batches are excluded.",nil,nil,true},
 {"known_folder","Resolve the current user's real Desktop, Documents or Downloads path, including redirection.",[]string{"folder"},[]string{"folder"},true},
 {"process_start","Start an executable directly with separate arguments and working directory. Non-administrator by default even when the agent is elevated; fail if a verified standard token is unavailable. elevated=true only on explicit user request. request_id prevents duplicate launches.",[]string{"executable","args","working_directory","elevated","request_id"},[]string{"executable","request_id"},false},
 {"window_list","List visible windows on the unlocked interactive desktop, with opaque IDs bound to process identity. Does not change focus.",nil,nil,true},
 {"window_inspect","Inspect one window by its returned window_id, including classic Edit and modern Document controls. Returns a one-use 90-second snapshot.",[]string{"window_id"},[]string{"window_id"},true},
 {"window_focus","Activate the exact identified window and verify foreground ownership.",[]string{"window_id","request_id"},[]string{"window_id","request_id"},false},
 {"window_close","Request ordinary closure of the exact window. Returns closed, needs_input or pending; never kills a process or discards a save prompt.",[]string{"window_id","request_id"},[]string{"window_id","request_id"},false},
 {"ui_action","Act on an exact inspected control: set_text replaces the complete field/document; type_text replaces using focused Unicode keyboard input; invoke/select/toggle/focus use supported patterns. Returns a fresh snapshot when available. Never use to replace unapproved existing content.",[]string{"snapshot_id","element_id","action","text","request_id"},[]string{"snapshot_id","element_id","action","request_id"},false},
 {"ui_keys","Send one key chord to the exact window from a fresh snapshot, after validating identity and foreground. Example keys=[CTRL,SHIFT,S]. No automatic replay of uncertain outcomes.",[]string{"snapshot_id","keys","request_id"},[]string{"snapshot_id","keys","request_id"},false},
 {"ui_file_dialog","Drive a standard Windows Open/Save As dialog through the application UI, type an absolute path and invoke its button. mode=open/save; save verifies the actual resulting file and optional expected_text. Never creates the user's file directly. No overwrite by default; overwrite requires expected_sha256 and explicit user authorization.",[]string{"window_id","dialog_mode","path","overwrite","expected_sha256","expected_text","request_id"},[]string{"window_id","dialog_mode","path","request_id"},false},
}
func init(){runtimeCatalog=append(runtimeCatalog,directToolSpecs...)}
func isDirectUI(name string)bool{switch name{case "window_list","window_inspect","window_focus","window_close","ui_action","ui_keys","ui_file_dialog":return true};return false}

// UI/provider calls can block; reserve the durable receipt before dispatch, but
// release Runtime.mu so status/history/other read-only calls remain responsive.
func (r *Runtime) externalMutation(p runtimeRequest, fn func()(any,error))(any,error){
 if !requestIDPattern.MatchString(p.RequestID){return nil,errors.New("a unique request_id of 16-64 safe characters is required")}
 r.mu.Lock()
 if r.closing{r.mu.Unlock();return nil,errors.New("runtime closing")}
 b,_:=json.Marshal(p);h:=digest(b);path:=filepath.Join(r.dir,"requests",p.RequestID+".json")
 if oldBytes,e:=os.ReadFile(path);e==nil{
  var old Receipt
  if json.Unmarshal(oldBytes,&old)!=nil{r.mu.Unlock();return nil,errors.New("corrupt receipt")}
  r.mu.Unlock()
  if old.Digest!=h{return nil,errors.New("request_id reused with different arguments")}
  if old.Status!="completed"{return nil,fmt.Errorf("request recorded as %s; inspect before proceeding",old.Status)}
  var v any;e=json.Unmarshal(old.Result,&v);return v,e
 }else if !os.IsNotExist(e){r.mu.Unlock();return nil,e}
 x:=&Receipt{ID:p.RequestID,Digest:h,Operation:p.Operation,Path:p.Path,Status:"dispatched",At:utcNow()}
 if e:=r.saveReceipt(x);e!=nil{r.mu.Unlock();return nil,e}
 r.inflight.Add(1);r.mu.Unlock();defer r.inflight.Done()
 v,e:=fn()
 r.mu.Lock();defer r.mu.Unlock()
 if e!=nil{x.Status="uncertain_or_refused";x.Error=e.Error()}else{x.Status="completed";x.Result,_=json.Marshal(v)}
 if err:=r.saveReceipt(x);err!=nil{return nil,errors.New("result persistence failed; outcome uncertain; do not replay")}
 return v,e
}
func (a *Agent) directUI(r *Runtime,p runtimeRequest)(any,error){
 if p.Operation=="window_list"||p.Operation=="window_inspect"{return a.doDirectUI(p)}
 return r.externalMutation(p,func()(any,error){return a.doDirectUI(p)})
}
func (a *Agent) rememberWindow(raw json.RawMessage)(string,error){
 var identity struct{Hwnd int64 `json:"hwnd"`;PID int `json:"pid"`;Ticks json.RawMessage `json:"start_ticks"`}
 if json.Unmarshal(raw,&identity)!=nil||identity.Hwnd==0||identity.PID<=0||len(identity.Ticks)==0{return "",errors.New("invalid window identity")}
 key:=digest([]byte(fmt.Sprintf("%d:%d:%s",identity.Hwnd,identity.PID,identity.Ticks)))[:32]
 if a.windows==nil{a.windows=map[string]json.RawMessage{}}
 if len(a.windows)>=256{a.windows=map[string]json.RawMessage{}}
 a.windows[key]=append(json.RawMessage(nil),raw...)
 return key,nil
}
func (a *Agent) snapshotDirect(out desktopOutput)(map[string]any,error){
 wid,e:=a.rememberWindow(out.Window);if e!=nil{return nil,e}
 s:=&desktopSnapshot{ID:newID(),Created:time.Now(),Window:out.Window,Elements:out.Elements}
 a.mu.Lock();a.desktop=s;a.mu.Unlock()
 elements:=[]map[string]any{}
 for _,x:=range out.Elements{elements=append(elements,map[string]any{"id":x.ID,"name":x.Name,"type":x.Type,"automation_id":x.AutomationID,"native_handle":x.NativeHandle,"bounds":x.Bounds,"patterns":x.Patterns})}
 return map[string]any{"window_id":wid,"window":out.Window,"snapshot_id":s.ID,"expires_seconds":90,"elements":elements,"truncated":out.Truncated},nil
}
func (a *Agent) doDirectUI(p runtimeRequest)(any,error){
 a.uiMu.Lock();defer a.uiMu.Unlock()
 if os.Getenv("JARVIS_DESKTOP_ENABLED")!="1"{return nil,errors.New("desktop control disabled")}
 input:=map[string]any{"operation":p.Operation,"return_snapshot":true}
 if p.Operation!="window_list"&&p.Operation!="ui_action"&&p.Operation!="ui_keys"{
  w,ok:=a.windows[p.WindowID];if !ok{return nil,errors.New("unknown window_id; list windows again")};input["target_window"]=w
 }
 if p.Operation=="window_inspect"{input["operation"]="desktop_inspect"}
 if p.Operation=="ui_action"||p.Operation=="ui_keys"{
  a.mu.Lock();s:=a.desktop;a.desktop=nil;a.mu.Unlock()
  if s==nil||s.ID!=p.SnapshotID||time.Since(s.Created)>90*time.Second{return nil,errors.New("unknown, consumed or expired snapshot; inspect again")}
  input["window"]=s.Window;input["target_window"]=s.Window
  if p.Operation=="ui_action"{
   d:=desktopRequest{SnapshotID:p.SnapshotID,ElementID:p.ElementID,Action:p.Action}
   if p.Action=="set_text"||p.Action=="type_text"{d.Text=&p.Text;input["text"]=p.Text}
   target,e:=desktopTarget(s,d,time.Now());if e!=nil{return nil,e}
   input["operation"]="desktop_action";input["target"]=target;input["action"]=p.Action
  }else{
   if len(p.Keys)<1||len(p.Keys)>5{return nil,errors.New("keys must contain one key chord of 1..5 keys")}
   input["keys"]=p.Keys
  }
 }
 if p.Operation=="ui_file_dialog"{
  if p.DialogMode!="open"&&p.DialogMode!="save"{return nil,errors.New("dialog_mode must be open or save")}
  if !filepath.IsAbs(p.Path)||strings.ContainsRune(p.Path,0){return nil,errors.New("dialog path must be absolute")}
  input["path"]=p.Path;input["dialog_mode"]=p.DialogMode;input["overwrite"]=p.Overwrite;input["expected_sha256"]=p.Expected
  if p.ExpectedText!=nil{input["expected_text"]=*p.ExpectedText}
 }
 ctx,cancel:=context.WithTimeout(context.Background(),25*time.Second);defer cancel()
 out,e:=runDesktopWorker(ctx,input);if e!=nil{return nil,fmt.Errorf("UI outcome uncertain; inspect before another action: %w",e)}
 if !out.OK{return nil,errors.New("UI refused or uncertain: "+out.Error)}
 result:=map[string]any{"dispatched":out.Dispatched,"verified":out.Verified}
 for k,v:=range out.Data{result[k]=v}
 if len(out.Windows)>0{
  windows:=[]any{};for _,w:=range out.Windows{wid,e:=a.rememberWindow(w);if e==nil{windows=append(windows,map[string]any{"window_id":wid,"window":w})}}
  result["windows"]=windows
 }
 if p.Operation=="window_list"&&len(out.Windows)==0{result["windows"]=[]any{}}
 if len(out.Window)>0&&string(out.Window)!="null"{
  snap,e:=a.snapshotDirect(out);if e!=nil{return nil,e};for k,v:=range snap{result[k]=v}
 }
 return result,nil
}
