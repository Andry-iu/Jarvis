package main

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"unicode/utf16"
)

func sourceUpdateFixture(t *testing.T) (*Agent, *Runtime, *int) {
	t.Helper()
	r, _ := fixtureRuntime(t)
	launches := new(int)
	a := &Agent{runtime: r, testPrivileges: func() agentPrivileges { yes := true; return agentPrivileges{Elevated: &yes} }, testLaunchSourceUpdate: func(batch string) error { *launches++; return nil }}
	return a, r, launches
}
func sourceUpdateRequest(r *Runtime) runtimeRequest {
	return runtimeRequest{Operation: "agent_update", MachineID: r.MachineID, RequestID: newID(), SourceCommit: strings.Repeat("a", 40), TargetVersion: version}
}
func invokeSource(a *Agent, p runtimeRequest) any {
	b, _ := json.Marshal(p)
	return a.invokeRuntime(p.Operation, b)
}
func TestSourceUpdateReceiptAndAdmission(t *testing.T) {
	a, r, n := sourceUpdateFixture(t)
	p := sourceUpdateRequest(r)
	if toolFailed(invokeSource(a, p)) || *n != 1 {
		t.Fatal("initial launch")
	}
	if toolFailed(invokeSource(a, p)) || *n != 1 {
		t.Fatal("replayed launch")
	}
	restarted := &Agent{runtime: r, testPrivileges: a.testPrivileges, testLaunchSourceUpdate: a.testLaunchSourceUpdate}
	if toolFailed(invokeSource(restarted, p)) || *n != 1 {
		t.Fatal("relaunch after restart")
	}
	changed := p
	changed.SourceCommit = strings.Repeat("b", 40)
	if !toolFailed(invokeSource(a, changed)) {
		t.Fatal("request ID rebound")
	}
	second := p
	second.RequestID = newID()
	if !toolFailed(invokeSource(a, second)) || *n != 1 {
		t.Fatal("overlapping update admitted")
	}
	status, e := a.sourceUpdateStatus(r, p.RequestID)
	if e != nil || status.(*sourceUpdateRecord).Phase != "dispatching" {
		t.Fatal("status missing", e)
	}
	statusPath, _ := sourceUpdatePath(r.home, p.RequestID)
	v := status.(*sourceUpdateRecord)
	v.Phase = "completed"
	v.CleanupVerified = true
	v.InstalledHash = strings.Repeat("0", 64)
	if e := atomicJSON(statusPath, v); e != nil {
		t.Fatal(e)
	}
	if _, e = a.sourceUpdateStatus(r, p.RequestID); e == nil {
		t.Fatal("completed accepted without live hash")
	}
}
func TestSourceUpdateRefusesInvalidOrBusy(t *testing.T) {
	a, r, n := sourceUpdateFixture(t)
	for _, commit := range []string{"main", "latest", strings.Repeat("a", 39), strings.Repeat("a", 40) + ";whoami"} {
		p := sourceUpdateRequest(r)
		p.SourceCommit = commit
		if !toolFailed(invokeSource(a, p)) {
			t.Fatal("unbound source admitted")
		}
	}
	p := sourceUpdateRequest(r)
	p.MachineID = newID()
	if !toolFailed(invokeSource(a, p)) {
		t.Fatal("wrong machine")
	}
	r.Jobs["busy"] = &Job{Status: "running"}
	p = sourceUpdateRequest(r)
	if !toolFailed(invokeSource(a, p)) {
		t.Fatal("busy update")
	}
	delete(r.Jobs, "busy")
	a.testPrivileges = func() agentPrivileges { no := false; return agentPrivileges{Elevated: &no} }
	if !toolFailed(invokeSource(a, p)) || *n != 0 {
		t.Fatal("non elevated update")
	}
}
func TestSourceUpdateBatchDoesNotInterpolateShell(t *testing.T) {
	p := runtimeRequest{RequestID: newID(), MachineID: newID(), SourceCommit: strings.Repeat("a", 40), TargetVersion: version}
	home := `C:\Test & percent%\O'Brian`
	script := filepath.Join(home, "runner.ps1")
	batch := sourceUpdateBatch(newID(), script, home, p, strings.Repeat("b", 64))
	marker := "-EncodedCommand "
	encoded := strings.Split(strings.Split(batch, marker)[1], "\r\n")[0]
	raw, e := base64.StdEncoding.DecodeString(encoded)
	if e != nil {
		t.Fatal(e)
	}
	units := make([]uint16, len(raw)/2)
	for i := range units {
		units[i] = binary.LittleEndian.Uint16(raw[i*2:])
	}
	decoded := string(utf16.Decode(units))
	if !strings.Contains(decoded, `O''Brian`) || !strings.Contains(decoded, p.SourceCommit) || strings.Contains(batch, home) {
		t.Fatal("unsafe shell interpolation")
	}
}
func sourceGateway(t *testing.T, a *Agent, r *Runtime) peerDevice {
	s := httptest.NewTLSServer(gatewayHandler(a, testPeerToken))
	t.Cleanup(s.Close)
	sum := sha256.Sum256(s.Certificate().Raw)
	t.Setenv("GATEWAY_TEST_TOKEN", testPeerToken)
	return peerDevice{ID: "target", MachineID: r.MachineID, Endpoint: s.URL + "/gateway", TokenEnv: "GATEWAY_TEST_TOKEN", CertificateSHA256: hex.EncodeToString(sum[:])}
}
func TestSameSourceUpdateCommandAtFourGatewayLevels(t *testing.T) {
	a, target, launches := sourceUpdateFixture(t)
	p := sourceUpdateRequest(target)
	if toolFailed(invokeSource(a, p)) {
		t.Fatal("direct command failed")
	}
	peer := sourceGateway(t, a, target)
	var previous *Runtime
	for depth := 1; depth <= 4; depth++ {
		r, _ := fixtureRuntime(t)
		peers := []peerDevice{peer}
		if previous != nil {
			peer.ID = "next"
			peers = []peerDevice{peer, {ID: "target", MachineID: target.MachineID, Via: "next"}}
		}
		saveTestPeers(t, r, peers)
		raw, _ := json.Marshal(p)
		result, e := r.deviceCall(runtimeRequest{Operation: "device_call", DeviceID: "target", Call: raw})
		if e != nil || toolFailed(result) || *launches != 1 {
			t.Fatalf("depth %d changed/replayed target command: %v %+v", depth, e, result)
		}
		raw, _ = json.Marshal(runtimeRequest{Operation: "agent_update_status", RequestID: p.RequestID, MachineID: target.MachineID})
		result, e = r.deviceCall(runtimeRequest{Operation: "device_call", DeviceID: "target", Call: raw})
		if e != nil || toolFailed(result) {
			t.Fatalf("depth %d status: %v %+v", depth, e, result)
		}
		// Only the destination writes update state; the intermediate hop is transport.
		if _, e := os.Stat(filepath.Join(r.home, "State", "source-updates")); !os.IsNotExist(e) {
			t.Fatal("gateway acquired target update state")
		}
		previous = r
		peer = sourceGateway(t, &Agent{runtime: r}, r)
	}
}
