//go:build windows

package main

import (
	"os/exec"
	"syscall"
)

// The independent child inherits the agent's Windows token, including elevation.
// CREATE_NO_WINDOW keeps an invisible console for CMD and its console children.
// DETACHED_PROCESS must not be combined with it: Windows would ignore NO_WINDOW.
func detachedBatchCommand(path string) *exec.Cmd {
	cmd := exec.Command("cmd.exe", "/D", "/C", path)
	cmd.SysProcAttr = &syscall.SysProcAttr{
		HideWindow:    true,
		CreationFlags: 0x00000200 | 0x08000000, // CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW
	}
	return cmd
}
