package main

import (
 "encoding/json"
 "sync/atomic"
 "testing"
 "time"
)

func TestDirectToolContractsAndTextIntent(t *testing.T){
 for _,name:=range []string{"maintenance_status","known_folder","process_start","window_list","window_inspect","window_focus","window_close","ui_action","ui_keys","ui_file_dialog"}{if !isRuntimeTool(name){t.Fatal("missing tool",name)}}
 missing:=json.RawMessage("{\"machine_id\":\"pc\",\"snapshot_id\":\"s\",\"element_id\":\"e\",\"action\":\"set_text\",\"request_id\":\"1234567890123456\"}")
 if _,err:=decodeRuntime("ui_action",missing);err==nil{t.Fatal("missing text accepted as destructive empty replacement")}
 explicit:=json.RawMessage("{\"machine_id\":\"pc\",\"snapshot_id\":\"s\",\"element_id\":\"e\",\"action\":\"set_text\",\"text\":\"\",\"request_id\":\"1234567890123456\"}")
 if _,err:=decodeRuntime("ui_action",explicit);err!=nil{t.Fatal(err)}
 launch:=json.RawMessage("{\"machine_id\":\"pc\",\"executable\":\"notepad.exe\",\"request_id\":\"1234567890123456\"}")
 p,err:=decodeRuntime("process_start",launch);if err!=nil||p.Elevated{t.Fatal("default elevation",p,err)}
}
func TestExternalActionReceiptIsResponsiveAndNeverReplays(t *testing.T){
 r,_:=fixtureRuntime(t)
 p:=runtimeRequest{Operation:"window_close",MachineID:r.MachineID,WindowID:"window",RequestID:newID()}
 started:=make(chan struct{});finish:=make(chan struct{});done:=make(chan error,1);var calls atomic.Int32
 go func(){_,e:=r.externalMutation(p,func()(any,error){calls.Add(1);close(started);<-finish;return map[string]any{"status":"closed"},nil});done<-e}()
 <-started
 statusDone:=make(chan error,1);go func(){_,e:=r.call(runtimeRequest{Operation:"request_status",RequestID:p.RequestID});statusDone<-e}()
 select{case e:=<-statusDone:if e!=nil{t.Fatal(e)};case <-time.After(time.Second):close(finish);<-done;t.Fatal("external UI action held global runtime lock")}
 if _,e:=r.externalMutation(p,func()(any,error){calls.Add(1);return nil,nil});e==nil{t.Fatal("in-flight request replayed")}
 close(finish);if e:=<-done;e!=nil{t.Fatal(e)}
 if _,e:=r.externalMutation(p,func()(any,error){calls.Add(1);return nil,nil});e!=nil{t.Fatal(e)}
 if calls.Load()!=1{t.Fatal("duplicate external mutation",calls.Load())}
 p.WindowID="other";if _,e:=r.externalMutation(p,func()(any,error){return nil,nil});e==nil{t.Fatal("reused request_id accepted with different target")}
}
