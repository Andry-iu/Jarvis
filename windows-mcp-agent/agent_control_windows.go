//go:build windows

package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"syscall"
	"time"
	"unsafe"
)

func currentProcessElevated() (bool, error) {
	t, e := processToken(uint32(os.Getpid()), 8)
	if e != nil {
		return false, e
	}
	defer syscall.CloseHandle(t)
	return tokenElevated(t)
}
func processCreated(h syscall.Handle) (uint64, error) {
	var c, e, k, u syscall.Filetime
	if err := syscall.GetProcessTimes(h, &c, &e, &k, &u); err != nil {
		return 0, err
	}
	return uint64(c.HighDateTime)<<32 | uint64(c.LowDateTime), nil
}
func restartIdentity() (string, uint64, error) {
	t, e := syscall.OpenCurrentProcessToken()
	if e != nil {
		return "", 0, e
	}
	defer t.Close()
	u, e := t.GetTokenUser()
	if e != nil {
		return "", 0, e
	}
	sid, e := u.User.Sid.String()
	if e != nil {
		return "", 0, e
	}
	h, e := syscall.GetCurrentProcess()
	if e != nil {
		return "", 0, e
	}
	created, e := processCreated(h)
	return sid, created, e
}

type shellExecuteInfo struct {
	Size       uint32
	Mask       uint32
	Window     uintptr
	Verb       *uint16
	File       *uint16
	Parameters *uint16
	Directory  *uint16
	Show       int32
	Instance   uintptr
	IDList     uintptr
	Class      *uint16
	ClassKey   uintptr
	HotKey     uint32
	Icon       uintptr
	Process    syscall.Handle
}

