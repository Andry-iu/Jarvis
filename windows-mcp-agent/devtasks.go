package main

import (
 "context"
 "errors"
 "fmt"
 "os"
 "os/exec"
 "path/filepath"
 "strings"
 "time"
)

// startGoTask only permits fixed Go operations inside an explicitly authorized project.
func (a *Agent) startGoTask(operation, project string) (any, error) {
 if operation != "go_test" && operation != "go_build" {
  return nil, errors.New("unsupported operation")
 }
 dir, err := resolveProject(project)
 if err != nil { return nil, err }
 if info, err := os.Stat(filepath.Join(dir, "go.mod")); err != nil || info.IsDir() {
  return nil, errors.New("selected directory is not a Go module root")
 }
 a.mu.Lock()
 defer a.mu.Unlock()
 if a.task != nil && a.task.Status == "running" { return nil, errors.New("a task is already running") }
 a.next++
 ctx, cancel := context.WithTimeout(context.Background(), 120*time.Second)
 t := &Task{ID:fmt.Sprintf("task-%d",a.next),Name:operation,Status:"running",StartedAt:time.Now().Format(time.RFC3339),cancel:cancel}
 a.task=t
 go func() {
  defer cancel()
  var cmd *exec.Cmd
  if operation=="go_test" {
   cmd=exec.CommandContext(ctx,"go","test","./...")
  } else {
   staging:=filepath.Join(dir,"Staging")
   if err:=os.MkdirAll(staging,0700);err!=nil {
    a.completeGoTask(t,"failed","cannot create staging directory: "+err.Error());return
   }
   cmd=exec.CommandContext(ctx,"go","build","-o",filepath.Join(staging,"jarvis-mcp.exe"),".")
  }
  cmd.Dir=dir
  cmd.Env=append(os.Environ(),"GOWORK=off","GOTOOLCHAIN=local")
  out,runErr:=cmd.CombinedOutput()
  result:=strings.TrimSpace(string(out))
  if len(result)>12000 {result=result[len(result)-12000:]}
  if runErr!=nil {
   status:="failed"
   if ctx.Err()!=nil {status="cancelled"}
   a.completeGoTask(t,status,fmt.Sprintf("%s: %v\n%s",operation,runErr,result))
  } else {a.completeGoTask(t,"completed",operation+" succeeded\n"+result)}
 }()
 return map[string]any{"task_id":t.ID,"operation":operation,"status":"running"},nil
}
func (a *Agent) completeGoTask(t *Task,status,result string){
 a.mu.Lock();defer a.mu.Unlock()
 t.Status=status
 t.Result=result
 t.FinishedAt=time.Now().Format(time.RFC3339)
 if status=="completed" {t.Progress=100}
}
