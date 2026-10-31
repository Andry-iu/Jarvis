//go:build !windows
package main
import "os/exec"
func runManagedCommand(c *exec.Cmd)error{return c.Run()}
