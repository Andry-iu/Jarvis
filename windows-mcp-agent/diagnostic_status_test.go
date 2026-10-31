package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestDiagnosticStatus(t *testing.T) {
	root := t.TempDir()
	t.Setenv("JARVIS_HOME", root)
	id := "11111111111111111111111111111111"
	dir := filepath.Join(root, "Logs", "diagnostics")
	if err := os.MkdirAll(dir, 0700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, id+".status"), []byte("PHASE=testing\nTEST=failed\nEXIT_CODE=13\nSECRET=forbidden\nGO=x;rm\nDONE=1\n"), 0600); err != nil {
		t.Fatal(err)
	}
	v, err := diagnosticStatus(id)
	if err != nil {
		t.Fatal(err)
	}
	m := v.(map[string]any)
	if m["phase"] != "testing" || m["test"] != "failed" || m["exit_code"] != "13" || m["done"] != "1" {
		t.Fatal(m)
	}
	if m["secret"] != nil || m["go"] != nil {
		t.Fatal(m)
	}
	if _, err := diagnosticStatus("../../evil"); err == nil {
		t.Fatal("traversal accepted")
	}
}