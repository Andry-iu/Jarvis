package main

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func pairingFixture(t *testing.T, allow bool) (*Runtime, *Runtime, peerDevice, *atomic.Int32, chan struct{}) {
	t.Helper()
	target, _ := fixtureRuntime(t)
	count := &atomic.Int32{}
	release := make(chan struct{})
	t.Cleanup(func() {
		select {
		case <-release:
		default:
			close(release)
		}
	})
	s := httptest.NewTLSServer(gatewayHandlerWithConfirmation(&Agent{runtime: target}, testPeerToken, func(ctx context.Context, p pairPrompt) (bool, error) {
		count.Add(1)
		if !strings.Contains(p.Code, "-") || p.Address == "" {
			t.Error("missing local verification details")
		}
		select {
		case <-release:
			return allow, nil
		case <-ctx.Done():
			return false, ctx.Err()
		}
	}))
	t.Cleanup(s.Close)
	sum := sha256.Sum256(s.Certificate().Raw)
	entry, _ := fixtureRuntime(t)
	peer := peerDevice{ID: "softplctest", Name: "SoftPlcTest1", MachineID: target.MachineID, Endpoint: s.URL + "/gateway", CertificateSHA256: hex.EncodeToString(sum[:])}
	return entry, target, peer, count, release
}
func outgoing(t *testing.T, r *Runtime, id string) pairOutgoing {
	t.Helper()
	var x pairOutgoing
	if e := readPairJSON(filepath.Join(r.dir, "pair-outgoing", id+".json"), &x); e != nil {
		t.Fatal(e)
	}
	return x
}
func awaitPair(t *testing.T, r *Runtime, id, state string) any {
	t.Helper()
	for i := 0; i < 100; i++ {
		v, e := r.interactivePair(runtimeRequest{Operation: "device_pair_status", RequestID: id})
		if e != nil {
			t.Fatal(e)
		}
		if v.(map[string]any)["state"] == state {
			return v
		}
		time.Sleep(5 * time.Millisecond)
	}
	t.Fatalf("pairing did not reach %s", state)
	return nil
}
func TestInteractivePairRequiresLocalApprovalAndPersists(t *testing.T) {
	entry, target, peer, count, release := pairingFixture(t, true)
	id := newID()
	begin := runtimeRequest{Operation: "device_pair_begin", Device: &peer, Label: "Desktop1", RequestID: id}
	v, e := entry.interactivePair(begin)
	if e != nil {
		t.Fatal(e)
	}
	if v.(map[string]any)["state"] != "pending" {
		t.Fatal(v)
	}
	if _, e = entry.interactivePair(runtimeRequest{Operation: "device_pair_complete", PairingID: id, RequestID: newID(), Expected: "missing"}); e == nil {
		t.Fatal("paired before approval")
	}
	if _, e = entry.interactivePair(begin); e != nil {
		t.Fatal(e)
	}
	_, b, _ := readPeers(entry.home)
	if b != nil {
		t.Fatal("configuration written without approval")
	}
	close(release)
	awaitPair(t, entry, id, "approved")
	complete := runtimeRequest{Operation: "device_pair_complete", PairingID: id, RequestID: newID(), Expected: "missing"}
	v, e = entry.interactivePair(complete)
	if e != nil {
		t.Fatal(e)
	}
	raw, _ := json.Marshal(v)
	if bytes.Contains(raw, []byte(testPeerToken)) || bytes.Contains(raw, []byte("private_key")) {
		t.Fatal("secret exposed")
	}
	if _, e = entry.interactivePair(complete); e != nil {
		t.Fatal("idempotent completion", e)
	}
	if count.Load() != 1 {
		t.Fatal("replayed local approval", count.Load())
	}
	peers, _, e := readPeers(entry.home)
	if e != nil || len(peers) != 1 || peers[0].MachineID != target.MachineID {
		t.Fatal("pair not durable", e)
	}
	result, e := entry.deviceCall(runtimeRequest{Operation: "device_call", DeviceID: peer.ID, Call: testCall("machine_info", nil)})
	if e != nil || toolFailed(result) {
		t.Fatal("paired control failed", e)
	}
	x := outgoing(t, entry, id)
	if x.PrivateKey != "" || !x.Completed {
		t.Fatal("private handshake key retained")
	}
	entries, _ := os.ReadDir(filepath.Join(entry.home, "Config", "enrollments"))
	if len(entries) != 0 {
		t.Fatal("handoff file not removed")
	}
}
func TestInteractivePairDenialAndCAS(t *testing.T) {
	entry, _, peer, _, release := pairingFixture(t, false)
	id := newID()
	_, e := entry.interactivePair(runtimeRequest{Operation: "device_pair_begin", Device: &peer, RequestID: id})
	if e != nil {
		t.Fatal(e)
	}
	close(release)
	awaitPair(t, entry, id, "denied")
	if _, e = entry.interactivePair(runtimeRequest{Operation: "device_pair_complete", PairingID: id, RequestID: newID(), Expected: "missing"}); e == nil {
		t.Fatal("denial ignored")
	}
	peers, _, _ := readPeers(entry.home)
	if len(peers) != 0 {
		t.Fatal("denied pairing persisted")
	}
}
func TestInteractivePairPinnedIdentityReplayAndExpiry(t *testing.T) {
	entry, _, peer, _, release := pairingFixture(t, true)
	id := newID()
	begin := runtimeRequest{Operation: "device_pair_begin", Device: &peer, RequestID: id}
	_, e := entry.interactivePair(begin)
	if e != nil {
		t.Fatal(e)
	}
	x := outgoing(t, entry, id)
	bad := x
	bad.Transcript.Target = newID()
	if _, e = pairHTTP(bad, "status"); e == nil {
		t.Fatal("target changed")
	}
	bad = x
	bad.Transcript.Name = "Forged"
	if _, e = pairHTTP(bad, "status"); e == nil {
		t.Fatal("transcript changed")
	}
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	bad = x
	bad.PrivateKey = base64.StdEncoding.EncodeToString(x509.MarshalPKCS1PrivateKey(key))
	if _, e = pairHTTP(bad, "status"); e == nil {
		t.Fatal("polling proof not required")
	}
	bad = x
	bad.Transcript.Pin = strings.Repeat("0", 64)
	if _, e = pairHTTP(bad, "status"); e == nil {
		t.Fatal("certificate pin ignored")
	}
	peer.Name = "another"
	if _, e = entry.interactivePair(begin); e == nil {
		t.Fatal("request ID reused")
	}
	close(release)
	awaitPair(t, entry, id, "approved")
	saveTestPeers(t, entry, nil)
	if _, e = entry.interactivePair(runtimeRequest{Operation: "device_pair_complete", PairingID: id, RequestID: newID(), Expected: "missing"}); e == nil {
		t.Fatal("configuration CAS ignored")
	}
	peers, b, _ := readPeers(entry.home)
	if len(peers) != 0 {
		t.Fatal("configuration overwritten")
	}
	if _, e = entry.interactivePair(runtimeRequest{Operation: "device_pair_complete", PairingID: id, RequestID: newID(), Expected: digest(b)}); e != nil {
		t.Fatal("retry with reviewed CAS", e)
	}
	x = outgoing(t, entry, id)
	x.Completed = false
	x.Expires = time.Now().Add(-time.Second)
	_ = atomicJSON(filepath.Join(entry.dir, "pair-outgoing", id+".json"), x)
	v, e := entry.interactivePair(runtimeRequest{Operation: "device_pair_status", RequestID: id})
	if e != nil || v.(map[string]any)["state"] != "expired" {
		t.Fatal(v, e)
	}
}
func TestPairingCodeBindsEveryIdentityAndKey(t *testing.T) {
	t1 := pairTranscript{ID: newID(), Target: newID(), Requester: newID(), Name: "Desktop1", Pin: strings.Repeat("a", 64), PublicKey: "key"}
	for _, change := range []func(*pairTranscript){func(p *pairTranscript) { p.ID = newID() }, func(p *pairTranscript) { p.Target = newID() }, func(p *pairTranscript) { p.Requester = newID() }, func(p *pairTranscript) { p.Name = "other" }, func(p *pairTranscript) { p.Pin = strings.Repeat("b", 64) }, func(p *pairTranscript) { p.PublicKey = "other" }} {
		t2 := t1
		change(&t2)
		if pairCode(t1) == pairCode(t2) {
			t.Fatal("comparison code does not bind transcript")
		}
	}
}
