//go:build windows

package main

import (
	"os"
	"path/filepath"
	"syscall"
	"testing"
)

func TestReadRefusesConcurrentWriterThenRecovers(t *testing.T) {
	r, dir := fixtureRuntime(t)
	path := filepath.Join(dir, "writing.txt")
	if err := os.WriteFile(path, []byte("stable"), 0600); err != nil {
		t.Fatal(err)
	}
	name, err := syscall.UTF16PtrFromString(path)
	if err != nil {
		t.Fatal(err)
	}
	h, err := syscall.CreateFile(name, syscall.GENERIC_WRITE, syscall.FILE_SHARE_READ|syscall.FILE_SHARE_WRITE, nil, syscall.OPEN_EXISTING, syscall.FILE_ATTRIBUTE_NORMAL, 0)
	if err != nil {
		t.Fatal(err)
	}
	_, readErr := r.call(runtimeRequest{Operation: "file_read", Project: "sample", Path: "writing.txt"})
	syscall.CloseHandle(h)
	if readErr == nil {
		t.Fatal("file held by writer was exposed")
	}
	v, err := r.call(runtimeRequest{Operation: "file_read", Project: "sample", Path: "writing.txt"})
	if err != nil || v.(map[string]any)["content"] != "stable" {
		t.Fatal("stable read did not recover", err)
	}
}

