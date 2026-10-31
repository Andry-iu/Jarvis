//go:build windows

package main

import (
	"os"
	"path/filepath"
	"syscall"
	"testing"
	"time"
)

func TestRuntimeWriteWaitsForSharingLock(t *testing.T) {
	r, dir := fixtureRuntime(t)
	path := filepath.Join(dir, "locked.txt")
	if e := os.WriteFile(path, []byte("original"), 0600); e != nil {
		t.Fatal(e)
	}
	name, _ := syscall.UTF16PtrFromString(path)
	h, e := syscall.CreateFile(name, syscall.GENERIC_READ, syscall.FILE_SHARE_READ, nil, syscall.OPEN_EXISTING, syscall.FILE_ATTRIBUTE_NORMAL, 0)
	if e != nil {
		t.Fatal(e)
	}
	released := make(chan struct{})
	go func() {
		time.Sleep(250 * time.Millisecond)
		syscall.CloseHandle(h)
		close(released)
	}()
	root, e := r.projectRoot("sample")
	if e != nil {
		<-released
		t.Fatal(e)
	}
	defer root.Close()
	e = writeRootFile(root, "locked.txt", []byte("replacement"), digest([]byte("original")))
	<-released
	if e != nil {
		t.Fatal(e)
	}
	b, _ := os.ReadFile(path)
	if string(b) != "replacement" {
		t.Fatal("replacement failed after releasing sharing lock")
	}
}

func TestRuntimeNativeMoveDirectoryAndNoReplace(t *testing.T) {
	r, dir := fixtureRuntime(t)
	os.Mkdir(filepath.Join(dir, "folder"), 0700)
	os.WriteFile(filepath.Join(dir, "folder", "one.txt"), []byte("keep"), 0600)
	root, e := r.projectRoot("sample")
	if e != nil {
		t.Fatal(e)
	}
	h, e := pathDigest(root, "folder")
	root.Close()
	if e != nil {
		t.Fatal(e)
	}
	p := runtimeRequest{Operation: "path_move", Project: "sample", Path: "folder", Destination: "renamed", Expected: h, RequestID: newID()}
	v := mutateFile(t, r, p)
	b, _ := os.ReadFile(filepath.Join(dir, "renamed", "one.txt"))
	if string(b) != "keep" {
		t.Fatal("directory content lost")
	}
	mutateFile(t, r, runtimeRequest{Operation: "file_restore", RestoreID: v.(map[string]any)["request_id"].(string)})
	os.WriteFile(filepath.Join(dir, "from.txt"), []byte("source"), 0600)
	os.WriteFile(filepath.Join(dir, "to.txt"), []byte("destination"), 0600)
	root, e = r.projectRoot("sample")
	if e != nil {
		t.Fatal(e)
	}
	defer root.Close()
	if e = moveNoReplace(root, "from.txt", "to.txt"); e == nil {
		t.Fatal("overwrote existing destination")
	}
	b, _ = os.ReadFile(filepath.Join(dir, "to.txt"))
	if string(b) != "destination" {
		t.Fatal("destination altered")
	}
	if e = os.Link(filepath.Join(dir, "from.txt"), filepath.Join(dir, "alias.txt")); e == nil {
		if _, e = readRegular(root, "alias.txt"); e == nil {
			t.Fatal("hardlink accepted")
		}
	}
}
func TestRuntimeCommandCompletionTimeoutAndCancellation(t *testing.T) {
	r, dir := fixtureRuntime(t)
	os.WriteFile(filepath.Join(dir, "go.mod"), []byte("module runtime-fixture\n\ngo 1.27.0\n"), 0600)
	os.WriteFile(filepath.Join(dir, "main.go"), []byte("package main\nfunc main() {}\n"), 0600)
	run := func(profile string, timeout int) string {
		v := mutateFile(t, r, runtimeRequest{Operation: "command_start", Project: "sample", Profile: profile, Timeout: timeout})
		return v.(map[string]any)["job_id"].(string)
	}
	wait := func(id string) Job {
		t.Helper()
		deadline := time.Now().Add(45 * time.Second)
		for time.Now().Before(deadline) {
			v, e := r.jobCall(runtimeRequest{Operation: "job_status", JobID: id})
			if e != nil {
				t.Fatal(e)
			}
			j := v.(Job)
			if j.Status != "running" {
				return j
			}
			time.Sleep(50 * time.Millisecond)
		}
		t.Fatal("job did not finish")
		return Job{}
	}
	id := run("go_build", 30)
	if j := wait(id); j.Status != "completed" {
		t.Fatalf("build: %+v", j)
	}
	os.WriteFile(filepath.Join(dir, "slow_test.go"), []byte("package main\nimport(\"testing\";\"time\")\nfunc TestSlow(t *testing.T){time.Sleep(20*time.Second)}\n"), 0600)
	id = run("go_test", 1)
	if j := wait(id); j.Status != "timed_out" {
		t.Fatalf("timeout: %+v", j)
	}
	id = run("go_test", 30)
	time.Sleep(250 * time.Millisecond)
	if _, e := r.jobCall(runtimeRequest{Operation: "job_cancel", JobID: id, RequestID: newID()}); e != nil {
		t.Fatal(e)
	}
	if j := wait(id); j.Status != "cancelled" {
		t.Fatalf("cancel: %+v", j)
	}
}
