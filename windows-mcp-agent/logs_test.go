package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestLogReadAndTraversal(t *testing.T) {
	root := t.TempDir()
	t.Setenv("JARVIS_HOME", root)
	dir := filepath.Join(root, "Logs", "diagnostics")
	if e := os.MkdirAll(dir, 0700); e != nil {
		t.Fatal(e)
	}
	if e := os.WriteFile(filepath.Join(dir, "test.log"), []byte("abc\nxyz\n"), 0600); e != nil {
		t.Fatal(e)
	}
	v, e := logRead(logReadRequest{Path: filepath.Join("diagnostics", "test.log"), TailBytes: 4})
	if e != nil {
		t.Fatal(e)
	}
	if v.(map[string]any)["content"] != "xyz\n" {
		t.Fatal(v)
	}
	for _, bad := range []string{"../secrets.env", "/tmp/test.log", "diagnostics/../../secret.log", "diagnostics/test.txt"} {
		if _, e = logRead(logReadRequest{Path: bad}); e == nil {
			t.Fatalf("accepted %q", bad)
		}
	}
	if e := os.Symlink(filepath.Join(dir, "test.log"), filepath.Join(dir, "link.log")); e == nil {
		if _, e = logRead(logReadRequest{Path: filepath.Join("diagnostics", "link.log")}); e == nil {
			t.Fatal("symlink accepted")
		}
	}
}
func TestLogCleanup(t *testing.T) {
	root := t.TempDir()
	t.Setenv("JARVIS_HOME", root)
	t.Setenv("JARVIS_LOG_RETENTION_DAYS", "30")
	dir := filepath.Join(root, "Logs", "diagnostics")
	if e := os.MkdirAll(dir, 0700); e != nil {
		t.Fatal(e)
	}
	now := time.Now()
	for _, name := range []string{"old.log", "recent.log", "old.txt"} {
		if e := os.WriteFile(filepath.Join(dir, name), []byte("x"), 0600); e != nil {
			t.Fatal(e)
		}
	}
	for _, name := range []string{"old.log", "old.txt"} {
		if e := os.Chtimes(filepath.Join(dir, name), now.Add(-32*24*time.Hour), now.Add(-32*24*time.Hour)); e != nil {
			t.Fatal(e)
		}
	}
	n, e := cleanupOldLogs(now)
	if e != nil || n != 1 {
		t.Fatalf("removed %d: %v", n, e)
	}
	if _, e = os.Stat(filepath.Join(dir, "old.log")); !os.IsNotExist(e) {
		t.Fatal("old log remains")
	}
	if _, e = os.Stat(filepath.Join(dir, "recent.log")); e != nil {
		t.Fatal(e)
	}
	if _, e = os.Stat(filepath.Join(dir, "old.txt")); e != nil {
		t.Fatal(e)
	}
	v, e := logList()
	if e != nil {
		t.Fatal(e)
	}
	if !strings.Contains(v.(map[string]any)["logs"].([]map[string]any)[0]["path"].(string), "recent.log") {
		t.Fatal(v)
	}
}