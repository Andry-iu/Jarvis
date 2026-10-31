package main

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestCloneInputValidation(t *testing.T) {
	ok := []string{"https://github.com/Andry-iu/NestingOpt.git", "https://github.com/Andry-iu/NestingOpt"}
	for _, u := range ok {
		_, name, err := validateCloneInput(u, "")
		if err != nil || name != "NestingOpt" {
			t.Fatalf("%v: %v %s", u, err, name)
		}
	}
	bad := []string{"http://github.com/Andry-iu/NestingOpt", "https://evil.com/Andry-iu/NestingOpt", "https://github.com/other/NestingOpt", "https://user:pass@github.com/Andry-iu/NestingOpt", "https://github.com/Andry-iu/NestingOpt?x=y", "https://github.com/Andry-iu/../../else"}
	for _, u := range bad {
		if _, _, err := validateCloneInput(u, ""); err == nil {
			t.Errorf("accepted %s", u)
		}
	}
	if _, _, err := validateCloneInput(ok[0], "../escape"); err == nil {
		t.Fatal("accepted escaping project")
	}
}
func TestCloneRejectsExistingTarget(t *testing.T) {
	root := t.TempDir()
	t.Setenv("JARVIS_PROJECTS_ROOT", root)
	if err := os.Mkdir(filepath.Join(root, "NestingOpt"), 0700); err != nil {
		t.Fatal(err)
	}
	a := &Agent{}
	if _, err := a.cloneProject("https://github.com/Andry-iu/NestingOpt.git", ""); err == nil {
		t.Fatal("should reject existing target")
	}
}
func TestCloneFailsSafeWithoutGitAuth(t *testing.T) {
	root := t.TempDir()
	t.Setenv("JARVIS_PROJECTS_ROOT", root)
	// Force git lookup failure through empty PATH and verify staging cleanup.
	t.Setenv("PATH", "")
	a := &Agent{}
	_, err := a.cloneProject("https://github.com/Andry-iu/NestingOpt.git", "")
	if err != nil {
		t.Fatal(err)
	}
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		a.mu.Lock()
		status := a.task.Status
		a.mu.Unlock()
		if status != "running" {
			if status != "failed" {
				t.Fatalf("status %s", status)
			}
			break
		}
		time.Sleep(20 * time.Millisecond)
	}
	entries, err := os.ReadDir(root)
	if err != nil || len(entries) != 0 {
		t.Fatalf("staging not cleaned %v %v", entries, err)
	}
}
