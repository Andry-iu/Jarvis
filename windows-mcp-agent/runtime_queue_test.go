package main

import (
    "encoding/json"
    "os"
    "path/filepath"
    "testing"
    "time"
)

func withQueue(r *Runtime,fn func()){r.mu.Lock();defer r.mu.Unlock();fn()}
func holdQueue(t *testing.T,r *Runtime,project string) string {
    t.Helper();resource:=""
    withQueue(r,func(){var err error;resource,err=r.projectResource(project);if err!=nil{t.Fatal(err)};r.queue.Paused[resource]="test hold";if err=r.queueSaveLocked();err!=nil{t.Fatal(err)}})
    return resource
}
func addQueueTest(t *testing.T,r *Runtime,project,label string,deps []string) string {
    t.Helper();call,_:=json.Marshal(map[string]any{"operation":"command_start","profile":"git_status"})
    v,err:=r.call(runtimeRequest{Operation:"queue_add",MachineID:r.MachineID,Project:project,RequestID:newID(),Label:label,DependsOn:deps,Call:call});if err!=nil{t.Fatal(err)}
    return v.(map[string]any)["queue_id"].(string)
}
func TestQueueOrderCancelDependenciesAndReceipt(t *testing.T){
    r,_:=fixtureRuntime(t);holdQueue(t,r,"sample")
    a:=addQueueTest(t,r,"sample","A",nil);b:=addQueueTest(t,r,"sample","B",[]string{a});c:=addQueueTest(t,r,"sample","C",nil)
    p:=runtimeRequest{Operation:"queue_move",QueueID:c,BeforeID:a,RequestID:newID()}
    if _,err:=r.call(p);err!=nil{t.Fatal(err)};if _,err:=r.call(p);err!=nil{t.Fatal("idempotent receipt",err)}
    withQueue(r,func(){if r.queue.Entries[0].ID!=c||r.queue.Entries[1].ID!=a||r.queue.Entries[2].ID!=b{t.Fatal("wrong order")}})
    if _,err:=r.call(runtimeRequest{Operation:"queue_move",QueueID:b,BeforeID:a,RequestID:newID()});err==nil{t.Fatal("dependency inversion accepted")}
    if _,err:=r.call(runtimeRequest{Operation:"queue_cancel",QueueID:c,RequestID:newID()});err!=nil{t.Fatal(err)}
    withQueue(r,func(){if r.queueFindLocked(c).Status!="cancelled"{t.Fatal("waiting operation not cancelled")};r.queueFindLocked(a).Status="running";r.queueFindLocked(a).JobID="fake-job";r.Jobs["fake-job"]=&Job{Status:"running"}})
    if _,err:=r.call(runtimeRequest{Operation:"queue_cancel",QueueID:a,RequestID:newID()});err==nil{t.Fatal("queue cancel interrupted active work")}
}
func TestQueueRecoveryNeverReplaysStartedEntries(t *testing.T){
    r,_:=fixtureRuntime(t);holdQueue(t,r,"sample");a:=addQueueTest(t,r,"sample","A",nil);b:=addQueueTest(t,r,"sample","B",nil)
    withQueue(r,func(){r.queueFindLocked(a).Status="starting";if err:=r.queueSaveLocked();err!=nil{t.Fatal(err)}})
    dir:=r.dir;r.Close();again,err:=newRuntime(dir);if err!=nil{t.Fatal(err)};defer again.Close()
    withQueue(again,func(){if again.queue.Entries[0].ID!=a||again.queue.Entries[1].ID!=b||again.queueFindLocked(a).Status!="uncertain"||again.queueFindLocked(b).Status!="queued"||len(again.queue.Paused)==0{t.Fatal("recovery replayed or lost order")}})
}
func TestQueueOverlapReadAvailabilityAndWriteExclusion(t *testing.T){
    r,dir:=fixtureRuntime(t);resource:=holdQueue(t,r,"sample")
    if !queuePathsOverlap(resource,filepath.Join(resource,"child"))||!queuePathsOverlap(filepath.Join(resource,"child"),resource)||queuePathsOverlap(resource,resource+"-other"){t.Fatal("incorrect overlap")}
    id:=addQueueTest(t,r,"sample","active",nil)
    withQueue(r,func(){r.queueFindLocked(id).Status="running";r.queueFindLocked(id).JobID="fake-job";r.Jobs["fake-job"]=&Job{Status:"running"}})
    if err:=os.WriteFile(filepath.Join(dir,"read.txt"),[]byte("read while processing"),0600);err!=nil{t.Fatal(err)}
    done:=make(chan error,1);go func(){_,err:=r.call(runtimeRequest{Operation:"file_read",Project:"sample",Path:"read.txt"});done<-err}()
    select{case err:=<-done:if err!=nil{t.Fatal(err)};case <-time.After(2*time.Second):t.Fatal("read waited for processing")}
    if _,err:=r.call(runtimeRequest{Operation:"file_write",Project:"sample",Path:"other.txt",Expected:"missing",Content:"unsafe",RequestID:newID()});err==nil{t.Fatal("direct write raced active processing")}
    if _,err:=r.call(runtimeRequest{Operation:"queue_list",Project:"sample"});err!=nil{t.Fatal(err)}
}
func TestQueueBlockedHeadDoesNotGetBypassed(t *testing.T){
    r,_:=fixtureRuntime(t);resource:=holdQueue(t,r,"sample");other:=filepath.Join(filepath.Dir(resource),"other");if err:=os.Mkdir(other,0700);err!=nil{t.Fatal(err)};holdQueue(t,r,"other")
    dep:=addQueueTest(t,r,"other","dependency",nil);a:=addQueueTest(t,r,"sample","first",[]string{dep});b:=addQueueTest(t,r,"sample","second",nil)
    withQueue(r,func(){delete(r.queue.Paused,resource);r.queueDispatchLocked();if r.queueFindLocked(a).Status!="queued"||r.queueFindLocked(b).Status!="queued"{t.Fatal("blocked head bypassed")};r.queueFindLocked(dep).Status="completed";r.queueDispatchLocked();if r.queueFindLocked(a).JobID==""||r.queueFindLocked(b).Status!="queued"{t.Fatal("wrong dispatch order")}})
}
func TestQueueRejectsUnsafeCallsAndCrossProjectMoves(t *testing.T){
    r,_:=fixtureRuntime(t);resource:=holdQueue(t,r,"sample");other:=filepath.Join(filepath.Dir(resource),"other");os.Mkdir(other,0700);holdQueue(t,r,"other")
    a:=addQueueTest(t,r,"sample","A",nil);b:=addQueueTest(t,r,"other","B",nil)
    if _,err:=r.call(runtimeRequest{Operation:"queue_move",QueueID:a,BeforeID:b,RequestID:newID()});err==nil{t.Fatal("cross-project move accepted")}
    for _,call:=range []string{`{"operation":"file_write","path":"x","content":"x"}`,`{"operation":"command_start","profile":"cmd /c whoami"}`,`{"operation":"command_start","profile":"git_status","project":"other"}`} {
        if _,err:=r.call(runtimeRequest{Operation:"queue_add",Project:"sample",RequestID:newID(),Call:json.RawMessage(call)});err==nil{t.Fatal("unsafe call accepted",call)}
    }
}
func TestQueueIndependentProjectsCanDispatchTogether(t *testing.T){
    r,_:=fixtureRuntime(t);resource:=holdQueue(t,r,"sample");other:=filepath.Join(filepath.Dir(resource),"other");os.Mkdir(other,0700);otherResource:=holdQueue(t,r,"other")
    a:=addQueueTest(t,r,"sample","A",nil);b:=addQueueTest(t,r,"other","B",nil)
    withQueue(r,func(){delete(r.queue.Paused,resource);delete(r.queue.Paused,otherResource);r.queueDispatchLocked();if r.queueFindLocked(a).JobID==""||r.queueFindLocked(b).JobID==""{t.Fatal("independent project blocked by global job gate")}})
}
