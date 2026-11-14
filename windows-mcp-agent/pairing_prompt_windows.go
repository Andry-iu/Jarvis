//go:build windows

package main

import (
	"bytes"
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
)

//go:embed pairing_prompt.ps1
var pairingPromptScript string

func pairingPromptCommand(ctx context.Context, p pairPrompt) *exec.Cmd {
	exe := filepath.Join(os.Getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe")
	cmd := exec.CommandContext(ctx, exe, "-NoLogo", "-NoProfile", "-NonInteractive", "-STA", "-Command", pairingPromptScript)
	// CREATE_NO_WINDOW suppresses the console; SW_HIDE must not be inherited
	// by WinForms, whose first ShowDialog would otherwise remain invisible.
	cmd.SysProcAttr = &syscall.SysProcAttr{CreationFlags: 0x08000000}
	b, _ := json.Marshal(p)
	cmd.Stdin = bytes.NewReader(b)
	return cmd
}

func confirmPeerPair(ctx context.Context, p pairPrompt) (bool, error) {
	cmd := pairingPromptCommand(ctx, p)
	out, e := cmd.Output()
	if e != nil {
		return false, errors.New("local pairing confirmation unavailable or expired")
	}
	return strings.TrimSpace(string(out)) == "ALLOW", nil
}
