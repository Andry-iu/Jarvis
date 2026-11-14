package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

const testPeerToken = "fixture-only-gateway-credential-1234567890123456"

func testGateway(t *testing.T) (*Runtime, *httptest.Server, peerDevice) {
	t.Helper()
	r, _ := fixtureRuntime(t)
	a := &Agent{runtime: r}
	s := httptest.NewTLSServer(gatewayHandler(a, testPeerToken))
	t.Cleanup(s.Close)
	sum := sha256.Sum256(s.Certificate().Raw)
	t.Setenv("GATEWAY_TEST_TOKEN", testPeerToken)
	return r, s, peerDevice{ID: "target", MachineID: r.MachineID, Endpoint: s.URL + "/gateway", TokenEnv: "GATEWAY_TEST_TOKEN", CertificateSHA256: hex.EncodeToString(sum[:])}
}
func saveTestPeers(t *testing.T, r *Runtime, peers []peerDevice) {
	t.Helper()
	os.MkdirAll(filepath.Join(r.home, "Config"), 0700)
	if e := atomicJSON(deviceConfigPath(r.home), peers); e != nil {
		t.Fatal(e)
	}
}
func testCall(op string, values map[string]any) json.RawMessage {
	if values == nil {
		values = map[string]any{}
	}
	values["operation"] = op
	b, _ := json.Marshal(values)
	return b
}
func TestGatewayTwoHopsAndRetirement(t *testing.T) {
	target, _, pTarget := testGateway(t)
	middle, _, pMiddle := testGateway(t)
	entry, _ := fixtureRuntime(t)
	saveTestPeers(t, middle, []peerDevice{pTarget})
	pMiddle.ID = "middle"
	via := peerDevice{ID: "target", Name: "Banco con nome libero", Kind: "temporary", MachineID: target.MachineID, Via: "middle"}
	saveTestPeers(t, entry, []peerDevice{pMiddle, via})
	call := runtimeRequest{Operation: "device_call", DeviceID: "target", Call: testCall("machine_info", nil)}
	result, e := entry.deviceCall(call)
	if e != nil || toolFailed(result) {
		t.Fatalf("two hops: %v %+v", e, result)
	}
	data, _ := json.Marshal(result)
	if !bytes.Contains(data, []byte(target.MachineID)) {
		t.Fatal("wrong final machine")
	}
	// A legacy public tool also traverses both gateways, without runtime decoding.
	call.Call = testCall("agent_status", nil)
	result, e = entry.deviceCall(call)
	if e != nil || toolFailed(result) {
		t.Fatalf("legacy forwarding: %v %+v", e, result)
	}
	// A durable target receipt remains readable on the same route.
	request := newID()
	p := runtimeRequest{Operation: "directory_create", MachineID: target.MachineID, Project: "sample", Path: "gateway-proof", RequestID: request}
	call.Call, _ = json.Marshal(p)
	result, e = entry.deviceCall(call)
	if e != nil || toolFailed(result) {
		t.Fatalf("target mutation: %v %+v", e, result)
	}
	call.Call = testCall("request_status", map[string]any{"request_id": request})
	result, e = entry.deviceCall(call)
	if e != nil || toolFailed(result) {
		t.Fatal("receipt unavailable", e, result)
	}
	_, raw, _ := readPeers(entry.home)
	retired, e := entry.deviceCall(runtimeRequest{Operation: "device_retire", DeviceID: "middle", Expected: digest(raw), RequestID: newID()})
	if e != nil {
		t.Fatal(retired, e)
	}
	if _, e = entry.deviceCall(call); e == nil {
		t.Fatal("retired gateway used")
	}
	// Retired records stay on disk; a new runtime uses the same configuration.
	peers, _, e := readPeers(entry.home)
	if e != nil || peers[0].Status != "retired" {
		t.Fatal("retirement not durable", e)
	}
}
func TestGatewayIdentityAndLoopGuards(t *testing.T) {
	target, _, peer := testGateway(t)
	entry, _ := fixtureRuntime(t)
	saveTestPeers(t, entry, []peerDevice{peer})
	call := runtimeRequest{Operation: "device_call", DeviceID: "target", Call: testCall("machine_info", map[string]any{"machine_id": newID()})}
	if _, e := entry.deviceCall(call); e == nil {
		t.Fatal("wrong target accepted")
	}
	call.Call = testCall("machine_info", nil)
	if _, e := entry.deviceCallTrace(call, []string{entry.MachineID}); e == nil {
		t.Fatal("cycle accepted")
	}
	if _, e := entry.deviceCallTrace(call, make([]string, maxGatewayHops)); e == nil {
		t.Fatal("hop limit ignored")
	}
	peer.CertificateSHA256 = strings.Repeat("0", 64)
	saveTestPeers(t, entry, []peerDevice{peer})
	if _, e := entry.deviceCall(call); e == nil {
		t.Fatal("wrong certificate accepted")
	}
	peer.CertificateSHA256 = ""
	saveTestPeers(t, entry, []peerDevice{peer})
	if _, e := entry.deviceCall(call); e == nil {
		t.Fatal("untrusted self-signed certificate accepted")
	}
	_ = target
}
func TestGatewayHTTPAuthenticationAndTarget(t *testing.T) {
	r, s, p := testGateway(t)
	req, _ := http.NewRequest("POST", s.URL+"/gateway", strings.NewReader(`{}`))
	req.Header.Set("Content-Type", "application/json")
	res, e := s.Client().Do(req)
	if e != nil {
		t.Fatal(e)
	}
	res.Body.Close()
	if res.StatusCode != 401 {
		t.Fatal("missing authentication accepted")
	}
	p.MachineID = newID()
	v, e := peerRPC(p, "agent_status", map[string]any{})
	if e != nil || !toolFailed(v) {
		t.Fatal("wrong envelope target accepted", e, v)
	}
	p.MachineID = r.MachineID
	v, e = peerRPCTrace(p, "agent_status", map[string]any{}, []string{r.MachineID})
	if e != nil || !toolFailed(v) {
		t.Fatal("transport cycle accepted", e, v)
	}
}
func TestGatewayConfigurationCASAndIdentityReuse(t *testing.T) {
	_, _, peer := testGateway(t)
	r, _ := fixtureRuntime(t)
	configure := runtimeRequest{Operation: "device_configure", Device: &peer, Expected: "missing", RequestID: newID()}
	if _, e := r.deviceCall(configure); e != nil {
		t.Fatal(e)
	}
	if _, e := r.deviceCall(configure); e != nil {
		t.Fatal("idempotent config receipt", e)
	}
	configure.RequestID = newID()
	if _, e := r.deviceCall(configure); e == nil {
		t.Fatal("stale configuration overwritten")
	}
	_, raw, _ := readPeers(r.home)
	configure.Expected = digest(raw)
	peer.MachineID = newID()
	if _, e := r.deviceCall(configure); e == nil {
		t.Fatal("identity reused")
	}
	if e := validatePeers([]peerDevice{{ID: "a", MachineID: newID(), Via: "b"}, {ID: "b", MachineID: newID(), Via: "a"}}); e == nil {
		t.Fatal("configuration cycle accepted")
	}
}
func TestGatewayRoutedFinalIdentityMismatch(t *testing.T) {
	target, _, targetPeer := testGateway(t)
	middle, _, middlePeer := testGateway(t)
	entry, _ := fixtureRuntime(t)
	saveTestPeers(t, middle, []peerDevice{targetPeer})
	middlePeer.ID = "middle"
	saveTestPeers(t, entry, []peerDevice{middlePeer, {ID: "target", MachineID: newID(), Via: "middle"}})
	v, e := entry.deviceCall(runtimeRequest{DeviceID: "target", Call: testCall("agent_status", nil)})
	if e == nil && !toolFailed(v) {
		t.Fatal("downstream identity change accepted")
	}
	_ = target
}
func TestGatewayPrivateBinding(t *testing.T) {
	for _, v := range []string{"0.0.0.0:8766", "[::]:8766", "8.8.8.8:8766", "example.com:8766"} {
		if privateListen(v) == nil {
			t.Fatal("unsafe bind accepted", v)
		}
	}
	for _, v := range []string{"192.168.50.2:8766", "127.0.0.1:8766", "[fd00::1]:8766"} {
		if e := privateListen(v); e != nil {
			t.Fatal(v, e)
		}
	}
}

