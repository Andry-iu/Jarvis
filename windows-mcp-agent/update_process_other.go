//go:build !windows

package main

import "os/exec"

func detachedBatchCommand(path string) *exec.Cmd { return exec.Command("cmd.exe", "/D", "/C", path) }