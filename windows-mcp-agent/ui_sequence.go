package main

import (
 "encoding/json"
 "errors"
 "fmt"
 "strings"
 "time"
 "unicode/utf8"
)

// ui_sequence accepts only these fixed, reviewable UI primitives. It cannot
// execute shell commands, PowerShell snippets, or arbitrary tool calls.
type uiSequenceStep struct {
 Kind string `json:"kind"`
 MouseAction string `json:"mouse_action,omitempty"`
 X *int `json:"x,omitempty"`
 Y *int `json:"y,omitempty"`
 EndX *int `json:"end_x,omitempty"`
 EndY *int `json:"end_y,omitempty"`
 WheelDelta *int `json:"wheel_delta,omitempty"`
 Keys []string `json:"keys,omitempty"`
 Text *string `json:"text,omitempty"`
 SettleMS *int `json:"settle_ms,omitempty"`
}

type uiSession struct {
 ID string
 Window json.RawMessage
 Geometry captureGeometry
 Bounds [4]int
 LastActivity time.Time
}

const uiSessionIdleLimit = 5 * time.Minute
const uiSequenceMaxSteps = 24

func uiSessionValid(s *uiSession, now time.Time) bool {
 return s != nil && !now.Before(s.LastActivity) && now.Sub(s.LastActivity) < uiSessionIdleLimit
}

func validKeyChord(keys []string) bool {
 if len(keys) == 0 || len(keys) > 5 {return false}
 mod := map[string]bool{}
 nonMod := false
 for _, raw := range keys {
  k := strings.ToUpper(raw)
  switch k {
  case "CTRL", "CONTROL": k = "CTRL"
  case "SHIFT", "ALT":
  default:
   if nonMod { return false }
   nonMod = true
   if len(k) == 1 {
    if k[0] >= 'A' && k[0] <= 'Z' || k[0] >= '0' && k[0] <= '9' {continue}
   }
   switch k {
   case "ENTER", "TAB", "ESC", "ESCAPE", "BACKSPACE", "DELETE", "HOME", "END", "LEFT", "UP", "RIGHT", "DOWN":
    continue
   }
   if len(k) >= 2 && len(k) <= 3 && k[0] == 'F' {
    n:=0
    for _,digit:=range k[1:] {
     if digit < '0' || digit > '9' {return false}
     n=n*10+int(digit-'0')
    }
    if n>=1 && n<=24 {continue}
   }
   return false
  }
  if mod[k] {return false}
  mod[k] = true
 }
 return nonMod
}

func validateUISequence(steps []uiSequenceStep) error {
 if len(steps) < 1 || len(steps) > uiSequenceMaxSteps {
  return fmt.Errorf("steps must contain 1..%d actions", uiSequenceMaxSteps)
 }
 totalText := 0
 for i, step := range steps {
  bad := func(reason string) error {return fmt.Errorf("steps[%d]: %s",i,reason)}
  if step.SettleMS!=nil && (*step.SettleMS<0 || *step.SettleMS>500){return bad("settle_ms must be 0..500")}
  hasMouse := step.MouseAction!="" || step.X!=nil || step.Y!=nil || step.EndX!=nil || step.EndY!=nil || step.WheelDelta!=nil
  switch step.Kind {
  case "mouse":
   if step.Keys != nil || step.Text != nil || step.X == nil || step.Y == nil {
    return bad("mouse requires x/y, and no keyboard/text fields")
   }
   if *step.X<0 || *step.X>=32768 || *step.Y<0 || *step.Y>=32768 {
    return bad("coordinates out of range")
   }
   if step.MouseAction == "drag" {
    if step.EndX==nil || step.EndY==nil || step.WheelDelta!=nil {return bad("drag requires end_x/end_y and no wheel_delta")}
    if *step.EndX<0 || *step.EndX>=32768 || *step.EndY<0 || *step.EndY>=32768 {return bad("drag end coordinates out of range")}
   } else if step.EndX!=nil || step.EndY!=nil {return bad("end_x/end_y are only for drag")}
   switch step.MouseAction {
   case "move", "click", "double_click", "right_click":
    if step.WheelDelta!=nil {return bad("wheel_delta is only for wheel")}
   case "drag":
   case "wheel":
    if step.WheelDelta==nil || *step.WheelDelta==0 || *step.WheelDelta < -1200 || *step.WheelDelta>1200 || *step.WheelDelta%120!=0 {return bad("invalid wheel_delta")}
   default:
    return bad("invalid mouse_action")
   }
  case "keys":
   if hasMouse || step.Text!=nil || !validKeyChord(step.Keys) {return bad("invalid key chord or extra fields")}
  case "text":
   if hasMouse || step.Keys!=nil || step.Text==nil {return bad("text requires one string and no other fields")}
   if len(*step.Text)==0 || !utf8.ValidString(*step.Text) || utf8.RuneCountInString(*step.Text)>1024 || strings.ContainsRune(*step.Text,0) {
    return bad("invalid text (1..1024 Unicode characters)")
   }
   totalText+=utf8.RuneCountInString(*step.Text)
   if totalText>4096 {return bad("total text exceeds 4096 characters")}
  default:
   return bad("unsupported kind")
  }
 }
 return nil
}

func physicalUISequence(steps []uiSequenceStep, geometry captureGeometry) ([]map[string]any,error) {
 if err:=validateUISequence(steps);err!=nil{return nil,err}
 out:=make([]map[string]any,0,len(steps))
 for i, step:= range steps {
  action:=map[string]any{"kind":step.Kind}
  switch step.Kind {
  case "mouse":
   x,y,err:=geometry.physical(*step.X,*step.Y)
   if err!=nil{return nil,fmt.Errorf("steps[%d]: %w",i,err)}
   action["x"]=x;action["y"]=y
   action["mouse_action"]=step.MouseAction
   if step.MouseAction=="drag" {
    endX,endY,err:=geometry.physical(*step.EndX,*step.EndY)
    if err!=nil{return nil,fmt.Errorf("steps[%d]: %w",i,err)}
    action["end_x"]=endX;action["end_y"]=endY
   }
   if step.MouseAction=="wheel" {action["wheel_delta"]=*step.WheelDelta}
  case "keys":
   action["keys"]=step.Keys
  case "text":
   action["text"]=*step.Text
  }
  if step.SettleMS!=nil {action["settle_ms"]=*step.SettleMS}
  out=append(out,action)
 }
 return out,nil
}

func captureRateAllowed(last, now time.Time) bool {
 return last.IsZero() || now.Sub(last)>=time.Second
}

var errUISessionExpired = errors.New("ui session missing or expired after 5 minutes of inactivity; open a new session")
