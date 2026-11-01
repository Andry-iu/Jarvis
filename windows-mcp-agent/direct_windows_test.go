//go:build windows

package main

import (
 "bytes"
 "flag"
 "encoding/json"
 "regexp"
 "os"
 "os/exec"
 "path/filepath"
 "strings"
 "syscall"
 "testing"
 "time"
)
var processHelper=flag.Bool("jarvis-process-helper",false,"isolated process privilege probe")
func TestProcessStartHelper(t *testing.T){if *processHelper{time.Sleep(1500*time.Millisecond)}}
func TestProcessStartDefaultHasNonElevatedToken(t *testing.T){
 if *processHelper{return}
 exe,e:=os.Executable();if e!=nil{t.Fatal(e)}
 v,e:=launchProcess(runtimeRequest{Executable:exe,Args:[]string{"-test.run=^TestProcessStartHelper$","-jarvis-process-helper=true"}})
 if e!=nil{t.Fatal("standard-user process launch",e)}
 var pid uint32
 switch n:=v.(map[string]any)["pid"].(type){case uint32:pid=n;case int:pid=uint32(n);default:t.Fatal("unexpected PID")}
 token,e:=processToken(pid,8);if e!=nil{t.Fatal(e)};defer syscall.CloseHandle(token)
 elevated,e:=tokenElevated(token);if e!=nil||elevated{t.Fatal("default child token is not standard",elevated,e)}
}
func TestDesktopWorkerPowerShellAndNativeDeclarationsCompile(t *testing.T){
 worker:=strings.Replace(desktopWorker," $req=[Console]::In.ReadToEnd()",desktopExtensions+"\n $req=[Console]::In.ReadToEnd()",1)
 dir:=t.TempDir();path:=filepath.Join(dir,"worker.ps1");if e:=os.WriteFile(path,[]byte(worker),0600);e!=nil{t.Fatal(e)}
 // Compile the worker and native declarations without executing UI actions.
 ps:="$ErrorActionPreference='Stop';$p=[Console]::In.ReadToEnd();$tok=$null;$err=$null;$null=[Management.Automation.Language.Parser]::ParseFile($p,[ref]$tok,[ref]$err);if($err.Count){throw ($err|Out-String)};$s=[IO.File]::ReadAllText($p);foreach($m in [regex]::Matches($s,\"(?s)Add-Type -TypeDefinition @'\\r?\\n(.*?)\\r?\\n'@\")){Add-Type -TypeDefinition $m.Groups[1].Value};[Console]::Write('COMPILED')"
 cmd:=exec.Command(filepath.Join(os.Getenv("SystemRoot"),"System32","WindowsPowerShell","v1.0","powershell.exe"),"-NoProfile","-NonInteractive","-Command",ps)
 cmd.Env=childEnvironment();configureDesktopCommand(cmd);cmd.Stdin=bytes.NewBufferString(path)
 b,e:=cmd.CombinedOutput();if e!=nil||!strings.Contains(string(b),"COMPILED"){t.Fatalf("worker compile: %v\n%s",e,b)}
}

func TestDesktopEmptyRectangleProducesFiniteJSON(t *testing.T){
 re:=regexp.MustCompile("(?m)^ function Rect\\(\\$r\\).*")
 fn:=re.FindString(desktopWorker);if fn==""{t.Fatal("Rect helper missing")}
 ps:="Add-Type -AssemblyName WindowsBase;"+fn+";ConvertTo-Json -Compress -InputObject (Rect ([Windows.Rect]::Empty))"
 cmd:=exec.Command(filepath.Join(os.Getenv("SystemRoot"),"System32","WindowsPowerShell","v1.0","powershell.exe"),"-NoProfile","-NonInteractive","-Command",ps)
 cmd.Env=childEnvironment();configureDesktopCommand(cmd)
 b,e:=cmd.CombinedOutput();if e!=nil{t.Fatalf("rectangle probe: %v %s",e,b)}
 var bounds []float64
 if e=json.Unmarshal(bytes.TrimSpace(b),&bounds);e!=nil||len(bounds)!=4{t.Fatalf("invalid bounds JSON %q: %v",b,e)}
 for _,n:=range bounds{if n!=0{t.Fatal("empty rectangle must be non-actionable",bounds)}}
}