func launchAdminRestart(x *adminRestart) error {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	ole := syscall.NewLazyDLL("ole32.dll")
	hr, _, _ := ole.NewProc("CoInitializeEx").Call(0, 2)
	if int32(hr) < 0 {
		return errors.New("elevation COM initialization failed")
	}
	defer ole.NewProc("CoUninitialize").Call()
	verb, _ := syscall.UTF16PtrFromString("runas")
	file, _ := syscall.UTF16PtrFromString(x.Executable)
	args := "-restart-admin-helper " + syscall.EscapeArg(x.ID) + " -restart-home " + syscall.EscapeArg(x.Home)
	params, e := syscall.UTF16PtrFromString(args)
	if e != nil {
		return e
	}
	dir, e := syscall.UTF16PtrFromString(x.Home)
	if e != nil {
		return e
	}
	info := shellExecuteInfo{Mask: 0x40 | 0x100, Verb: verb, File: file, Parameters: params, Directory: dir, Show: 0}
	info.Size = uint32(unsafe.Sizeof(info))
	ok, _, e := launchShell.NewProc("ShellExecuteExW").Call(uintptr(unsafe.Pointer(&info)))
	if ok == 0 {
		if errors.Is(e, syscall.Errno(1223)) {
			return errElevationCancelled
		}
		return errors.New("Windows elevation launch failed")
	}
	if info.Process == 0 {
		return errors.New("elevation process handle unavailable")
	}
	defer syscall.CloseHandle(info.Process)
	// A late approval cannot stop the original: the handoff independently checks expiry.
	result, e := syscall.WaitForSingleObject(info.Process, 180000)
	if e != nil {
		return e
	}
	if result != 0 {
		return errors.New("elevation helper outcome unavailable; inspect status")
	}
	var code uint32
	if e = syscall.GetExitCodeProcess(info.Process, &code); e != nil {
		return e
	}
	if code != 0 {
		return errors.New("elevation helper refused or failed")
	}
	return nil
}
func localRestartCall(listen, name string, args any) (json.RawMessage, error) {
	b, _ := json.Marshal(map[string]any{"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": map[string]any{"name": name, "arguments": args}})
	req, e := http.NewRequest("POST", "http://"+listen+"/mcp", bytes.NewReader(b))
	if e != nil {
		return nil, e
	}
	req.Header.Set("Authorization", "Bearer "+os.Getenv("JARVIS_MCP_TOKEN"))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json, text/event-stream")
	client := &http.Client{Timeout: 5 * time.Second, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	resp, e := client.Do(req)
	if e != nil {
		return nil, e
	}
	defer resp.Body.Close()
	var rpc struct {
		Error  json.RawMessage `json:"error"`
		Result struct {
			IsError bool `json:"isError"`
			Content []struct {
				Text string `json:"text"`
			} `json:"content"`
		} `json:"result"`
	}
	if resp.StatusCode != 200 || json.NewDecoder(resp.Body).Decode(&rpc) != nil || len(rpc.Error) > 0 && string(rpc.Error) != "null" || rpc.Result.IsError || len(rpc.Result.Content) != 1 {
		return nil, errors.New("local verification unavailable")
	}
	return json.RawMessage(rpc.Result.Content[0].Text), nil
}
func runAdminRestartHelper(home, id string) (resultErr error) {
	elevated, e := currentProcessElevated()
	if e != nil || !elevated {
		return errors.New("elevated helper required")
	}
	dir := restartHomeState(home)
	x, e := readRestart(dir, id)
	if e != nil {
		return e
	}
	handed := false
	defer func() {
		if resultErr != nil {
			x.Reason = "helper_verification_or_start_failed"
			x.Phase = "failed_before_handoff"
			if handed {
				x.Phase = "failed_after_handoff"
			}
			_ = saveRestart(x)
		}
	}()
	exe, e := os.Executable()
	if e != nil {
		return e
	}
	sid, _, e := restartIdentity()
	if e != nil {
		return e
	}
	if e = validateRestartRecord(x, home, exe, sid); e != nil {
		return e
	}
	if !samePath(x.StateDir, dir) {
		return errors.New("state directory changed")
	}
	parent, e := syscall.OpenProcess(0x1000|0x100000, false, uint32(x.ParentPID))
	if e != nil {
		return e
	}
	defer syscall.CloseHandle(parent)
	created, e := processCreated(parent)
	if e != nil || created != x.ParentCreated {
		return errors.New("original process changed")
	}
	var buf [32768]uint16
	n := uint32(len(buf))
	ok, _, _ := launchKernel.NewProc("QueryFullProcessImageNameW").Call(uintptr(parent), 0, uintptr(unsafe.Pointer(&buf[0])), uintptr(unsafe.Pointer(&n)))
	if ok == 0 || !samePath(syscall.UTF16ToString(buf[:n]), x.Executable) {
		return errors.New("original executable changed")
	}
	// Verify configured owner/start profile before requesting the old process to stop.
	var manifest struct {
		Home    string `json:"home"`
		SID     string `json:"user_sid"`
		Version string `json:"agent_version"`
		Hash    string `json:"agent_sha256"`
	}
	manifestPath := filepath.Join(home, "Config", "installation.json")
	managed := false
	if b, err := os.ReadFile(manifestPath); err == nil {
		managed = true
		if json.Unmarshal(b, &manifest) != nil || !samePath(manifest.Home, home) || manifest.SID != sid || manifest.Version != version || !strings.EqualFold(manifest.Hash, x.SHA256) {
			return errors.New("installation profile changed")
		}
	} else if !os.IsNotExist(err) {
		return err
	}
	startScript := filepath.Join(home, "Scripts", "Start.ps1")
	if managed {
		st, err := os.Lstat(startScript)
		if err != nil || !st.Mode().IsRegular() || isReparse(st) {
			return errors.New("configured supervisor unavailable")
		}
	}
	body, _ := json.Marshal(map[string]string{"request_id": id, "nonce": x.Nonce})
	req, e := http.NewRequest("POST", "http://"+x.Listen+"/internal/agent-restart", bytes.NewReader(body))
	if e != nil {
		return e
	}
	req.Header.Set("Authorization", "Bearer "+os.Getenv("JARVIS_MCP_TOKEN"))
	req.Header.Set("Content-Type", "application/json")
	resp, e := (&http.Client{Timeout: 10 * time.Second}).Do(req)
	if e != nil {
		return e
	}
	resp.Body.Close()
	if resp.StatusCode != 200 {
		return errors.New("original process refused handoff")
	}
	handed = true
	wait, e := syscall.WaitForSingleObject(parent, 30000)
	if e != nil || wait != 0 {
		return errors.New("original process did not stop; no second instance started")
	}
	if managed {
		ready := false
		for i := 0; i < 100; i++ {
			p, _ := syscall.UTF16PtrFromString(filepath.Join(home, "Config", "runner.lock"))
			h, err := syscall.CreateFile(p, syscall.GENERIC_READ|syscall.GENERIC_WRITE, 0, nil, syscall.OPEN_ALWAYS, syscall.FILE_ATTRIBUTE_NORMAL, 0)
			if err == nil {
				syscall.CloseHandle(h)
				ready = true
				break
			}
			time.Sleep(100 * time.Millisecond)
		}
		if !ready {
			return errors.New("configured supervisor still holds its lock")
		}
	}
	var cmd *exec.Cmd
	if managed {
		cmd = exec.Command(filepath.Join(os.Getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe"), "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", startScript, "-HomeDir", home)
	} else {
		cmd = exec.Command(exe, "-listen", x.Listen)
	}
	cmd.Dir = home
	cmd.Env = os.Environ()
	cmd.SysProcAttr = &syscall.SysProcAttr{CreationFlags: 0x08000000 | 0x00000200, HideWindow: true}
	if e = cmd.Start(); e != nil {
		return e
	}
	go func() { _ = cmd.Wait() }()
	var observed struct {
		Machine    string          `json:"machine_id"`
		Version    string          `json:"version"`
		Privileges agentPrivileges `json:"privileges"`
	}
	for i := 0; i < 80; i++ {
		b, err := localRestartCall(x.Listen, "machine_info", map[string]any{})
		if err == nil && json.Unmarshal(b, &observed) == nil && observed.Machine == x.Machine && observed.Version == version && observed.Privileges.PID != x.ParentPID && observed.Privileges.Elevated != nil && *observed.Privileges.Elevated {
			x.NewPID = observed.Privileges.PID
			x.Phase = "completed"
			x.Reason = ""
			return saveRestart(x)
		}
		time.Sleep(250 * time.Millisecond)
	}
	return fmt.Errorf("new elevated agent not verified; inspect the configured supervisor")
}
