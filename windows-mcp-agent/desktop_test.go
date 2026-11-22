package main

import (
 "encoding/json"
 "strings"
 "testing"
 "time"
)
func TestDesktopGuardRejectsStaleAmbiguousAndUnsupported(t *testing.T){
 now:=time.Now()
 good:=desktopRequest{SnapshotID:"known",ElementID:"target",Action:"invoke"}
 base:=func()*desktopSnapshot{return &desktopSnapshot{ID:"known",Created:now,Elements:[]desktopElement{{ID:"target",Identity:json.RawMessage("{}")}}}}
 cases:=[]struct{name string;s *desktopSnapshot;p desktopRequest}{
 {"missing",nil,good},
 {"expired",&desktopSnapshot{ID:"known",Created:now.Add(-91*time.Second)},good},
 {"replay",&desktopSnapshot{ID:"another",Created:now},good},
 {"future",&desktopSnapshot{ID:"known",Created:now.Add(time.Second)},good},
 {"duplicate",&desktopSnapshot{ID:"known",Created:now,Elements:[]desktopElement{{ID:"target"},{ID:"target"}}},good},
 {"unknown_action",base(),desktopRequest{SnapshotID:"known",ElementID:"target",Action:"send_keys"}},
 {"missing_text",base(),desktopRequest{SnapshotID:"known",ElementID:"target",Action:"set_text"}},
 {"unknown_element",base(),desktopRequest{SnapshotID:"known",ElementID:"missing",Action:"invoke"}},
 }
 for _,c:=range cases{t.Run(c.name,func(t *testing.T){if _,err:=desktopTarget(c.s,c.p,now);err==nil{t.Fatal("unsafe action accepted")}})}
 if _,err:=desktopTarget(base(),good,now);err!=nil{t.Fatal(err)}
}
func TestDesktopInputRejectsCoordinatesAndBoundsOutput(t *testing.T){
 for _,raw:=range []string{`{"x":100,"y":200}`,`{"keys":"ENTER"}`,`{"command":"anything"}`}{
 if _,err:=decodeDesktop(json.RawMessage(raw));err==nil{t.Fatal("unexpected input accepted")}
 }
 w:=&desktopBoundedOutput{}
 if n,err:=w.Write([]byte(strings.Repeat("a",70000)));n!=70000||err!=nil||!w.overflow||w.b.Len()!=65536{t.Fatal("output bound failed")}
}
