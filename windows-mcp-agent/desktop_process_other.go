//go:build !windows

package main

import "os/exec"

func configureDesktopCommand(cmd *exec.Cmd) {}
