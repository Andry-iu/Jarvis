package main

import (
 "encoding/json"
 "testing"
)

func TestCapturedPixelCoordinateTransform(t *testing.T) {
 cases := []struct {
  name string
  g captureGeometry
  x,y int
  wantX,wantY int
  valid bool
 }{
  {"scale_1_5",captureGeometry{OriginX:0,OriginY:0,OriginalWidth:1920,OriginalHeight:1080,ImageWidth:1280,ImageHeight:720},400,200,600,300,true},
  {"bottom_right",captureGeometry{OriginalWidth:1920,OriginalHeight:1080,ImageWidth:1280,ImageHeight:720},1279,719,1919,1079,true},
  {"monitor_negative_origin",captureGeometry{OriginX:-1600,OriginY:-200,OriginalWidth:1600,OriginalHeight:900,ImageWidth:800,ImageHeight:450},200,100,-1199,1,true},
  {"right_edge_refused",captureGeometry{OriginalWidth:1920,OriginalHeight:1080,ImageWidth:1280,ImageHeight:720},1280,0,0,0,false},
  {"negative_pixel_refused",captureGeometry{OriginalWidth:1920,OriginalHeight:1080,ImageWidth:1280,ImageHeight:720},-1,0,0,0,false},
  {"zero_dimension_refused",captureGeometry{},0,0,0,0,false},
 }
 for _,tc:=range cases{
  t.Run(tc.name,func(t *testing.T){
   x,y,e:=tc.g.physical(tc.x,tc.y)
   if (e==nil)!=tc.valid{t.Fatalf("valid=%v want %v: %v",e==nil,tc.valid,e)}
   if e==nil && (x!=tc.wantX||y!=tc.wantY){t.Fatalf("got %d,%d want %d,%d",x,y,tc.wantX,tc.wantY)}
  })
 }
}
func TestImageMouseRequiresCaptureAndUniqueRequest(t *testing.T){
 cases:=[]struct{name,json string;ok bool}{
  {"click",`{"machine_id":"x","capture_id":"fresh","x":300,"y":250,"mouse_action":"click","request_id":"screen-mouse-click-test-001"}`,true},
  {"move",`{"machine_id":"x","capture_id":"fresh","x":0,"y":0,"mouse_action":"move","request_id":"screen-mouse-move-test-002"}`,true},
  {"no_capture",`{"machine_id":"x","x":300,"y":250,"mouse_action":"click","request_id":"screen-mouse-nocap-test-003"}`,false},
  {"old_snapshot_instead",`{"machine_id":"x","snapshot_id":"old","x":300,"y":250,"mouse_action":"click","request_id":"screen-mouse-old-id-test-004"}`,false},
  {"invalid_extra",`{"machine_id":"x","capture_id":"fresh","x":300,"y":250,"mouse_action":"click","arbitrary_execution":true,"request_id":"screen-mouse-bad-extra-005"}`,false},
  {"bad_action",`{"machine_id":"x","capture_id":"fresh","x":300,"y":250,"mouse_action":"drag","request_id":"screen-mouse-bad-action-006"}`,false},
  {"bad_wheel",`{"machine_id":"x","capture_id":"fresh","x":300,"y":250,"mouse_action":"move","wheel_delta":120,"request_id":"screen-mouse-bad-wheel-007"}`,false},
  {"missing_x",`{"machine_id":"x","capture_id":"fresh","y":250,"mouse_action":"click","request_id":"screen-mouse-missing-x-008"}`,false},
 }
 for _,tc:=range cases{
  t.Run(tc.name,func(t *testing.T){
   _,e:=decodeRuntime("ui_mouse_screen",json.RawMessage(tc.json))
   if (e==nil)!=tc.ok{t.Fatalf("accepted=%t want=%t: %v",e==nil,tc.ok,e)}
  })
 }
 if !isDirectUI("ui_mouse_screen"){t.Fatal("image mouse must use guarded direct UI")}
 for _,spec:=range directToolSpecs{
  if spec.Name=="ui_mouse_screen"{
   if spec.ReadOnly{t.Fatal("image mouse must be mutating")}
   if len(spec.Required)!=5{t.Fatalf("required: %v",spec.Required)}
   return
  }
 }
 t.Fatal("missing ui_mouse_screen tool")
}
