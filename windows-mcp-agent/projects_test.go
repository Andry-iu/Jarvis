package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"testing"
)

func TestProjectsDisabled(t *testing.T) {
	t.Setenv("JARVIS_PROJECTS_ROOT", "")
	_, err := projectList()
	if err == nil {
		t.Fatal("expected disabled")
	}
}
func TestProjectStatus(t *testing.T) {
	root := t.TempDir()
	t.Setenv("JARVIS_PROJECTS_ROOT", root)
	repo := filepath.Join(root, "testrepo")
	if err := os.Mkdir(repo, 0700); err != nil {
		t.Fatal(err)
	}
	for _, args := range [][]string{{"init", "-q"}, {"config", "user.email", "test@example.org"}, {"config", "user.name", "Test"}, {"commit", "--allow-empty", "-qm", "initial"}} {
		c := exec.Command("git", args...)
		c.Dir = repo
		if out, err := c.CombinedOutput(); err != nil {
			t.Fatalf("git: %v %s", err, out)
		}
	}
	_, err := projectStatus("testrepo")
	if err != nil {
		t.Fatal(err)
	}
	_, err = resolveProject("../outside")
	if err == nil {
		t.Fatal("path escape accepted")
	}
	_, err = projectList()
	if err != nil {
		t.Fatal(err)
	}
}
