package main

import (
 "encoding/json"
 "strings"
 "testing"
 "time"
)

func uiInt(v int) *int {return &v}
func uiString(v string) *string {return &v}

func TestUISequenceRequestValidation(t *testing.T){
 cases:=[]struct{name,body string;ok bool}{
  {"mouse_drag",`{"machine_id":"pc","session_id":"active","steps":[{"kind":"mouse","mouse_action":"drag","x":10,"y":20,"end_x":90,"end_y":80}],"request_id":"ui-sequence-drag-test-0001"}`,true},
  {"mouse_click_origin",`{"machine_id":"pc","session_id":"active","steps":[{"kind":"mouse","mouse_action":"click","x":0,"y":0}],"request_id":"ui-sequence-click-test-0001"}`,true},
  {"keys_text_unicode",`{"machine_id":"pc","session_id":"active","steps":[{"kind":"keys","keys":["CTRL","A"]},{"kind":"text","text":"Caffè 中文"}],"request_id":"ui-sequence-keys-test-0001"}`,true},
  {"missing_steps",`{"machine_id":"pc","session_id":"active","request_id":"ui-sequence-missing-steps"}`,false},
  {"empty_steps",`{"machine_id":"pc","session_id":"active","steps":[],"request_id":"ui-sequence-empty-steps"}`,false},
  {"unknown_step",`{"machine_id":"pc","session_id":"active","steps":[{"kind":"mouse","mouse_action":"click","x":1,"y":2,"run":"powershell"}],"request_id":"ui-sequence-unknown"}`,false},
  {"missing_start",`{"machine_id":"pc","session_id":"active","steps":[{"kind":"mouse","mouse_action":"click","x":1}],"request_id":"ui-sequence-start"}`,false},
  {"drag_no_end",`{"machine_id":"pc","session_id":"active","steps":[{"kind":"mouse","mouse_action":"drag","x":1,"y":2}],"request_id":"ui-sequence-no-end"}`,false},
  {"keys_invalid",`{"machine_id":"pc","session_id":"active","steps":[{"kind":"keys","keys":["CTRL","CTRL","Z"]}],"request_id":"ui-sequence-bad-chord"}`,false},
  {"text_with_mouse",`{"machine_id":"pc","session_id":"active","steps":[{"kind":"text","text":"hi","x":2}],"request_id":"ui-sequence-extra"}`,false},
  {"mouse_with_keys",`{"machine_id":"pc","session_id":"active","steps":[{"kind":"mouse","mouse_action":"click","x":1,"y":2,"keys":["A"]}],"request_id":"ui-sequence-mixed"}`,false},
  {"text_too_long",`{"machine_id":"pc","session_id":"active","steps":[{"kind":"text","text":"`+strings.Repeat("X",1025)+`"}],"request_id":"ui-sequence-long-text"}`,false},
  {"settle_500",`{"machine_id":"pc","session_id":"active","steps":[{"kind":"mouse","mouse_action":"click","x":1,"y":2,"settle_ms":500}],"request_id":"ui-sequence-settle-max"}`,true},
  {"settle_negative",`{"machine_id":"pc","session_id":"active","steps":[{"kind":"mouse","mouse_action":"click","x":1,"y":2,"settle_ms":-1}],"request_id":"ui-sequence-settle-negative"}`,false},
  {"settle_too_long",`{"machine_id":"pc","session_id":"active","steps":[{"kind":"keys","keys":["A"],"settle_ms":501}],"request_id":"ui-sequence-settle-too-long"}`,false},
  {"unknown_kind",`{"machine_id":"pc","session_id":"active","steps":[{"kind":"script","text":"hello"}],"request_id":"ui-sequence-script"}`,false},
  {"wait_selected",`{"session_id":"active","steps":[{"kind":"wait","condition":"selected","x":0,"y":2}],"request_id":"ui-sequence-wait-selected"}`,true},
  {"wait_focus",`{"session_id":"active","steps":[{"kind":"wait","condition":"editable_focus","x":8,"y":9}],"request_id":"ui-sequence-wait-focus"}`,true},
  {"wait_unknown",`{"session_id":"active","steps":[{"kind":"wait","condition":"anything","x":0,"y":2}],"request_id":"ui-sequence-wait-unknown"}`,false},
  {"wait_missing_point",`{"session_id":"active","steps":[{"kind":"wait","condition":"selected","x":0}],"request_id":"ui-sequence-wait-missing-point"}`,false},
  {"wait_with_action",`{"session_id":"active","steps":[{"kind":"wait","condition":"selected","mouse_action":"click","x":0,"y":2}],"request_id":"ui-sequence-wait-action"}`,false},
  {"wait_with_pause",`{"session_id":"active","steps":[{"kind":"wait","condition":"selected","x":0,"y":2,"settle_ms":20}],"request_id":"ui-sequence-wait-pause"}`,false},
  {"condition_on_text",`{"session_id":"active","steps":[{"kind":"text","text":"hello","condition":"editable_focus"}],"request_id":"ui-sequence-text-condition"}`,false},
 }
 for _,tc:=range cases{t.Run(tc.name,func(t *testing.T){_,err:=decodeRuntime("ui_sequence",json.RawMessage(tc.body));if (err==nil)!=tc.ok{t.Fatalf("valid=%v want=%v err=%v",err==nil,tc.ok,err)}})}
}
func TestUISequenceWaitGeometry(t *testing.T) {
 g := captureGeometry{OriginX:-100,OriginY:10,OriginalWidth:1200,OriginalHeight:600,ImageWidth:600,ImageHeight:300}
 steps := []uiSequenceStep{{Kind:"wait",Condition:uiString("selected"),X:uiInt(100),Y:uiInt(100)}}
 out, err := physicalUISequence(steps,g)
 if err != nil {
  t.Fatal(err)
 }
 if out[0]["x"]!=101 || out[0]["y"]!=211 || out[0]["condition"]!="selected" || len(out[0])!=4 {
  t.Fatalf("wrong worker condition: %+v",out[0])
 }
 steps[0].X=uiInt(600)
 if _,err:=physicalUISequence(steps,g);err==nil {
  t.Fatal("out-of-image wait accepted")
 }
}
func TestUISequenceStepLimits(t *testing.T){
 steps:=make([]uiSequenceStep,24)
 for i:=range steps{steps[i]=uiSequenceStep{Kind:"keys",Keys:[]string{"A"}}}
 if err:=validateUISequence(steps);err!=nil{t.Fatal(err)}
 if err:=validateUISequence(append(steps,steps[0]));err==nil{t.Fatal("25 steps accepted")}
 steps=[]uiSequenceStep{{Kind:"text",Text:uiString("")}}
 if err:=validateUISequence(steps);err==nil{t.Fatal("empty text accepted")}
}
func TestUISequenceGeometryAndSessionExpiry(t *testing.T){
 g:=captureGeometry{OriginX:-100,OriginY:10,OriginalWidth:1200,OriginalHeight:600,ImageWidth:600,ImageHeight:300}
 steps:=[]uiSequenceStep{{Kind:"mouse",MouseAction:"drag",X:uiInt(100),Y:uiInt(100),EndX:uiInt(200),EndY:uiInt(200)},{Kind:"keys",Keys:[]string{"CTRL","A"}},{Kind:"text",Text:uiString("Caffè")}}
 out,err:=physicalUISequence(steps,g);if err!=nil{t.Fatal(err)}
 if out[0]["x"].(int)!=101 || out[0]["y"].(int)!=211 || out[0]["end_x"].(int)!=301 || out[0]["end_y"].(int)!=411 {t.Fatalf("wrong scaled coordinates %+v",out[0])}
 if len(out)!=3||out[1]["kind"]!="keys"||out[2]["text"]!="Caffè"{t.Fatalf("sequence order not preserved %+v",out)}
 steps[0].EndX=uiInt(600)
 if _,err:=physicalUISequence(steps,g);err==nil{t.Fatal("out-of-image drag accepted")}
 now:=time.Unix(1700000000,0)
 s:=&uiSession{ID:"test",LastActivity:now}
 if !uiSessionValid(s,now.Add(299*time.Second)){t.Fatal("early expiration")}
 if uiSessionValid(s,now.Add(300*time.Second)){t.Fatal("expired session accepted")}
 if uiSessionValid(nil,now){t.Fatal("nil session accepted")}
 if !captureRateAllowed(time.Time{},now) || captureRateAllowed(now,now.Add(999*time.Millisecond)) || !captureRateAllowed(now,now.Add(time.Second)){t.Fatal("capture rate gate incorrect")}
}
func TestUISequenceToolCatalog(t *testing.T){
 for _,name:=range []string{"ui_session_open","ui_sequence","ui_session_close"} {
  if !isDirectUI(name){t.Fatalf("not in UI dispatch: %s",name)}
  found:=false
  for _,spec:=range directToolSpecs {if spec.Name==name {
   found=true
   if spec.ReadOnly{t.Fatalf("mutation marked read-only: %s",name)}
   break
  }}
  if !found{t.Fatalf("missing catalog: %s",name)}
 }
}
