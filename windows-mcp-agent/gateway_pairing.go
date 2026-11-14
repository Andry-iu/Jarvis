package main

// First trust is a physical, local decision. The same transcript code is shown
// at both ends. RSA proof of possession binds polling to the initiating agent;
// OAEP seals the credential to it even if a response is intercepted. Names and
// discovery alone never grant control. There is no remotely callable approve.
import (
	"bytes"
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
	"unicode"
)

const pairingTTL = 3 * time.Minute

type pairTranscript struct {
	ID        string `json:"request_id"`
	Target    string `json:"target_machine_id"`
	Pin       string `json:"certificate_sha256"`
	Requester string `json:"requester_machine_id"`
	Name      string `json:"requester_name"`
	PublicKey string `json:"public_key"`
}
type pairWire struct {
	Transcript pairTranscript `json:"transcript"`
	Action     string         `json:"action"`
	Signature  string         `json:"signature"`
}
type pairReply struct {
	State   string    `json:"state"`
	Code    string    `json:"comparison_code"`
	Expires time.Time `json:"expires_at"`
	Sealed  string    `json:"sealed_credential,omitempty"`
}
type pairIncoming struct {
	Transcript pairTranscript `json:"transcript"`
	Reply      pairReply      `json:"reply"`
}
type pairOutgoing struct {
	Transcript pairTranscript `json:"transcript"`
	Device     peerDevice     `json:"device"`
	PrivateKey string         `json:"private_key,omitempty"`
	Expires    time.Time      `json:"expires_at"`
	Completed  bool           `json:"completed,omitempty"`
}
type pairPrompt struct {
	Name    string `json:"name"`
	Machine string `json:"machine_id"`
	Address string `json:"address"`
	Code    string `json:"code"`
}
type pairingServer struct {
	agent   *Agent
	token   string
	confirm func(context.Context, pairPrompt) (bool, error)
	mu      sync.Mutex
	last    time.Time
	active  string
}

