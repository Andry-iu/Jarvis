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
)

//go:embed pairing_prompt.ps1
var pairingPromptScript string

func confirmPeerPair(ctx context.Context, p pairPrompt) (bool, error) {
	exe := filepath.Join(os.Getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe")
	cmd := exec.CommandContext(ctx, exe, "-NoLogo", "-NoProfile", "-NonInteractive", "-STA", "-Command", pairingPromptScript)
	configureDesktopCommand(cmd)
	b, _ := json.Marshal(p)
	cmd.Stdin = bytes.NewReader(b)
	out, e := cmd.Output()
	if e != nil {
		return false, errors.New("local pairing confirmation unavailable or expired")
	}
	return strings.TrimSpace(string(out)) == "ALLOW", nil
}
