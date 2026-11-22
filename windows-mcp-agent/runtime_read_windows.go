//go:build windows

package main

import (
	"errors"
	"os"
	"path/filepath"
	"runtime"
	"syscall"
	"unsafe"
)

// Open a leaf relative to a verified parent. FILE_SHARE_READ excludes writer
// and delete handles for this short snapshot, without a project-wide lock.
func openStableRead(root *os.Root, n string) (*os.File, error) {
	parent, err := root.Open(filepath.Dir(n))
	if err != nil {
		return nil, err
	}
	defer parent.Close()
	if err = validateOpenedFile(root, parent); err != nil {
		return nil, err
	}
	u, err := syscall.UTF16FromString(filepath.Base(n))
	if err != nil {
		return nil, err
	}
	name := ntUnicode{uint16((len(u) - 1) * 2), uint16(len(u) * 2), &u[0]}
	oa := ntAttributes{Root: parent.Fd(), Name: &name, Attributes: 0x40}
	oa.Length = uint32(unsafe.Sizeof(oa))
	var ios ntIOStatus
	var h syscall.Handle
	status, _, _ := ntCreateFile.Call(uintptr(unsafe.Pointer(&h)), 0x120089, uintptr(unsafe.Pointer(&oa)), uintptr(unsafe.Pointer(&ios)), 0, 0, 1, 1, 0x200060, 0, 0)
	runtime.KeepAlive(u)
	if uint32(status)&0x80000000 != 0 {
		return nil, errors.New("file busy or unavailable for a stable read; no partial content returned")
	}
	f := os.NewFile(uintptr(h), n)
	if f == nil {
		syscall.CloseHandle(h)
		return nil, errors.New("file handle unavailable")
	}
	var info syscall.ByHandleFileInformation
	if err = syscall.GetFileInformationByHandle(h, &info); err != nil || info.FileAttributes&0x400 != 0 {
		f.Close()
		return nil, errors.New("file changed or became a reparse point")
	}
	return f, nil
}