func pairCode(t pairTranscript) string {
	b, _ := json.Marshal(t)
	s := strings.ToUpper(digest(b)[:12])
	return s[:4] + "-" + s[4:8] + "-" + s[8:]
}
func validPairName(s string) bool {
	return strings.TrimSpace(s) != "" && len(s) <= 160 && !strings.ContainsFunc(s, func(r rune) bool { return unicode.IsControl(r) || unicode.Is(unicode.Cf, r) })
}
func pairKey(t pairTranscript) (*rsa.PublicKey, error) {
	b, e := base64.StdEncoding.DecodeString(t.PublicKey)
	if e != nil || len(b) > 1024 {
		return nil, errors.New("invalid pairing key")
	}
	key, e := x509.ParsePKCS1PublicKey(b)
	if e != nil || key.N.BitLen() != 2048 || key.E != 65537 {
		return nil, errors.New("RSA2048 pairing key required")
	}
	return key, nil
}
func pairSignature(t pairTranscript, action string) []byte {
	b, _ := json.Marshal(struct {
		Transcript pairTranscript
		Action     string
	}{t, action})
	h := sha256.Sum256(b)
	return h[:]
}
func privatePairDir(r *Runtime, name string) (string, error) {
	dir := filepath.Join(r.dir, name)
	if e := os.MkdirAll(dir, 0700); e != nil {
		return "", e
	}
	if e := protectRuntimeDirectory(dir); e != nil {
		return "", e
	}
	return dir, nil
}
func readPairJSON(path string, v any) error {
	st, e := os.Lstat(path)
	if e != nil || !st.Mode().IsRegular() || isReparse(st) || st.Size() > 16384 {
		return errors.New("private pairing state unavailable")
	}
	b, e := os.ReadFile(path)
	if e != nil {
		return e
	}
	d := json.NewDecoder(bytes.NewReader(b))
	d.DisallowUnknownFields()
	if d.Decode(v) != nil || d.Decode(new(any)) != io.EOF {
		return errors.New("invalid private pairing state")
	}
	return nil
}
func (s *pairingServer) serveHTTP(w http.ResponseWriter, req *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	host, _, e := net.SplitHostPort(req.RemoteAddr)
	ip, err := netip.ParseAddr(host)
	if e != nil || err != nil || (!ip.IsPrivate() && !ip.IsLoopback()) || req.Method != "POST" || !strings.HasPrefix(strings.ToLower(req.Header.Get("Content-Type")), "application/json") {
		http.Error(w, "private JSON POST required", 403)
		return
	}
	var p pairWire
	d := json.NewDecoder(http.MaxBytesReader(w, req.Body, 8192))
	d.DisallowUnknownFields()
	if d.Decode(&p) != nil || d.Decode(new(any)) != io.EOF {
		http.Error(w, "invalid pairing request", 400)
		return
	}
	t := p.Transcript
	if !requestIDPattern.MatchString(t.ID) || !requestIDPattern.MatchString(t.Target) || !requestIDPattern.MatchString(t.Requester) || !updateHashPattern.MatchString(t.Pin) || !validPairName(t.Name) || (p.Action != "begin" && p.Action != "status") {
		http.Error(w, "invalid pairing transcript", 400)
		return
	}
	key, e := pairKey(t)
	sig, err := base64.StdEncoding.DecodeString(p.Signature)
	if e != nil || err != nil || rsa.VerifyPSS(key, crypto.SHA256, pairSignature(t, p.Action), sig, nil) != nil {
		http.Error(w, "pairing signature refused", 403)
		return
	}
	r, e := s.agent.getRuntime()
	if e != nil || t.Target != r.MachineID || t.Requester == r.MachineID {
		http.Error(w, "pairing target mismatch", 409)
		return
	}
	dir, e := privatePairDir(r, "pair-incoming")
	if e != nil {
		http.Error(w, "pairing state unavailable", 503)
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	path := filepath.Join(dir, t.ID+".json")
	var old pairIncoming
	if _, err := os.Lstat(path); err == nil {
		if readPairJSON(path, &old) != nil || old.Transcript != t {
			http.Error(w, "pairing identity changed", 409)
			return
		}
		if time.Now().After(old.Reply.Expires) {
			old.Reply.State = "expired"
			old.Reply.Sealed = ""
		}
		// A restart cannot redisplay or silently approve an old pending request.
		if old.Reply.State == "pending" && s.active != t.ID {
			old.Reply.State = "interrupted"
		}
		_ = json.NewEncoder(w).Encode(old.Reply)
		return
	} else if !os.IsNotExist(err) {
		http.Error(w, "pairing state unavailable", 503)
		return
	}
	if p.Action != "begin" {
		http.Error(w, "unknown pairing request", 404)
		return
	}
	if s.active != "" || time.Since(s.last) < time.Minute {
		http.Error(w, "another pairing request is active or cooling down", 429)
		return
	}
	// Bounded storage: remove only expired, validated pairing records in our dir.
	entries, e := os.ReadDir(dir)
	if e != nil {
		http.Error(w, "pairing state unavailable", 503)
		return
	}
	for _, entry := range entries {
		var previous pairIncoming
		if strings.HasSuffix(entry.Name(), ".json") && readPairJSON(filepath.Join(dir, entry.Name()), &previous) == nil && time.Now().After(previous.Reply.Expires) {
			_ = os.Remove(filepath.Join(dir, entry.Name()))
		}
	}
	if len(entries) > 64 {
		http.Error(w, "pairing state capacity reached", 429)
		return
	}
	x := pairIncoming{Transcript: t, Reply: pairReply{State: "pending", Code: pairCode(t), Expires: time.Now().Add(pairingTTL)}}
	if atomicJSON(path, x) != nil {
		http.Error(w, "pairing persistence failed", 503)
		return
	}
	s.last = time.Now()
	s.active = t.ID
	go s.ask(r, path, x, host, key)
	_ = json.NewEncoder(w).Encode(x.Reply)
}
func (s *pairingServer) ask(r *Runtime, path string, x pairIncoming, address string, key *rsa.PublicKey) {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()
	allow, err := s.confirm(ctx, pairPrompt{Name: x.Transcript.Name, Machine: x.Transcript.Requester, Address: address, Code: x.Reply.Code})
	s.mu.Lock()
	defer s.mu.Unlock()
	defer func() { s.active = "" }()
	x.Reply.State = "denied"
	if err != nil {
		x.Reply.State = "unavailable"
	}
	if ctx.Err() != nil || time.Now().After(x.Reply.Expires) {
		x.Reply.State = "expired"
		allow = false
	}
	if allow && err == nil {
		sealed, e := rsa.EncryptOAEP(sha256.New(), rand.Reader, key, []byte(s.token), []byte(x.Reply.Code))
		if e == nil {
			x.Reply.State = "approved"
			x.Reply.Sealed = base64.StdEncoding.EncodeToString(sealed)
		} else {
			x.Reply.State = "unavailable"
		}
	}
	_ = atomicJSON(path, x) // failure leaves pending/interrupted, never an implicit allow
}
func pairHTTP(x pairOutgoing, action string) (pairReply, error) {
	var result pairReply
	u, e := url.Parse(x.Device.Endpoint)
	if e != nil || u.Scheme != "https" || u.User != nil || u.RawQuery != "" || u.Fragment != "" || u.Path != "/gateway" || !updateHashPattern.MatchString(x.Device.CertificateSHA256) {
		return result, errors.New("pinned HTTPS gateway required")
	}
	ip, e := netip.ParseAddr(u.Hostname())
	if e != nil || (!ip.IsPrivate() && !ip.IsLoopback()) {
		return result, errors.New("pairing requires an explicit private LAN address")
	}
	b, e := base64.StdEncoding.DecodeString(x.PrivateKey)
	if e != nil {
		return result, errors.New("private pairing key unavailable")
	}
	key, e := x509.ParsePKCS1PrivateKey(b)
	if e != nil {
		return result, errors.New("private pairing key invalid")
	}
	sig, e := rsa.SignPSS(rand.Reader, key, crypto.SHA256, pairSignature(x.Transcript, action), nil)
	if e != nil {
		return result, e
	}
	body, _ := json.Marshal(pairWire{Transcript: x.Transcript, Action: action, Signature: base64.StdEncoding.EncodeToString(sig)})
	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	req, _ := http.NewRequestWithContext(ctx, "POST", x.Device.Endpoint+"/pair", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	tr := &http.Transport{Proxy: nil, TLSClientConfig: &tls.Config{MinVersion: tls.VersionTLS12, InsecureSkipVerify: true, VerifyConnection: func(cs tls.ConnectionState) error {
		if len(cs.PeerCertificates) == 0 {
			return errors.New("missing pairing certificate")
		}
		cert := cs.PeerCertificates[0]
		sum := sha256.Sum256(cert.Raw)
		if hex.EncodeToString(sum[:]) != x.Transcript.Pin || time.Now().Before(cert.NotBefore) || time.Now().After(cert.NotAfter) {
			return errors.New("pairing certificate mismatch or expired")
		}
		return nil
	}}}
	defer tr.CloseIdleConnections()
	client := &http.Client{Transport: tr, CheckRedirect: func(*http.Request, []*http.Request) error { return errors.New("pairing redirect refused") }}
	resp, e := client.Do(req)
	if e != nil {
		return result, errors.New("pairing connection unavailable; inspect the same request_id")
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return result, errors.New("pairing refused or unsupported; check target version and active request")
	}
	d := json.NewDecoder(io.LimitReader(resp.Body, 4096))
	d.DisallowUnknownFields()
	if d.Decode(&result) != nil || d.Decode(new(any)) != io.EOF || result.Code != pairCode(x.Transcript) {
		return result, errors.New("invalid pairing response")
	}
	return result, nil
}
func publicPair(x pairOutgoing, reply pairReply) any {
	return map[string]any{"state": reply.State, "pairing_id": x.Transcript.ID, "device_id": x.Device.ID, "target_machine_id": x.Device.MachineID, "name": x.Device.Name, "comparison_code": pairCode(x.Transcript), "expires_at": reply.Expires, "completed": x.Completed}
}

var outgoingPairMu sync.Mutex

func (r *Runtime) interactivePair(p runtimeRequest) (any, error) {
	outgoingPairMu.Lock()
	defer outgoingPairMu.Unlock()
	dir, e := privatePairDir(r, "pair-outgoing")
	if e != nil {
		return nil, e
	}
	id := p.RequestID
	if p.Operation == "device_pair_complete" {
		id = p.PairingID
	}
	if !requestIDPattern.MatchString(id) {
		return nil, errors.New("valid pairing request_id required")
	}
	path := filepath.Join(dir, id+".json")
	var x pairOutgoing
	if p.Operation == "device_pair_begin" {
		if p.Device == nil {
			return nil, errors.New("discovered device required")
		}
		device := *p.Device
		if device.ID == "" || !requestIDPattern.MatchString(device.MachineID) || !validPairName(device.Name) || !updateHashPattern.MatchString(device.CertificateSHA256) || device.Via != "" || device.TokenEnv != "" || device.AccessIDEnv != "" || device.AccessSecretEnv != "" || device.Status == "retired" {
			return nil, errors.New("untrusted direct LAN identity required, without credentials or route")
		}
		name := p.Label
		if name == "" {
			name = r.deviceName()
		}
		if !validPairName(name) {
			return nil, errors.New("invalid requester name")
		}
		if _, err := os.Lstat(path); err == nil {
			if readPairJSON(path, &x) != nil || x.Device != device || x.Transcript.Name != name {
				return nil, errors.New("pairing request_id reused with different arguments")
			}
		} else if os.IsNotExist(err) {
			key, err := rsa.GenerateKey(rand.Reader, 2048)
			if err != nil {
				return nil, err
			}
			x = pairOutgoing{Device: device, Expires: time.Now().Add(pairingTTL), PrivateKey: base64.StdEncoding.EncodeToString(x509.MarshalPKCS1PrivateKey(key))}
			x.Transcript = pairTranscript{ID: id, Target: device.MachineID, Pin: strings.ToLower(device.CertificateSHA256), Requester: r.MachineID, Name: name, PublicKey: base64.StdEncoding.EncodeToString(x509.MarshalPKCS1PublicKey(&key.PublicKey))}
			if e = atomicJSON(path, x); e != nil {
				return nil, e
			}
		} else {
			return nil, err
		}
	} else if e = readPairJSON(path, &x); e != nil {
		return nil, e
	}
	if x.Completed {
		return publicPair(x, pairReply{State: "paired", Expires: x.Expires}), nil
	}
	if time.Now().After(x.Expires) {
		x.PrivateKey = ""
		_ = atomicJSON(path, x)
		return publicPair(x, pairReply{State: "expired", Expires: x.Expires}), nil
	}
	action := "status"
	if p.Operation == "device_pair_begin" {
		action = "begin"
	}
	reply, e := pairHTTP(x, action)
	if e != nil {
		return nil, e
	}
	if p.Operation != "device_pair_complete" {
		return publicPair(x, reply), nil
	}
	if reply.State != "approved" {
		return nil, errors.New("target has not approved this pairing")
	}
	keyBytes, _ := base64.StdEncoding.DecodeString(x.PrivateKey)
	key, e := x509.ParsePKCS1PrivateKey(keyBytes)
	if e != nil {
		return nil, e
	}
	sealed, e := base64.StdEncoding.DecodeString(reply.Sealed)
	if e != nil {
		return nil, errors.New("invalid sealed pairing credential")
	}
	credential, e := rsa.DecryptOAEP(sha256.New(), rand.Reader, key, sealed, []byte(pairCode(x.Transcript)))
	if e != nil || len(credential) < 32 || len(credential) > 128 {
		return nil, errors.New("pairing credential verification failed")
	}
	// Reuse the existing atomic/CAS/private-import path; no secrets enter MCP.
	enrollmentDir := filepath.Join(r.home, "Config", "enrollments")
	if e = os.MkdirAll(enrollmentDir, 0700); e != nil {
		return nil, e
	}
	if e = protectRuntimeDirectory(enrollmentDir); e != nil {
		return nil, e
	}
	filename := "interactive-" + id + ".json"
	enrollment := filepath.Join(enrollmentDir, filename)
	if e = atomicJSON(enrollment, map[string]any{"machine_id": x.Device.MachineID, "name": x.Device.Name, "endpoint": x.Device.Endpoint, "certificate_sha256": x.Device.CertificateSHA256, "token": string(credential)}); e != nil {
		return nil, e
	}
	defer os.Remove(enrollment)
	result, e := r.deviceCall(runtimeRequest{Operation: "device_pair", MachineID: r.MachineID, DeviceID: x.Device.ID, Label: x.Device.Name, Path: filename, Expected: p.Expected, RequestID: p.RequestID})
	if e != nil {
		return nil, e
	}
	x.Completed = true
	x.PrivateKey = ""
	if e = atomicJSON(path, x); e != nil {
		return nil, errors.New("pairing saved; final state uncertain, inspect device_list and request receipt")
	}
	return result, nil
}
