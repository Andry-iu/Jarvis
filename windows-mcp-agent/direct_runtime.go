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
 {"ui_mouse","Move, click, wheel or drag with left button held and released in one guarded operation. For drag supply end_x/end_y in physical desktop coordinates; both endpoints must belong to the verified foreground window. One-use snapshot, no held mouse across requests.",[]string{"snapshot_id","x","y","end_x","end_y","mouse_action","wheel_delta","request_id"},[]string{"snapshot_id","x","y","mouse_action","request_id"},false},
  {"ui_mouse_screen","Move/click or atomically drag on the visible interactive desktop using JPEG pixel coordinates from one recent screen_capture. For drag supply end_x/end_y in the same JPEG, not physical pixels; both points must be in the same foreground window. Button release is automatic even on failure. One-use capture expires after 45 seconds.",[]string{"capture_id","x","y","end_x","end_y","mouse_action","wheel_delta","request_id"},[]string{"capture_id","x","y","mouse_action","request_id"},false},
 {"ui_session_open","Start a 5-minute idle-expiring UI session for the exact foreground window observed in one recent screenshot. Requires window_id from window_list and capture_id from screen_capture. Session reuses geometry without recapturing; identity/foreground/occlusion and authorization checks remain active.",[]string{"window_id","capture_id","request_id"},[]string{"window_id","capture_id","request_id"},false},
 {"ui_sequence","Execute 1..24 mouse, keyboard, text or UI readiness steps in one serialized UI worker using screenshot pixels from an open UI session. A wait step checks selection or editable focus before further input. No shell or arbitrary code. Abort at first failure, no automatic retry; session expires after 5 minutes idle.",[]string{"session_id","steps","request_id"},[]string{"session_id","steps","request_id"},false},
 {"ui_session_close","End the exact active UI session, invalidating its cached screenshot coordinates.",[]string{"session_id","request_id"},[]string{"session_id","request_id"},false},
 {"ui_file_dialog","Drive a standard Windows Open/Save As dialog through the application UI, type an absolute path and invoke its button. mode=open/save; save verifies the actual resulting file and optional expected_text. Never creates the user's file directly. No overwrite by default; overwrite requires expected_sha256 and explicit user authorization.",[]string{"window_id","dialog_mode","path","overwrite","expected_sha256","expected_text","request_id"},[]string{"window_id","dialog_mode","path","request_id"},false},
}
func init(){runtimeCatalog=append(runtimeCatalog,directToolSpecs...)}
func isDirectUI(name string)bool{switch name{case "window_list","window_inspect","window_focus","window_close","ui_action","ui_keys","ui_mouse","ui_mouse_screen","ui_session_open","ui_sequence","ui_session_close","ui_file_dialog":return true};return false}

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
 if p.Operation=="ui_session_close"{
  if a.uiSession==nil||a.uiSession.ID!=p.SessionID{return nil,errUISessionExpired}
  a.uiSession=nil
  return map[string]any{"status":"ui_session_closed","verified":true},nil
 }
 sequenceOK:=false
 if p.Operation=="ui_sequence" {
  // A partial or uncertain UI sequence must never be replayed with stale state.
  defer func(){
   if !sequenceOK&&a.uiSession!=nil&&a.uiSession.ID==p.SessionID{a.uiSession=nil}
  }()
 }
 input:=map[string]any{"operation":p.Operation,"return_snapshot":true}
 var openedCapture *imageCaptureSnapshot
 if p.Operation!="window_list"&&p.Operation!="ui_action"&&p.Operation!="ui_keys"&&p.Operation!="ui_mouse"&&p.Operation!="ui_mouse_screen"&&p.Operation!="ui_sequence"{
  w,ok:=a.windows[p.WindowID];if !ok{return nil,errors.New("unknown window_id; list windows again")};input["target_window"]=w
 }
 if p.Operation=="window_inspect"{input["operation"]="desktop_inspect"}
 if p.Operation=="ui_session_open" {
  a.mu.Lock()
  snap:=a.capture
  a.capture=nil // one-use screenshot for opening exactly one session
  a.mu.Unlock()
  if snap==nil||snap.ID!=p.CaptureID||time.Since(snap.Created)<0||time.Since(snap.Created)>45*time.Second {
   return nil,errors.New("unknown, consumed or expired capture_id; capture again")
  }
  var identity struct{ Hwnd int64 `json:"hwnd"` }
  if json.Unmarshal(input["target_window"].(json.RawMessage),&identity)!=nil||identity.Hwnd==0||identity.Hwnd!=snap.ForegroundHWND {
   return nil,errors.New("capture foreground does not match selected window")
  }
  openedCapture=snap
  input["return_snapshot"]=false
 }
 if p.Operation=="ui_sequence" {
  session:=a.uiSession
  if session==nil||session.ID!=p.SessionID||!uiSessionValid(session,time.Now()){
   a.uiSession=nil
   return nil,errUISessionExpired
  }
  steps,err:=physicalUISequence(p.Steps,session.Geometry)
  if err!=nil{return nil,err}
  input["target_window"]=session.Window
  input["expected_bounds"]=session.Bounds
  input["steps"]=steps
  input["return_snapshot"]=false
 }
 if p.Operation=="ui_action"||p.Operation=="ui_keys"||p.Operation=="ui_mouse"{
  a.mu.Lock();s:=a.desktop;a.desktop=nil;a.mu.Unlock()
  if s==nil||s.ID!=p.SnapshotID||time.Since(s.Created)>90*time.Second{return nil,errors.New("unknown, consumed or expired snapshot; inspect again")}
  input["window"]=s.Window;input["target_window"]=s.Window
  if p.Operation=="ui_mouse"{
   input["mouse_action"]=p.MouseAction;input["x"]=p.X;input["y"]=p.Y;input["wheel_delta"]=p.WheelDelta
   if p.MouseAction=="drag"{input["end_x"]=*p.EndX;input["end_y"]=*p.EndY}
  }else if p.Operation=="ui_action"{
   d:=desktopRequest{SnapshotID:p.SnapshotID,ElementID:p.ElementID,Action:p.Action}
   if p.Action=="set_text"||p.Action=="type_text"{d.Text=&p.Text;input["text"]=p.Text}
   target,e:=desktopTarget(s,d,time.Now());if e!=nil{return nil,e}
   input["operation"]="desktop_action";input["target"]=target;input["action"]=p.Action
  }else{
   if len(p.Keys)<1||len(p.Keys)>5{return nil,errors.New("keys must contain one key chord of 1..5 keys")}
   input["keys"]=p.Keys
  }
 }
 if p.Operation=="ui_mouse_screen"{
  a.mu.Lock()
  snap:=a.capture
  a.capture=nil // consume even when mismatched, expired or refused
  a.mu.Unlock()
  if snap==nil||snap.ID!=p.CaptureID||time.Since(snap.Created)<0||time.Since(snap.Created)>45*time.Second {
   return nil,errors.New("unknown, consumed or expired capture_id; capture again")
  }
  px,py,e:=snap.Geometry.physical(p.X,p.Y)
  if e!=nil{return nil,e}
  input["return_snapshot"]=false
  input["x"]=px;input["y"]=py;input["mouse_action"]=p.MouseAction;input["wheel_delta"]=p.WheelDelta
   if p.MouseAction=="drag"{
    ex,ey,e:=snap.Geometry.physical(*p.EndX,*p.EndY)
    if e!=nil{return nil,e}
    input["end_x"]=ex;input["end_y"]=ey
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
 if p.Operation=="ui_session_open" {
  rawBounds,ok:=out.Data["window_bounds"].([]any)
  if !ok||len(rawBounds)!=4{return nil,errors.New("session opening missing verified bounds")}
  var bounds [4]int
  for i,v:=range rawBounds{n,ok:=v.(float64);if !ok{return nil,errors.New("invalid window bounds")};bounds[i]=int(n)}
  session:=&uiSession{ID:newID(),Window:input["target_window"].(json.RawMessage),Geometry:openedCapture.Geometry,Bounds:bounds,LastActivity:time.Now()}
  a.uiSession=session // one active UI session per agent; opening replaces any older session
  out.Data["session_id"]=session.ID
  out.Data["idle_expires_seconds"]=int(uiSessionIdleLimit/time.Second)
  out.Data["capture_reused"]=true
 }
 if p.Operation=="ui_sequence"{
  a.uiSession.LastActivity=time.Now()
  sequenceOK=true
 }
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