func TestGatewayDiscoveryAndPairing(t *testing.T) {
	target, s, peer := testGateway(t)
	entry, _ := fixtureRuntime(t)
	response, e := s.Client().Get(s.URL + "/gateway/identity")
	if e != nil {
		t.Fatal(e)
	}
	defer response.Body.Close()
	var identity map[string]any
	if json.NewDecoder(response.Body).Decode(&identity) != nil || identity["machine_id"] != target.MachineID || identity["token"] != nil {
		t.Fatal("identity disclosure/response", identity)
	}
	inbox := filepath.Join(entry.home, "Config", "enrollments")
	os.MkdirAll(inbox, 0700)
	enrollment := map[string]any{"name": "Desktop Quattro", "machine_id": peer.MachineID, "endpoint": peer.Endpoint, "certificate_sha256": peer.CertificateSHA256, "token": testPeerToken}
	if e = atomicJSON(filepath.Join(inbox, "target.json"), enrollment); e != nil {
		t.Fatal(e)
	}
	p := runtimeRequest{Operation: "device_pair", Path: "target.json", DeviceID: "d4", Expected: "missing", RequestID: newID()}
	result, e := entry.deviceCall(p)
	if e != nil {
		t.Fatal(e)
	}
	raw, _ := json.Marshal(result)
	if bytes.Contains(raw, []byte(testPeerToken)) {
		t.Fatal("credential leaked in pairing response")
	}
	if _, e = entry.deviceCall(p); e != nil {
		t.Fatal("pairing idempotence", e)
	}
	result, e = entry.deviceCall(runtimeRequest{DeviceID: "d4", Call: testCall("machine_info", nil)})
	if e != nil || toolFailed(result) {
		t.Fatal("paired credential not immediately usable", e, result)
	}
	_, b, _ := readPeers(entry.home)
	duplicate := peer
	duplicate.ID = "different"
	duplicate.MachineID = newID()
	duplicate.Name = "desktop quattro"
	if _, e = entry.deviceCall(runtimeRequest{Operation: "device_configure", Device: &duplicate, Expected: digest(b), RequestID: newID()}); e == nil {
		t.Fatal("duplicate active name accepted")
	}
	if _, e = discoveryIPs("8.8.8.0/24"); e == nil {
		t.Fatal("public discovery accepted")
	}
	if _, e = discoveryIPs("192.168.0.0/16"); e == nil {
		t.Fatal("unbounded discovery accepted")
	}
}
