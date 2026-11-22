package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestUpdateRetentionDefaultAndBounds(t *testing.T) {
	for _, raw := range []string{"", "invalid", "0", "-1", "366"} {
		t.Setenv("JARVIS_UPDATE_RETENTION_DAYS", raw)
		if got := updateRetentionDays(); got != 30 { t.Fatalf("%q: got %d", raw, got) }
	}
	t.Setenv("JARVIS_UPDATE_RETENTION_DAYS", "45")
	if updateRetentionDays() != 45 { t.Fatal("configured retention ignored") }
	if maintenanceInterval != 24*time.Hour { t.Fatal("maintenance interval changed") }
}

func TestBatchRetentionKeepsActivePendingRecentAndUnowned(t *testing.T) {
	t.Setenv("JARVIS_HOME", t.TempDir())
	t.Setenv("JARVIS_UPDATE_RETENTION_DAYS", "30")
	root, _ := updateBase()
	dir := filepath.Join(root, "Scripts", "updates")
	if err := os.MkdirAll(dir, 0700); err != nil { t.Fatal(err) }
	now := time.Now()
	ids := []string{strings.Repeat("a",32),strings.Repeat("b",32),strings.Repeat("c",32),strings.Repeat("d",32),strings.Repeat("e",32),strings.Repeat("f",32)}
	for i, id := range ids {
		text := "REM JARVIS-UPDATE-ID: "+id+"\r\n@echo off\r\n"
		if i == 5 { text = "unrelated user script\r\n" }
		p := filepath.Join(dir, id+".cmd")
		if err := os.WriteFile(p, []byte(text), 0600); err != nil { t.Fatal(err) }
		at := now.Add(-31*24*time.Hour)
		if i == 1 { at = now.Add(-29*24*time.Hour) }
		if i == 4 { at = now.Add(-30*24*time.Hour) }
		if err := os.Chtimes(p, at, at); err != nil { t.Fatal(err) }
	}
	updateState.Lock()
	oldPending := updateState.Pending
	updateState.Pending = &preparedUpdate{ID:ids[2]}
	updateState.Running[ids[3]] = true
	updateState.Unlock()
	t.Cleanup(func(){updateState.Lock();updateState.Pending=oldPending;delete(updateState.Running,ids[3]);updateState.Unlock()})
	n, err := cleanupOldUpdateBatches(now)
	if err != nil || n != 1 { t.Fatalf("removed=%d err=%v",n,err) }
	for i,id := range ids {
		_, err := os.Stat(filepath.Join(dir,id+".cmd"))
		if i == 0 && !os.IsNotExist(err) { t.Fatal("expired batch retained") }
		if i != 0 && err != nil { t.Fatalf("protected batch %d removed: %v",i,err) }
	}
}

func TestBatchRetentionDoesNotTraverseSymlinkParent(t *testing.T) {
	home := t.TempDir()
	out := t.TempDir()
	t.Setenv("JARVIS_HOME",home)
	if err := os.MkdirAll(filepath.Join(out,"updates"),0700);err!=nil{t.Fatal(err)}
	if err := os.Symlink(out,filepath.Join(home,"Scripts"));err!=nil{t.Skip("symlinks not available")}
	if _,err:=cleanupOldUpdateBatches(time.Now());err==nil{t.Fatal("linked update parent accepted")}
}
