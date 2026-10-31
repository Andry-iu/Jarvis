//go:build !windows
package main
import "os"
func protectRuntimeDirectory(dir string)error{return os.Chmod(dir,0700)}
