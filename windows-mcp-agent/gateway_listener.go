package main

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net"
	"net/http"
	"net/netip"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

type peerListenerConfig struct {
	Listen      string `json:"listen"`
	Certificate string `json:"certificate"`
	PrivateKey  string `json:"private_key"`
	TokenFile   string `json:"token_file"`
}

func privateListen(address string) error {
	host, port, e := net.SplitHostPort(address)
	if e != nil || port == "0" || port == "" {
		return errors.New("explicit private listener IP and port required")
	}
	n, e := strconv.Atoi(port)
	if e != nil || n < 1024 || n > 65535 {
		return errors.New("invalid peer port")
	}
	if host == "auto" {
		return nil
	}
	ip, e := netip.ParseAddr(host)
	if e != nil || (!ip.IsPrivate() && !ip.IsLoopback()) || ip.IsUnspecified() {
		return errors.New("gateway listener requires an explicit private/loopback address")
	}
	return nil
}
func gatewayError(message string) any { return toolResult(map[string]any{"error": message}, true) }
func gatewayHandler(a *Agent, token string) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		if req.URL.Path != "/gateway" && req.URL.Path != "/gateway/identity" {
			http.NotFound(w, req)
			return
		}
		if req.TLS == nil || req.Header.Get("Origin") != "" {
			http.Error(w, "TLS/non-browser request required", 403)
			return
		}
		if req.URL.Path == "/gateway/identity" {
			if req.Method != "GET" {
				http.Error(w, "GET required", 405)
				return
			}
			r, e := a.getRuntime()
			if e != nil {
				http.Error(w, "unavailable", 503)
				return
			}
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode(map[string]any{"machine_id": r.MachineID, "name": r.deviceName(), "version": version})
			return
		}
		if len(token) < 32 || subtle.ConstantTimeCompare([]byte(req.Header.Get("Authorization")), []byte("Bearer "+token)) != 1 {
			http.Error(w, "unauthorized", 401)
			return
		}
		if req.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		if !strings.HasPrefix(strings.ToLower(req.Header.Get("Content-Type")), "application/json") {
			http.Error(w, "JSON required", 415)
			return
		}
		var p gatewayEnvelope
		d := json.NewDecoder(http.MaxBytesReader(w, req.Body, 8*1024*1024))
		d.DisallowUnknownFields()
		if d.Decode(&p) != nil || d.Decode(new(any)) != io.EOF {
			http.Error(w, "invalid gateway request", 400)
			return
		}
		r, e := a.getRuntime()
		if e != nil {
			http.Error(w, "runtime unavailable", 503)
			return
		}
		var result any
		switch {
		case p.Target != r.MachineID:
			result = gatewayError("gateway target identity mismatch; no dispatch")
		case len(p.Visited) >= maxGatewayHops:
			result = gatewayError("gateway hop limit reached")
		default:
			seen := map[string]bool{}
			invalid := false
			for _, id := range p.Visited {
				if !requestIDPattern.MatchString(id) || id == r.MachineID || seen[id] {
					invalid = true
				}
				seen[id] = true
			}
			if invalid {
				result = gatewayError("gateway cycle or invalid route refused")
			} else if p.Method == "device_call" {
				q, err := decodeRuntime("device_call", p.Arguments)
				if err != nil || q.MachineID != r.MachineID {
					result = gatewayError("invalid forwarded gateway operation")
				} else {
					release, err := a.enterControlGate("device_call", p.Arguments)
					if err != nil {
						result = gatewayError(err.Error())
					} else {
						v, err := r.deviceCallTrace(q, p.Visited)
						release()
						if err != nil {
							result = gatewayError(err.Error())
						} else {
							result = v
						}
					}
				}
			} else if !toolDeclared(p.Method) {
				result = gatewayError("unknown public tool")
			} else {
				// The peer listener grants the paired gateway the same operations as the
				// external entry, retaining each tool's identity/permission/idempotency gates.
				result = a.invoke(p.Method, p.Arguments)
			}
		}
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		_ = json.NewEncoder(w).Encode(result)
	})
}
func startPeerListener(a *Agent) (*http.Server, error) {
	home := os.Getenv("JARVIS_HOME")
	path := filepath.Join(home, "Config", "peer-listener.json")
	b, e := os.ReadFile(path)
	if os.IsNotExist(e) {
		return nil, nil
	}
	if e != nil {
		return nil, e
	}
	var c peerListenerConfig
	d := json.NewDecoder(strings.NewReader(string(b)))
	d.DisallowUnknownFields()
	if len(b) > 16384 || d.Decode(&c) != nil || d.Decode(new(any)) != io.EOF {
		return nil, errors.New("invalid peer listener configuration")
	}
	if e = privateListen(c.Listen); e != nil {
		return nil, e
	}
	for _, p := range []string{c.Certificate, c.PrivateKey, c.TokenFile} {
		if !filepath.IsAbs(p) {
			return nil, errors.New("peer key/token paths must be absolute")
		}
	}
	tokenBytes, e := os.ReadFile(c.TokenFile)
	if e != nil {
		return nil, errors.New("peer credential unavailable")
	}
	token := strings.TrimSpace(string(tokenBytes))
	if len(token) < 32 || token == os.Getenv("JARVIS_MCP_TOKEN") {
		return nil, errors.New("separate peer credential of at least 32 characters required")
	}
	cert, e := tls.LoadX509KeyPair(c.Certificate, c.PrivateKey)
	if e != nil {
		return nil, errors.New("invalid peer certificate/key")
	}
	server := &http.Server{Addr: c.Listen, Handler: gatewayHandler(a, token), ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 30 * time.Second, IdleTimeout: 30 * time.Second, TLSConfig: &tls.Config{MinVersion: tls.VersionTLS12, Certificates: []tls.Certificate{cert}}}
	listeners := map[string]net.Listener{}
	attach := func() error {
		desired := map[string]bool{}
		for _, address := range listenerAddresses(c.Listen) {
			desired[address] = true
			if listeners[address] != nil {
				continue
			}
			listener, err := net.Listen("tcp", address)
			if err != nil {
				return err
			}
			listeners[address] = listener
			go func() { _ = server.Serve(tls.NewListener(listener, server.TLSConfig)) }()
		}
		for address, listener := range listeners {
			if !desired[address] {
				_ = listener.Close()
				delete(listeners, address)
			}
		}
		return nil
	}
	if e = attach(); e != nil {
		_ = server.Close()
		return nil, e
	}
	stop := make(chan struct{})
	server.RegisterOnShutdown(func() { close(stop) })
	go func() {
		ticker := time.NewTicker(5 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-stop:
				return
			case <-ticker.C:
				_ = attach()
			}
		}
	}()
	return server, nil
}

