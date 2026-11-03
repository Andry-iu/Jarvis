package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sync"
	"testing"
)

func TestToolUsagePersistentConcurrent(t *testing.T) {
	t.Setenv("JARVIS_HOME", t.TempDir())
	a := &Agent{}
	var wg sync.WaitGroup
	for i := 0; i < 30; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			r := a.invoke("agent_status", json.RawMessage("{}"))
			if toolFailed(r) {
				t.Error("status failed")
			}
		}()
	}
	wg.Wait()
	a.invoke("project_status", json.RawMessage("{"))
	s, e := a.usageStore()
	if e != nil {
		t.Fatal(e)
	}
	if s.state.Tools["agent_status"].Total != 30 || s.state.Tools["agent_status"].Succeeded != 30 {
		t.Fatal(s.state)
	}
	if s.state.Tools["project_status"].Failed != 1 {
		t.Fatal("failure missing")
	}
	b := &Agent{}
	other, e := b.usageStore()
	if e != nil {
		t.Fatal(e)
	}
	if other.state.Tools["agent_status"].Total != 30 {
		t.Fatal("restart lost counters")
	}
	raw, e := os.ReadFile(filepath.Join(os.Getenv("JARVIS_HOME"), "State", "tool-usage.json"))
	if e != nil {
		t.Fatal(e)
	}
	var v map[string]any
	if json.Unmarshal(raw, &v) != nil {
		t.Fatal("invalid json")
	}
}
func TestToolUsageAliasNoDoubleCount(t *testing.T) {
	t.Setenv("JARVIS_HOME", t.TempDir())
	a := &Agent{}
	// Wrong identity intentionally fails before any operation; it must still count once.
	a.invoke("task_start", json.RawMessage(`{"operation":"runtime","request":{"operation":"file_read","machine_id":"wrong","project":".","path":"missing"}}`))
	s, e := a.usageStore()
	if e != nil {
		t.Fatal(e)
	}
	if s.state.Tools["file_read"].Total != 1 || s.state.Tools["file_read"].Failed != 1 || s.state.Tools["task_start"].Total != 0 {
		t.Fatal(s.state)
	}
}
func TestToolUsageCorruptPreserved(t *testing.T) {
	home := t.TempDir()
	t.Setenv("JARVIS_HOME", home)
	d := filepath.Join(home, "State")
	os.MkdirAll(d, 0700)
	p := filepath.Join(d, "tool-usage.json")
	os.WriteFile(p, []byte("invalid"), 0600)
	a := &Agent{}
	if _, e := a.usageStore(); e == nil {
		t.Fatal("corruption accepted")
	}
	a.invoke("agent_status", json.RawMessage("{}"))
	raw, _ := os.ReadFile(p)
	if string(raw) != "invalid" {
		t.Fatal("corruption reset")
	}
}
func TestToolUsageInterrupted(t *testing.T) {
	t.Setenv("JARVIS_HOME", t.TempDir())
	a := &Agent{}
	s, e := a.usageStore()
	if e != nil {
		t.Fatal(e)
	}
	if e = s.begin("codex_turn"); e != nil {
		t.Fatal(e)
	}
	b := &Agent{}
	v, e := b.toolUsage("codex_turn")
	if e != nil {
		t.Fatal(e)
	}
	raw, _ := json.Marshal(v)
	var r struct {
		Tools []struct{ Total, Unfinished uint64 }
	}
	json.Unmarshal(raw, &r)
	if len(r.Tools) != 1 || r.Tools[0].Total != 1 || r.Tools[0].Unfinished != 1 {
		t.Fatal(string(raw))
	}
}
