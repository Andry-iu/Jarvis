//go:build windows

package main

import (
	"errors"
	"os"
	"syscall"
	"unsafe"
)

var lockFileEx = syscall.NewLazyDLL("kernel32.dll").NewProc("LockFileEx")

func lockRuntime(path string) (*os.File, error) {
	f, e := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0600)
	if e != nil {
		return nil, e
	}
	var ov syscall.Overlapped
	ok, _, _ := lockFileEx.Call(f.Fd(), 3, 0, 1, 0, uintptr(unsafe.Pointer(&ov)))
	if ok == 0 {
		f.Close()
		return nil, errors.New("runtime state is already locked by another agent")
	}
	return f, nil
}
