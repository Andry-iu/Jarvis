package main

import (
 "encoding/json"
 "os"
 "path/filepath"
 "testing"
 "time"
)

// Explicit integration check (> the removed five-minute watchdog), no model,
// real account or network. Run separately with JARVIS_LONG_TEST=1.
func TestLongCodexTurnContinuesUntilUserInterrupt(t *testing.T){
 if os.Getenv("JARVIS_LONG_TEST")!="1"{t.Skip("explicit 310-second integration test")}
 r,_:=fixtureRuntime(t);t.Setenv("JARVIS_CODEX_ENABLED","1")
 root,err:=r.projectResource("sample");if err!=nil{t.Fatal(err)}
 thread,turn:=newID(),newID()
 if err=atomicJSON(filepath.Join(r.dir,"threads",thread+".json"),codexLink{thread,"sample",root,"test",r.MachineID,utcNow()});err!=nil{t.Fatal(err)}
 w:=&rpcTestInput{make(chan codexMessage,16)}
 c:=&codexClient{input:w,cancel:func(){},dir:r.dir,machineID:r.MachineID,waiters:map[string]chan codexMessage{},pending:map[string]codexPending{},loaded:map[string]bool{thread:true},active:map[string]string{},finished:map[string]string{}}
 withQueue(r,func(){r.codex=c})
 interrupted:=make(chan struct{},2);stop:=make(chan struct{});defer close(stop)
 go func(){for{select{case<-stop:return;case m:=<-w.requests:
  var result any
  switch m.Method{
  case "account/read":result=json.RawMessage(`{"account":{"type":"chatgpt","planType":"self_serve_business_prolite","email":"fixture@example.invalid"}}`)
  case "account/rateLimits/read":result=json.RawMessage(`{"rateLimits":{"limitId":"codex","primary":{"usedPercent":1,"resetsAt":2000000000},"credits":{"hasCredits":false,"unlimited":false,"balance":"0"}}}`)
  case "thread/read":result=map[string]any{"thread":map[string]any{"id":thread,"cwd":root,"status":map[string]any{"type":"idle"}}}
  case "turn/start":result=map[string]any{"turn":map[string]any{"id":turn,"status":"inProgress"}}
  case "turn/interrupt":
   interrupted<-struct{}{}
   c.mu.Lock();c.finished[thread]=turn;delete(c.active,thread)
   completed,_:=json.Marshal(map[string]any{"turn":map[string]any{"id":turn,"status":"interrupted"}})
   c.appendEventLocked(thread,"turn/completed",completed,"")
   usage,_:=json.Marshal(map[string]any{"turn_id":turn,"available":true,"fixture":true})
   c.appendEventLocked(thread,"jarvis/usage",usage,"");c.mu.Unlock();result=map[string]any{}
  default:result=map[string]any{}
  }
  b,_:=json.Marshal(result);c.mu.Lock();ch:=c.waiters[c.nextIDString(m.ID)];c.mu.Unlock();if ch!=nil{ch<-codexMessage{Result:b}}
 }}}()
 value,err:=r.call(runtimeRequest{Operation:"codex_turn",Project:"sample",ThreadID:thread,Text:"fixture only",RequestID:newID(),MachineID:r.MachineID});if err!=nil{t.Fatal(err)}
 id:=value.(map[string]any)["queue_id"].(string)
 deadline:=time.Now().Add(5*time.Second)
 for{r.mu.Lock();e:=r.queueFindLocked(id);status:=e.Status;r.mu.Unlock();if status=="running"{break};if time.Now().After(deadline){t.Fatal("turn did not start",status)};time.Sleep(20*time.Millisecond)}
 timer:=time.NewTimer(310*time.Second);defer timer.Stop()
 select{case<-interrupted:t.Fatal("automatic time-based interruption");case<-timer.C:}
 r.mu.Lock();e:=r.queueFindLocked(id);status:=e.Status;r.mu.Unlock()
 if status!="running"{t.Fatal("long turn lost running state",status)}
 if _,err=r.codexCall(runtimeRequest{Operation:"codex_interrupt",Project:"sample",ThreadID:thread,TurnID:turn,RequestID:newID()});err!=nil{t.Fatal(err)}
 select{case<-interrupted:case<-time.After(time.Second):t.Fatal("user interruption not dispatched")}
 deadline=time.Now().Add(3*time.Second)
 for{
  e,err=r.archivedEntry(id);if err==nil{break};if time.Now().After(deadline){t.Fatal("interrupted turn not archived",err)};time.Sleep(20*time.Millisecond)
 }
 if e.Status!="interrupted"||len(e.FinalUsage)==0{t.Fatal("final turn/usage outcome lost")}
}
