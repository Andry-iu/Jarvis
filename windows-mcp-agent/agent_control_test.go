package main

import (
	"encoding/json"
	"errors"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func controlFixture(t *testing.T) *Agent {
	t.Helper()
	home := t.TempDir()
	t.Setenv("JARVIS_HOME", home)
	t.Setenv("JARVIS_STATE_DIR", filepath.Join(home, "State", "runtime-v1"))
	t.Setenv("JARVIS_PROJECTS_ROOT", filepath.Join(home, "Projects"))
	os.MkdirAll(filepath.Join(home, "Projects"), 0700)
	a := &Agent{listenAddress: "127.0.0.1:8765"}
	t.Cleanup(a.closeRuntime)
	return a
}
func privilege(v bool) func() agentPrivileges {
	return func() agentPrivileges {
		return agentPrivileges{Elevated: &v, PID: os.Getpid(), CheckedAt: utcNow(), Source: "test"}
	}
}
func TestAdminRestartAlreadyElevatedNoLaunch(t *testing.T) {
	a := controlFixture(t)
	a.testPrivileges = privilege(true)
	a.testLaunchRestart = func(*adminRestart) error { t.Error("unexpected launch"); return nil }
	r, e := a.getRuntime()
	if e != nil {
		t.Fatal(e)
	}
	p := runtimeRequest{Operation: "agent_restart_admin", MachineID: r.MachineID, RequestID: "already-admin-001"}
	v, e := a.restartAdmin(r, p)
	if e != nil {
		t.Fatal(e)
	}
	b, _ := json.Marshal(v)
	if !strings.Contains(string(b), "already_elevated") || a.restartPending {
		t.Fatal(string(b))
	}
	if _, e = a.restartAdmin(r, p); e != nil {
		t.Fatal("idempotent receipt", e)
	}
}
func TestAdminRestartUnknownAndActiveRefused(t *testing.T) {
	a := controlFixture(t)
	r, e := a.getRuntime()
	if e != nil {
		t.Fatal(e)
	}
	p := runtimeRequest{Operation: "agent_restart_admin", MachineID: r.MachineID, RequestID: "refuse-busy-0001"}
	a.testPrivileges = func() agentPrivileges { return agentPrivileges{Error: "unknown"} }
	if _, e = a.restartAdmin(r, p); e == nil {
		t.Fatal("unknown allowed")
	}
	a.testPrivileges = privilege(false)
	a.task = &Task{Status: "running"}
	if _, e = a.restartAdmin(r, p); e == nil {
		t.Fatal("active diagnostic allowed")
	}
	a.task = nil
	r.mu.Lock()
	r.queue.Entries = append(r.queue.Entries, &queueEntry{Status: "uncertain"})
	r.mu.Unlock()
	if _, e = a.restartAdmin(r, p); e == nil {
		t.Fatal("uncertain work allowed")
	}
	r.mu.Lock()
	r.queue.Entries = nil
	r.mu.Unlock()
}
func TestAdminRestartCancellationPreservesAndUnlocks(t *testing.T) {
	a := controlFixture(t)
	a.testPrivileges = privilege(false)
	a.testLaunchRestart = func(*adminRestart) error { return errElevationCancelled }
	r, e := a.getRuntime()
	if e != nil {
		t.Fatal(e)
	}
	p := runtimeRequest{Operation: "agent_restart_admin", MachineID: r.MachineID, RequestID: "cancel-admin-0001"}
	if _, e = a.restartAdmin(r, p); e != nil {
		t.Fatal(e)
	}
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		a.controlMu.RLock()
		pending := a.restartPending
		a.controlMu.RUnlock()
		if !pending {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	a.controlMu.RLock()
	pending := a.restartPending
	a.controlMu.RUnlock()
	if pending {
		t.Fatal("mutations stayed blocked")
	}
	x, e := readRestart(r.dir, p.RequestID)
	if e != nil || x.Phase != "cancelled" {
		t.Fatal(x, e)
	}
	pub, _ := json.Marshal(x.public())
	for _, secret := range []string{x.Nonce, x.SID, x.ConfigHash} {
		if secret != "" && strings.Contains(string(pub), secret) {
			t.Fatal("private restart data leaked")
		}
	}
	if _, e = a.restartAdmin(r, p); e != nil {
		t.Fatal("replay must return acknowledgement", e)
	}
}
func TestAdminRestartGateAndHandoffAuthentication(t *testing.T) {
	a := controlFixture(t)
	a.controlMu.Lock()
	a.restartPending = true
	a.controlMu.Unlock()
	if release, e := a.enterControlGate("file_write", json.RawMessage(`{}`)); e == nil {
		release()
		t.Fatal("mutation allowed")
	}
	release, e := a.enterControlGate("agent_status", json.RawMessage(`{}`))
	if e != nil {
		t.Fatal(e)
	}
	release()
	req := httptest.NewRequest("POST", "/internal/agent-restart", strings.NewReader(`{}`))
	req.RemoteAddr = "203.0.113.1:4567"
	out := httptest.NewRecorder()
	a.restartHandoff(out, req, strings.Repeat("x", 32))
	if out.Code != 403 {
		t.Fatal("remote handoff allowed")
	}
	req = httptest.NewRequest("POST", "/internal/agent-restart", strings.NewReader(`{}`))
	req.RemoteAddr = "127.0.0.1:4567"
	req.Header.Set("Authorization", "Bearer wrong")
	out = httptest.NewRecorder()
	a.restartHandoff(out, req, strings.Repeat("x", 32))
	if out.Code != 403 {
		t.Fatal("unauthenticated handoff allowed")
	}
}
func TestAdminRestartConfigurationFingerprint(t *testing.T) {
	old := restartConfigHash()
	t.Setenv("JARVIS_PROJECTS_ROOT", "different-root")
	if restartConfigHash() == old {
		t.Fatal("configuration change not detected")
	}
	if _, e := restartPath(t.TempDir(), "../escape"); e == nil {
		t.Fatal("invalid receipt accepted")
	}
	if !errors.Is(errElevationCancelled, errElevationCancelled) {
		t.Fatal("cancellation identity")
	}
}
