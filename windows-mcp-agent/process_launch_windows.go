//go:build windows

package main

import (
 "errors"
 "fmt"
 "os"
 "os/exec"
 "strings"
 "syscall"
 "unsafe"
)

var launchKernel=syscall.NewLazyDLL("kernel32.dll")
var launchAdvapi=syscall.NewLazyDLL("advapi32.dll")
var launchUser=syscall.NewLazyDLL("user32.dll")
var launchShell=syscall.NewLazyDLL("shell32.dll")
var launchUserenv=syscall.NewLazyDLL("userenv.dll")

func tokenElevated(token syscall.Handle)(bool,error){
 var v,n uint32
 ok,_,e:=launchAdvapi.NewProc("GetTokenInformation").Call(uintptr(token),20,uintptr(unsafe.Pointer(&v)),4,uintptr(unsafe.Pointer(&n)))
 if ok==0{return false,e};return v!=0,nil
}
func processToken(pid uint32,access uint32)(syscall.Handle,error){
 h,e:=syscall.OpenProcess(0x1000,false,pid);if e!=nil{return 0,e};defer syscall.CloseHandle(h)
 var token syscall.Handle
 ok,_,e:=launchAdvapi.NewProc("OpenProcessToken").Call(uintptr(h),uintptr(access),uintptr(unsafe.Pointer(&token)))
 if ok==0{return 0,e};return token,nil
}
func launchProcess(p runtimeRequest)(any,error){
 exe,e:=validateProcessRequest(p);if e!=nil{return nil,e}
 own,e:=processToken(uint32(os.Getpid()),8);if e!=nil{return nil,e}
 elevated,e:=tokenElevated(own);syscall.CloseHandle(own);if e!=nil{return nil,e}
 if p.Elevated&&!elevated{return nil,errors.New("explicit elevation requires an elevated agent; no implicit UAC or retry")}
 if !p.Elevated&&elevated{return launchWithShellToken(p,exe)}
 cmd:=exec.Command(exe,p.Args...);cmd.Dir=p.WorkingDirectory;cmd.Env=childEnvironment()
 if e=cmd.Start();e!=nil{return nil,e}
 pid:=cmd.Process.Pid
 // Reap without making the lifetime of the application depend on the MCP call.
 go func(){_ = cmd.Wait()}()
 return map[string]any{"pid":pid,"executable":exe,"elevated":p.Elevated,"status":"started","privilege_source":"agent_token"},nil
}

// An elevated agent uses only the token of its own interactive shell session.
// The shell token must actually be non-elevated. Failure never falls back to admin.
// https://learn.microsoft.com/windows/win32/api/winbase/nf-winbase-createprocesswithtokenw
func launchWithShellToken(p runtimeRequest,exe string)(any,error){
 shell,_,_:=launchUser.NewProc("GetShellWindow").Call();if shell==0{return nil,errors.New("non-elevated interactive shell unavailable")}
 var pid uint32
 launchUser.NewProc("GetWindowThreadProcessId").Call(shell,uintptr(unsafe.Pointer(&pid)))
 var ownSession,shellSession uint32
 for _,v:=range []struct{pid uint32;dest *uint32}{{uint32(os.Getpid()),&ownSession},{pid,&shellSession}}{ok,_,e:=launchKernel.NewProc("ProcessIdToSessionId").Call(uintptr(v.pid),uintptr(unsafe.Pointer(v.dest)));if ok==0{return nil,e}}
 if ownSession==0||ownSession!=shellSession{return nil,errors.New("shell is not in the agent interactive session")}
 token,e:=processToken(pid,0x0002|0x0008|0x0001);if e!=nil{return nil,fmt.Errorf("cannot obtain standard shell token: %w",e)};defer syscall.CloseHandle(token)
 high,e:=tokenElevated(token);if e!=nil||high{return nil,errors.New("standard shell token could not be verified")}
 var primary syscall.Handle
 ok,_,e:=launchAdvapi.NewProc("DuplicateTokenEx").Call(uintptr(token),0x02000000,0,2,1,uintptr(unsafe.Pointer(&primary)))
 if ok==0{return nil,e};defer syscall.CloseHandle(primary)
 var env uintptr
 ok,_,e=launchUserenv.NewProc("CreateEnvironmentBlock").Call(uintptr(unsafe.Pointer(&env)),uintptr(primary),0)
 if ok==0{return nil,e};defer launchUserenv.NewProc("DestroyEnvironmentBlock").Call(env)
 app,_:=syscall.UTF16PtrFromString(exe)
 argv:=[]string{syscall.EscapeArg(exe)};for _,arg:=range p.Args{argv=append(argv,syscall.EscapeArg(arg))}
 line,err:=syscall.UTF16FromString(strings.Join(argv," "));if err!=nil||len(line)>32767{return nil,errors.New("process command line exceeds Windows limit")}
 var dir *uint16;if p.WorkingDirectory!=""{dir,_=syscall.UTF16PtrFromString(p.WorkingDirectory)}
 desktop,_:=syscall.UTF16PtrFromString("winsta0\\default")
 si:=syscall.StartupInfo{Cb:uint32(unsafe.Sizeof(syscall.StartupInfo{})),Desktop:desktop}
 var pi syscall.ProcessInformation
 ok,_,e=launchAdvapi.NewProc("CreateProcessWithTokenW").Call(uintptr(primary),0,uintptr(unsafe.Pointer(app)),uintptr(unsafe.Pointer(&line[0])),0x400,env,uintptr(unsafe.Pointer(dir)),uintptr(unsafe.Pointer(&si)),uintptr(unsafe.Pointer(&pi)))
 if ok==0{return nil,fmt.Errorf("standard-user launch failed: %w",e)}
 defer syscall.CloseHandle(pi.Process);defer syscall.CloseHandle(pi.Thread)
 return map[string]any{"pid":pi.ProcessId,"executable":exe,"elevated":false,"status":"started","privilege_source":"verified_standard_shell_token"},nil
}

func knownFolder(name string)(any,error){
 // KNOWNFOLDERID values are stable Windows identifiers, not localized paths.
 guids:=map[string]syscall.GUID{
 "desktop":{Data1:0xB4BFCC3A,Data2:0xDB2C,Data3:0x424C,Data4:[8]byte{0xB0,0x29,0x7F,0xE9,0x9A,0x87,0xC6,0x41}},
 "documents":{Data1:0xFDD39AD0,Data2:0x238F,Data3:0x46AF,Data4:[8]byte{0xAD,0xB4,0x6C,0x85,0x48,0x03,0x69,0xC7}},
 "downloads":{Data1:0x374DE290,Data2:0x123F,Data3:0x4565,Data4:[8]byte{0x91,0x64,0x39,0xC4,0x92,0x5E,0x46,0x7B}},
 }
 id,found:=guids[name];if !found{return nil,errors.New("folder must be desktop, documents or downloads")}
 var ptr *uint16
 hr,_,_:=launchShell.NewProc("SHGetKnownFolderPath").Call(uintptr(unsafe.Pointer(&id)),0,0,uintptr(unsafe.Pointer(&ptr)))
 if int32(hr)<0||ptr==nil{return nil,errors.New("known folder unavailable")}
 defer syscall.NewLazyDLL("ole32.dll").NewProc("CoTaskMemFree").Call(uintptr(unsafe.Pointer(ptr)))
 chars:=unsafe.Slice(ptr,32768);n:=0;for n<len(chars)&&chars[n]!=0{n++};if n==len(chars){return nil,errors.New("known folder path exceeds limit")}
 return map[string]any{"folder":name,"path":syscall.UTF16ToString(chars[:n])},nil
}
