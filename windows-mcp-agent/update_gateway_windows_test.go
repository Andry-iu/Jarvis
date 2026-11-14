//go:build windows

package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestGatewayUpdateSurvivesLostAcknowledgement(t *testing.T) {
	target, _, peer := testGateway(t)
	middle, _, gateway := testGateway(t)
	entry, _ := fixtureRuntime(t)
	saveTestPeers(t, middle, []peerDevice{peer})
	gateway.ID = "middle"
	saveTestPeers(t, entry, []peerDevice{gateway, {ID: "target", MachineID: target.MachineID, Via: "middle"}})
	// updateBase is per-process; all fixture homes are private test directories.
	id := newID()
	body := "REM JARVIS-UPDATE-ID: " + id + "\r\n@echo off\r\necho proof>> \"" + filepath.Join(entry.home, "exactly-once.txt") + "\"\r\n"
	call := runtimeRequest{DeviceID: "target", Call: testCall("update_prepare", map[string]any{"update_id": id, "sha256": digest([]byte(body)), "content": body})}
	result, e := entry.deviceCall(call)
	if e != nil || toolFailed(result) {
		t.Fatal("prepare through two gateways", e, result)
	}
	call.Call = testCall("update_execute", map[string]any{"update_id": id, "sha256": digest([]byte(body))})
	result, e = entry.deviceCall(call)
	if e != nil || toolFailed(result) {
		t.Fatal("execute through two gateways", e, result)
	}
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		b, _ := os.ReadFile(filepath.Join(entry.home, "exactly-once.txt"))
		if strings.Count(string(b), "proof") == 1 {
			break
		}
		time.Sleep(25 * time.Millisecond)
	}
	// Repeat the same command (simulated lost response): receipt, never a new batch.
	if _, e = entry.deviceCall(call); e != nil {
		t.Fatal(e)
	}
	time.Sleep(100 * time.Millisecond)
	b, e := os.ReadFile(filepath.Join(entry.home, "exactly-once.txt"))
	if e != nil || strings.Count(string(b), "proof") != 1 {
		t.Fatal("update replayed", e, string(b))
	}
	call.Call = testCall("update_status", map[string]any{"update_id": id})
	result, e = entry.deviceCall(call)
	if e != nil || toolFailed(result) {
		t.Fatal("durable update status", e, result)
	}
	raw, _ := json.Marshal(result)
	if !strings.Contains(string(raw), id) {
		t.Fatal("missing update ID")
	}
	// Reconstructed state must still prevent execution after agent restart.
	updateState.Lock()
	updateState.Pending = nil
	delete(updateState.Used, id)
	updateState.Unlock()
	if _, e = executeUpdate(id, digest([]byte(body))); e != nil {
		t.Fatal("persisted receipt unavailable", e)
	}
}