// Initial provisioning only. Existing installations/configuration are never
// overwritten; credentials are retained on disk, never printed.
func initPeerListener(home, address string, r *Runtime) error {
	if e := privateListen(address); e != nil {
		return e
	}
	config := filepath.Join(home, "Config")
	if e := os.MkdirAll(config, 0700); e != nil {
		return e
	}
	if e := protectRuntimeDirectory(config); e != nil {
		return e
	}
	paths := []string{"peer-cert.pem", "peer-key.pem", "peer-token.txt", "peer-listener.json", "peer-enrollment.json"}
	for _, p := range paths {
		if _, e := os.Lstat(filepath.Join(config, p)); !os.IsNotExist(e) {
			return errors.New("peer provisioning target already exists or is unavailable")
		}
	}
	key, e := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if e != nil {
		return e
	}
	serial, e := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 120))
	if e != nil {
		return e
	}
	addresses := listenerAddresses(address)
	if len(addresses) == 0 {
		return errors.New("no private interface available")
	}
	host, _, _ := net.SplitHostPort(addresses[len(addresses)-1])
	now := time.Now()
	template := &x509.Certificate{SerialNumber: serial, Subject: pkix.Name{CommonName: "Jarvis " + r.MachineID}, NotBefore: now.Add(-5 * time.Minute), NotAfter: now.AddDate(2, 0, 0), IPAddresses: []net.IP{net.ParseIP(host)}, KeyUsage: x509.KeyUsageDigitalSignature, ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}}
	der, e := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if e != nil {
		return e
	}
	priv, e := x509.MarshalPKCS8PrivateKey(key)
	if e != nil {
		return e
	}
	token := newID() + newID()
	sum := sha256.Sum256(der)
	values := map[string][]byte{"peer-cert.pem": pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}), "peer-key.pem": pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: priv}), "peer-token.txt": []byte(token)}
	for name, b := range values {
		if e = os.WriteFile(filepath.Join(config, name), b, 0600); e != nil {
			return e
		}
	}
	c := peerListenerConfig{Listen: address, Certificate: filepath.Join(config, "peer-cert.pem"), PrivateKey: filepath.Join(config, "peer-key.pem"), TokenFile: filepath.Join(config, "peer-token.txt")}
	if e = atomicJSON(filepath.Join(config, "peer-listener.json"), c); e != nil {
		return e
	}
	// Private handoff file, imported locally by the operator on an authorized
	// gateway. Its bearer must never be put in chat, Git or a public package.
	enrollment := map[string]any{"machine_id": r.MachineID, "name": r.deviceName(), "endpoint": "https://" + net.JoinHostPort(host, func() string { _, p, _ := net.SplitHostPort(address); return p }()) + "/gateway", "certificate_sha256": hex.EncodeToString(sum[:]), "token": token}
	if e = atomicJSON(filepath.Join(config, "peer-enrollment.json"), enrollment); e != nil {
		return e
	}
	fmt.Println("Peer listener provisioned. Private enrollment file: " + filepath.Join(config, "peer-enrollment.json"))
	return nil
}
