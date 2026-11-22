//go:build windows

package main

import (
 "encoding/json"
 "os"
 "path/filepath"
 "strconv"
 "strings"
 "syscall"
 "testing"
 "time"
)

func TestGoTaskCancellationStopsRunningTestProcess(t *testing.T) {
 root:=t.TempDir()
 t.Setenv("JARVIS_PROJECTS_ROOT",root)
 dir:=filepath.Join(root,"probe")
 if err:=os.Mkdir(dir,0700);err!=nil {t.Fatal(err)}
 if err:=os.WriteFile(filepath.Join(dir,"go.mod"),[]byte("module cancel-probe\n\ngo 1.23.2\n"),0600);err!=nil {t.Fatal(err)}
 ready:=filepath.Join(dir,"ready")
 source:="package probe\nimport(\"os\";\"strconv\";\"testing\";\"time\")\nfunc TestWait(t *testing.T){os.WriteFile("+strconv.Quote(ready)+",[]byte(strconv.Itoa(os.Getpid())),0600);time.Sleep(30*time.Second)}\n"
 if err:=os.WriteFile(filepath.Join(dir,"wait_test.go"),[]byte(source),0600);err!=nil {t.Fatal(err)}
 a:=&Agent{}
 if _,err:=a.startGoTask("go_test","probe");err!=nil {t.Fatal(err)}
 defer a.stop()
 var pid int
 deadline:=time.Now().Add(25*time.Second)
 for time.Now().Before(deadline) {
  b,err:=os.ReadFile(ready)
  if err==nil {pid,_=strconv.Atoi(string(b));if pid>0 {break}}
  time.Sleep(50*time.Millisecond)
 }
 if pid==0 {t.Fatal("test child never started")}
 h,err:=syscall.OpenProcess(syscall.SYNCHRONIZE,false,uint32(pid))
 if err!=nil {t.Fatal(err)}
 defer syscall.CloseHandle(h)
 if _,err=a.stop();err!=nil {t.Fatal(err)}
 wait,err:=syscall.WaitForSingleObject(h,8000)
 if err!=nil || wait!=syscall.WAIT_OBJECT_0 {t.Fatalf("test child survived cancellation: wait=%v err=%v",wait,err)}
 deadline=time.Now().Add(5*time.Second)
 for time.Now().Before(deadline) {
  a.mu.Lock()
  status,result:=a.task.Status,a.task.Result
  a.mu.Unlock()
  if status!="running" {
   if status!="cancelled" || !strings.Contains(result,"context canceled") {t.Fatalf("status=%s result=%s",status,result)}
   return
  }
  time.Sleep(50*time.Millisecond)
 }
 b,_:=json.Marshal(a.snapshot())
 t.Fatalf("task did not settle: %s",b)
}
