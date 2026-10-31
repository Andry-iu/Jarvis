//go:build windows

package main

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"syscall"
	"unsafe"
)

var ntCreateFile = syscall.NewLazyDLL("ntdll.dll").NewProc("NtCreateFile")
var ntSetFile = syscall.NewLazyDLL("ntdll.dll").NewProc("NtSetInformationFile")
var finalPathName = syscall.NewLazyDLL("kernel32.dll").NewProc("GetFinalPathNameByHandleW")

type ntUnicode struct {
	Length, MaximumLength uint16
	Buffer                *uint16
}
type ntAttributes struct {
	Length            uint32
	Root              uintptr
	Name              *ntUnicode
	Attributes        uint32
	Security, Quality uintptr
}
type ntIOStatus struct{ Status, Information uintptr }

func isReparse(st os.FileInfo) bool {
	v, ok := st.Sys().(*syscall.Win32FileAttributeData)
	return ok && v.FileAttributes&0x400 != 0
}
func validateOpenedFile(root *os.Root, f *os.File) error {
	var info syscall.ByHandleFileInformation
	if e := syscall.GetFileInformationByHandle(syscall.Handle(f.Fd()), &info); e != nil {
		return e
	}
	if info.NumberOfLinks > 1 {
		return errors.New("hard-linked files excluded")
	}
	b := make([]uint16, 32768)
	n, _, e := finalPathName.Call(f.Fd(), uintptr(unsafe.Pointer(&b[0])), uintptr(len(b)), 0)
	if n == 0 || n >= uintptr(len(b)) {
		return e
	}
	path := strings.TrimPrefix(syscall.UTF16ToString(b[:n]), `\\?\`)
	rel, err := filepath.Rel(root.Name(), path)
	if err != nil {
		return err
	}
	return validRelative(rel, true)
}

// Both parents are opened through os.Root. Native calls receive only leaf names;
// ReplaceIfExists=false makes destination races fail without overwriting data.
func moveNoReplace(root *os.Root, src, dst string) error {
	if unsafe.Sizeof(uintptr(0)) != 8 {
		return errors.New("native moves require Windows AMD64")
	}
	if e := safePath(root, src, false); e != nil {
		return e
	}
	if e := safePath(root, dst, true); e != nil {
		return e
	}
	sp, e := root.Open(filepath.Dir(src))
	if e != nil {
		return e
	}
	defer sp.Close()
	dp, e := root.Open(filepath.Dir(dst))
	if e != nil {
		return e
	}
	defer dp.Close()
	if e = validateOpenedFile(root, sp); e != nil {
		return e
	}
	if e = validateOpenedFile(root, dp); e != nil {
		return e
	}
	u, e := syscall.UTF16FromString(filepath.Base(src))
	if e != nil {
		return e
	}
	name := ntUnicode{uint16((len(u) - 1) * 2), uint16(len(u) * 2), &u[0]}
	oa := ntAttributes{Root: sp.Fd(), Name: &name, Attributes: 0x40}
	oa.Length = uint32(unsafe.Sizeof(oa))
	var ios ntIOStatus
	var h syscall.Handle
	status, _, _ := ntCreateFile.Call(uintptr(unsafe.Pointer(&h)), 0x110080, uintptr(unsafe.Pointer(&oa)), uintptr(unsafe.Pointer(&ios)), 0, 0, 7, 1, 0x204020, 0, 0)
	runtime.KeepAlive(u)
	if uint32(status)&0x80000000 != 0 {
		return fmt.Errorf("native source open refused (NTSTATUS %08x)", uint32(status))
	}
	defer syscall.CloseHandle(h)
	var info syscall.ByHandleFileInformation
	if e = syscall.GetFileInformationByHandle(h, &info); e != nil {
		return e
	}
	if info.FileAttributes&0x400 != 0 {
		return errors.New("source changed to a reparse point")
	}
	dest, e := syscall.UTF16FromString(filepath.Base(dst))
	if e != nil {
		return e
	}
	buf := make([]byte, 20+len(dest)*2)
	*(*uintptr)(unsafe.Pointer(&buf[8])) = dp.Fd()
	*(*uint32)(unsafe.Pointer(&buf[16])) = uint32((len(dest) - 1) * 2)
	for i, v := range dest {
		*(*uint16)(unsafe.Pointer(&buf[20+i*2])) = v
	}
	status, _, _ = ntSetFile.Call(uintptr(h), uintptr(unsafe.Pointer(&ios)), uintptr(unsafe.Pointer(&buf[0])), uintptr(len(buf)), 10)
	if uint32(status)&0x80000000 != 0 {
		return fmt.Errorf("no-replace move refused (NTSTATUS %08x)", uint32(status))
	}
	return nil
}
