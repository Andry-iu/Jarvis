package main

import (
	"crypto/sha256"
	"encoding/hex"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestPinnedPeerKeepsConnectionAndRefreshesAuthorization(t *testing.T) {
	var connections atomic.Int32
	var expected atomic.Value
	expected.Store("first-fixture-token-12345678901234567890")
	server := httptest.NewUnstartedServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = io.Copy(io.Discard, r.Body)
		_ = r.Body.Close()
		if r.Header.Get("Authorization") != "Bearer "+expected.Load().(string) {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"content":[{"type":"text","text":"{}"}],"isError":false}`)
	}))
	server.Config.ConnState = func(_ net.Conn, state http.ConnState) {
		if state == http.StateNew {
			connections.Add(1)
		}
	}
	server.StartTLS()
	defer server.Close()

	pin := sha256.Sum256(server.Certificate().Raw)
	p := peerDevice{
		Endpoint: server.URL + "/gateway",
		MachineID: "test-peer-machine",
		TokenEnv: "JARVIS_TEST_PEER_BEARER",
		CertificateSHA256: hex.EncodeToString(pin[:]),
	}
	t.Setenv(p.TokenEnv, expected.Load().(string))
	for i := 0; i < 3; i++ {
		value, err := peerRPC(p, "machine_info", map[string]any{})
		if err != nil || value["isError"] == true {
			t.Fatalf("call %d failed: %v %v", i, err, value)
		}
	}
	if got := connections.Load(); got != 1 {
		t.Fatalf("three calls opened %d TLS connections, want one", got)
	}
	expected.Store("second-fixture-token-12345678901234567890")
	t.Setenv(p.TokenEnv, expected.Load().(string))
	if _, err := peerRPC(p, "machine_info", map[string]any{}); err != nil {
		t.Fatal("a fresh bearer must be sent on the reused TLS connection:", err)
	}
	if got := connections.Load(); got != 1 {
		t.Fatalf("credential rotation opened %d connections, want reused TLS", got)
	}
	incorrect := p
	incorrect.CertificateSHA256 = strings.Repeat("0", 64)
	if _, err := peerRPC(incorrect, "machine_info", map[string]any{}); err == nil {
		t.Fatal("changed certificate pin reused a trusted connection")
	}
}

func TestPinnedPeerIdentityCacheIsBoundedAndScoped(t *testing.T) {
	p := peerDevice{
		Endpoint: "https://identity-cache-fixture.invalid/gateway",
		MachineID: "fixture-machine",
		CertificateSHA256: strings.Repeat("a", 64),
	}
	if cachedPeerIdentity(p) {
		t.Fatal("identity cache granted trust before verification")
	}
	rememberPeerIdentity(p)
	if !cachedPeerIdentity(p) {
		t.Fatal("verified identity not cached")
	}
	otherMachine := p
	otherMachine.MachineID = "another-machine"
	if cachedPeerIdentity(otherMachine) {
		t.Fatal("identity reused across machines")
	}
	otherPin := p
	otherPin.CertificateSHA256 = strings.Repeat("b", 64)
	if cachedPeerIdentity(otherPin) {
		t.Fatal("identity reused across certificates")
	}
	otherEndpoint := p
	otherEndpoint.Endpoint = "https://other-endpoint.invalid/gateway"
	if cachedPeerIdentity(otherEndpoint) {
		t.Fatal("identity reused across endpoints")
	}
	unpinned := p
	unpinned.CertificateSHA256 = ""
	rememberPeerIdentity(unpinned)
	if cachedPeerIdentity(unpinned) {
		t.Fatal("unpinned HTTPS identity must be checked on each command")
	}
	key := peerIdentityKey{p.Endpoint, p.MachineID, p.CertificateSHA256}
	verifiedPeerIdentities.Lock()
	verifiedPeerIdentities.checked[key] = time.Now().Add(-299 * time.Second)
	verifiedPeerIdentities.Unlock()
	if !cachedPeerIdentity(p) {
		t.Fatal("verified identity expired before 300 seconds")
	}
	verifiedPeerIdentities.Lock()
	verifiedPeerIdentities.checked[key] = time.Now().Add(-300 * time.Second)
	verifiedPeerIdentities.Unlock()
	if cachedPeerIdentity(p) {
		t.Fatal("identity cache did not expire at 300 seconds")
	}
}
