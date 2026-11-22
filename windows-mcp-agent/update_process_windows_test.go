//go:build windows

package main

import (
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"testing"
	"time"
)

func TestDetachedBatchCommand(t *testing.T) {
	c := detachedBatchCommand(`D:\Jarvis\Scripts\updates\0123456789abcdef0123456789abcdef.cmd`)
	if len(c.Args) != 4 || c.Args[1] != "/D" || c.Args[2] != "/C" || !strings.HasSuffix(c.Args[3], ".cmd") {
		t.Fatalf("unexpected args: %q", c.Args)
	}
	if c.SysProcAttr == nil || !c.SysProcAttr.HideWindow ||
		c.SysProcAttr.CreationFlags&0x08000000 == 0 ||
		c.SysProcAttr.CreationFlags&0x00000200 == 0 ||
		c.SysProcAttr.CreationFlags&(0x00000008|0x00000010) != 0 {
		t.Fatal("batch must use NO_WINDOW and NEW_PROCESS_GROUP without DETACHED or NEW_CONSOLE")
	}
}

type silentBatchProbe struct {
	ConsoleVisible bool
	Elevated       bool
	Error          string
}

// Used only in a separate test process launched by the integration test.
func TestSilentBatchHelper(t *testing.T) {
	var args []string
	for i, arg := range os.Args {
		if arg == "--" {
			args = os.Args[i+1:]
			break
		}
	}
	if len(args) != 2 {
		return
	}
	switch args[0] {
	case "launch":
		c := detachedBatchCommand(args[1])
		if err := c.Start(); err != nil {
			os.Exit(2)
		}
		// Exit without waiting: the batch and PowerShell must outlive this parent.
		os.Exit(0)
	case "probe":
		r := silentBatchProbe{}
		window, _, _ := syscall.NewLazyDLL("kernel32.dll").NewProc("GetConsoleWindow").Call()
		if window != 0 {
			visible, _, _ := syscall.NewLazyDLL("user32.dll").NewProc("IsWindowVisible").Call(window)
			r.ConsoleVisible = visible != 0
		}
		token, err := processToken(uint32(os.Getpid()), 8)
		if err == nil {
			r.Elevated, err = tokenElevated(token)
			syscall.CloseHandle(token)
		}
		if err != nil {
			r.Error = err.Error()
		}
		data, _ := json.Marshal(r)
		if err := os.WriteFile(args[1], data, 0600); err != nil {
			os.Exit(3)
		}
		os.Exit(0)
	}
}

func TestSilentBatchSurvivesParentAndPreservesToken(t *testing.T) {
	dir := t.TempDir()
	exe, err := os.Executable()
	if err != nil {
		t.Fatal(err)
	}
	result := filepath.Join(dir, "result.json")
	batch := filepath.Join(dir, "batch with spaces.cmd")
	quotePS := func(s string) string { return "'" + strings.ReplaceAll(s, "'", "''") + "'" }
	ps := "Start-Sleep -Milliseconds 400; & " + quotePS(exe) +
		" '-test.run=^TestSilentBatchHelper$' '--' 'probe' " + quotePS(result)
	content := "@echo off\r\npowershell.exe -NoProfile -NonInteractive -Command \"" + ps + "\"\r\n"
	if err := os.WriteFile(batch, []byte(content), 0600); err != nil {
		t.Fatal(err)
	}
	token, err := processToken(uint32(os.Getpid()), 8)
	if err != nil {
		t.Fatal(err)
	}
	elevated, err := tokenElevated(token)
	syscall.CloseHandle(token)
	if err != nil {
		t.Fatal(err)
	}
	parent := exec.Command(exe, "-test.run=^TestSilentBatchHelper$", "--", "launch", batch)
	parent.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	if err := parent.Run(); err != nil {
		t.Fatal(err)
	}
	deadline := time.Now().Add(20 * time.Second)
	var data []byte
	for time.Now().Before(deadline) {
		data, err = os.ReadFile(result)
		if err == nil && json.Valid(data) {
			break
		}
		time.Sleep(25 * time.Millisecond)
	}
	var got silentBatchProbe
	if err := json.Unmarshal(data, &got); err != nil {
		t.Fatalf("descendant did not complete after launcher exit: %v", err)
	}
	if got.Error != "" || got.ConsoleVisible || got.Elevated != elevated {
		t.Fatal(fmt.Sprintf("console/token contract failed: %+v; parent elevated=%v", got, elevated))
	}
}
