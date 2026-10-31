//go:build windows

package main

import (
	"os/exec"
	"syscall"
)

// Detached child inherits the agent's Windows token (including elevation).
// /D disables AutoRun, and /C runs one absolute batch path.
func detachedBatchCommand(path string) *exec.Cmd {
	cmd := exec.Command("cmd.exe", "/D", "/C", path)
	cmd.SysProcAttr = &syscall.SysProcAttr{CreationFlags: 0x00000200 | 0x00000008} // CREATE_NEW_PROCESS_GROUP | DETACHED_PROCESS
	return cmd
}