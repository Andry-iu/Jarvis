package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestLoadLocalConfig(t *testing.T) {
	dir := t.TempDir()
	if err := os.Mkdir(filepath.Join(dir, "Config"), 0700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "Config", "agent.env"), []byte("# note\nJARVIS_TEST_CONFIG_051=normal\n"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "Config", "secrets.env"), []byte("JARVIS_TEST_SECRET_051=keep-me-local\n"), 0600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("JARVIS_HOME", dir)
	if err := loadLocalConfig(); err != nil {
		t.Fatal(err)
	}
	if os.Getenv("JARVIS_TEST_CONFIG_051") != "normal" || os.Getenv("JARVIS_TEST_SECRET_051") != "keep-me-local" {
		t.Fatal("failed to load config")
	}
}
func TestMissingSecrets(t *testing.T) {
	dir := t.TempDir()
	t.Setenv("JARVIS_HOME", dir)
	if err := loadLocalConfig(); err == nil {
		t.Fatal("missing secrets must fail")
	}
}
func TestInvalidConfig(t *testing.T) {
	p := filepath.Join(t.TempDir(), "config.env")
	_ = os.WriteFile(p, []byte("invalid line\n"), 0600)
	if err := readEnvFile(p, true); err == nil || !strings.Contains(err.Error(), "line 1") {
		t.Fatalf("unexpected %v", err)
	}
}
func TestExistingEnvironmentWins(t *testing.T) {
	t.Setenv("JARVIS_TEST_PRIORITY_051", "existing")
	p := filepath.Join(t.TempDir(), "config.env")
	_ = os.WriteFile(p, []byte("JARVIS_TEST_PRIORITY_051=file\n"), 0600)
	if err := readEnvFile(p, true); err != nil {
		t.Fatal(err)
	}
	if os.Getenv("JARVIS_TEST_PRIORITY_051") != "existing" {
		t.Fatal("env overwritten")
	}
}