package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"crypto/tls"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

const maxGatewayHops = 8

var devicesMu sync.Mutex

type peerDevice struct {
	home              string
	ID                string `json:"device_id"`
	Name              string `json:"name,omitempty"`
	Kind              string `json:"kind,omitempty"`
	Status            string `json:"status,omitempty"`
	MachineID         string `json:"machine_id"`
	Endpoint          string `json:"endpoint,omitempty"`
	Via               string `json:"via,omitempty"`
	TokenEnv          string `json:"token_env,omitempty"`
	CertificateSHA256 string `json:"certificate_sha256,omitempty"`
	AccessIDEnv       string `json:"access_id_env,omitempty"`
	AccessSecretEnv   string `json:"access_secret_env,omitempty"`
}
type deviceObservation struct {
	MachineID string `json:"machine_id"`
	State     string `json:"state"`
	CheckedAt string `json:"checked_at"`
	LastSeen  string `json:"last_seen,omitempty"`
}

func deviceConfigPath(home string) string { return filepath.Join(home, "Config", "devices.json") }
func configFingerprint(b []byte) string {
	if b == nil {
		return "missing"
	}
	return digest(b)
}
func readPeers(home string) ([]peerDevice, []byte, error) {
	b, e := os.ReadFile(deviceConfigPath(home))
	if os.IsNotExist(e) {
		return []peerDevice{}, nil, nil
	}
	if e != nil {
		return nil, nil, e
	}
	if len(b) > 65536 {
		return nil, nil, errors.New("device configuration too large")
	}
	var peers []peerDevice
	d := json.NewDecoder(bytes.NewReader(bytes.TrimPrefix(b, []byte{0xef, 0xbb, 0xbf})))
	d.DisallowUnknownFields()
	if d.Decode(&peers) != nil || d.Decode(new(any)) != io.EOF {
		return nil, nil, errors.New("invalid devices.json")
	}
	for i := range peers {
		peers[i].home = home
	}
	if e = validatePeers(peers); e != nil {
		return nil, nil, e
	}
	return peers, b, nil
}
func configuredPeers() ([]peerDevice, error) {
	p, _, e := readPeers(os.Getenv("JARVIS_HOME"))
	return p, e
}
func validatePeers(peers []peerDevice) error {
	if len(peers) > 128 {
		return errors.New("maximum 128 configured devices")
	}
	seen := map[string]peerDevice{}
	machines := map[string]bool{}
	names := map[string]bool{}
	for _, p := range peers {
		if !requestIDPattern.MatchString(p.MachineID) || p.ID == "" || len(p.ID) > 80 || strings.ContainsAny(p.ID, "\x00\r\n") || len(p.Name) > 160 {
			return errors.New("invalid peer identity/name")
		}
		if _, ok := seen[p.ID]; ok || machines[p.MachineID] {
			return errors.New("duplicate peer identity")
		}
		name := p.Name
		if name == "" {
			name = p.ID
		}
		name = strings.ToLower(strings.TrimSpace(name))
		if name == "" || strings.ContainsAny(name, "\x00\r\n") {
			return errors.New("invalid device name")
		}
		if p.Status != "retired" {
			if names[name] {
				return errors.New("device name already assigned to another active computer")
			}
			names[name] = true
		}
		seen[p.ID] = p
		machines[p.MachineID] = true
		if p.Status != "" && p.Status != "active" && p.Status != "retired" {
			return errors.New("invalid device status")
		}
		if p.Kind != "" && p.Kind != "stable" && p.Kind != "temporary" {
			return errors.New("invalid device kind")
		}
		if p.Via != "" {
			if p.Endpoint != "" || p.TokenEnv != "" || p.AccessIDEnv != "" || p.AccessSecretEnv != "" || p.CertificateSHA256 != "" {
				return errors.New("routed device must reference only its next gateway")
			}
			continue
		}
		u, e := url.Parse(p.Endpoint)
		if e != nil || u.Hostname() == "" || u.User != nil || u.Fragment != "" || u.RawQuery != "" || (u.Path != "/mcp" && u.Path != "/gateway") {
			return errors.New("invalid peer endpoint")
		}
		local := os.Getenv("JARVIS_TEST_PEERS") == "1" && u.Scheme == "http" && (u.Hostname() == "127.0.0.1" || u.Hostname() == "::1")
		if u.Scheme != "https" && !local {
			return errors.New("peers require HTTPS")
		}
		if p.TokenEnv != "" && !envName.MatchString(p.TokenEnv) {
			return errors.New("invalid peer token reference")
		}
		if (p.AccessIDEnv == "") != (p.AccessSecretEnv == "") || p.AccessIDEnv != "" && (!envName.MatchString(p.AccessIDEnv) || !envName.MatchString(p.AccessSecretEnv)) {
			return errors.New("invalid Access reference")
		}
		if u.Path == "/gateway" && p.TokenEnv == "" {
			return errors.New("gateway bearer reference required")
		}
		if p.CertificateSHA256 != "" && (!updateHashPattern.MatchString(p.CertificateSHA256) || u.Path != "/gateway") {
			return errors.New("invalid gateway certificate pin")
		}
	}
	for _, p := range peers {
		route := map[string]bool{p.ID: true}
		next := p.Via
		for next != "" {
			if route[next] || len(route) >= maxGatewayHops {
				return errors.New("cyclic or excessive configured route")
			}
			route[next] = true
			v, ok := seen[next]
			if !ok {
				return errors.New("unknown next gateway")
			}
			next = v.Via
		}
	}
	return nil
}

// The gateway envelope is accepted only on the separate TLS/bearer listener.
// Routing is never sent as untrusted headers to the ordinary Cloudflare endpoint.
type gatewayEnvelope struct {
	Target    string          `json:"target_machine_id"`
	Method    string          `json:"method"`
	Arguments json.RawMessage `json:"arguments"`
	Visited   []string        `json:"visited"`
}

func peerCredential(p peerDevice) string {
	if v := os.Getenv(p.TokenEnv); v != "" {
		return v
	}
	if p.home != "" && envName.MatchString(p.TokenEnv) && strings.HasPrefix(p.TokenEnv, "JARVIS_PEER_") {
		b, e := os.ReadFile(filepath.Join(p.home, "Config", "peer-credentials", p.TokenEnv+".txt"))
		if e == nil && len(b) <= 1024 {
			return strings.TrimSpace(string(b))
		}
	}
	return ""
}
func peerRPC(p peerDevice, method string, args any) (map[string]any, error) {
	return peerRPCTrace(p, method, args, nil)
}
func peerRPCTrace(p peerDevice, method string, args any, visited []string) (map[string]any, error) {
	u, _ := url.Parse(p.Endpoint)
	isGateway := u != nil && u.Path == "/gateway"
	var payload any = map[string]any{"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": map[string]any{"name": method, "arguments": args}}
	if isGateway {
		raw, e := json.Marshal(args)
		if e != nil {
			return nil, e
		}
		payload = gatewayEnvelope{Target: p.MachineID, Method: method, Arguments: raw, Visited: visited}
	}
	b, e := json.Marshal(payload)
	if e != nil || len(b) > 8*1024*1024 {
		return nil, errors.New("invalid/oversized peer request")
	}
	timeout := 120 * time.Second
	if method == "machine_info" {
		timeout = 8 * time.Second
	}
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	req, e := http.NewRequestWithContext(ctx, "POST", p.Endpoint, bytes.NewReader(b))
	if e != nil {
		return nil, e
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json, text/event-stream")
	if p.TokenEnv != "" {
		v := peerCredential(p)
		minimum := 24
		if isGateway {
			minimum = 32
		}
		if len(v) < minimum {
			return nil, errors.New("peer bearer reference unavailable")
		}
		req.Header.Set("Authorization", "Bearer "+v)
	}
	if p.AccessIDEnv != "" {
		id, secret := os.Getenv(p.AccessIDEnv), os.Getenv(p.AccessSecretEnv)
		if id == "" || secret == "" {
			return nil, errors.New("peer Access references unavailable")
		}
		req.Header.Set("CF-Access-Client-Id", id)
		req.Header.Set("CF-Access-Client-Secret", secret)
	}
	if p.TokenEnv == "" && p.AccessIDEnv == "" {
		return nil, errors.New("authenticated peer configuration required")
	}
	tr := http.DefaultTransport.(*http.Transport).Clone()
	if p.CertificateSHA256 != "" {
		pin := strings.ToLower(p.CertificateSHA256)
		// Exact certificate pin is an explicit trust anchor, including self-signed LAN
		// certificates. Never disable verification without this mandatory comparison.
		tr.TLSClientConfig = &tls.Config{MinVersion: tls.VersionTLS12, InsecureSkipVerify: true, VerifyConnection: func(cs tls.ConnectionState) error {
			if len(cs.PeerCertificates) == 0 {
				return errors.New("missing peer certificate")
			}
			sum := sha256.Sum256(cs.PeerCertificates[0].Raw)
			if hex.EncodeToString(sum[:]) != pin {
				return errors.New("gateway certificate mismatch")
			}
			cert := cs.PeerCertificates[0]
			now := time.Now()
			if now.Before(cert.NotBefore) || now.After(cert.NotAfter) {
				return errors.New("gateway certificate expired")
			}
			return nil
		}}
	}
	defer tr.CloseIdleConnections()
	client := &http.Client{Timeout: timeout, Transport: tr, CheckRedirect: func(*http.Request, []*http.Request) error { return errors.New("peer redirects refused") }}
	res, e := client.Do(req)
	if e != nil {
		return nil, errors.New("peer connection failed or timed out; execution may be uncertain; inspect target request_id/update_id before further action")
	}
	defer res.Body.Close()
	if res.StatusCode != 200 {
		return nil, errors.New("peer authentication or HTTP failure")
	}
	data, e := io.ReadAll(io.LimitReader(res.Body, 8*1024*1024+1))
	if e != nil || len(data) > 8*1024*1024 {
		return nil, errors.New("peer result too large or incomplete; inspect target receipt")
	}
	if isGateway {
		var result map[string]any
		if json.Unmarshal(data, &result) != nil || result["content"] == nil {
			return nil, errors.New("invalid gateway response")
		}
		return result, nil
	}
	var rpc struct {
		Result map[string]any `json:"result"`
		Error  any            `json:"error"`
	}
	if json.Unmarshal(data, &rpc) != nil || rpc.Error != nil || rpc.Result == nil {
		return nil, errors.New("invalid peer MCP response")
	}
	return rpc.Result, nil
}
func peerIdentity(p peerDevice) error {
	v, e := peerRPC(p, "machine_info", map[string]any{})
	if e != nil {
		return e
	}
	if v["isError"] == true {
		return errors.New("peer refused identity request")
	}
	content, ok := v["content"].([]any)
	if !ok {
		return errors.New("invalid peer identity")
	}
	for _, c := range content {
		m, _ := c.(map[string]any)
		s, _ := m["text"].(string)
		var id struct {
			MachineID string `json:"machine_id"`
		}
		if json.Unmarshal([]byte(s), &id) == nil && id.MachineID == p.MachineID {
			return nil
		}
	}
	return errors.New("peer identity mismatch; operation blocked")
}
func toolDeclared(name string) bool {
	for _, v := range toolsList().(map[string]any)["tools"].([]any) {
		if v.(map[string]any)["name"] == name {
			return true
		}
	}
	return false
}
func forwardArguments(raw json.RawMessage, target string) (string, map[string]any, error) {
	var args map[string]any
	if json.Unmarshal(raw, &args) != nil || args == nil {
		return "", nil, errors.New("call must be an object")
	}
	op, _ := args["operation"].(string)
	delete(args, "operation")
	if !toolDeclared(op) || op == "device_call" || op == "task_start" && args["operation"] == "runtime" {
		return "", nil, errors.New("unknown operation or raw nested forwarding; use configured via routes")
	}
	if id, ok := args["machine_id"]; ok && id != target {
		return "", nil, errors.New("forwarded machine_id mismatch")
	}
	args["machine_id"] = target
	return op, args, nil
}
func (r *Runtime) observeDevice(p peerDevice, online bool) error {
	devicesMu.Lock()
	defer devicesMu.Unlock()
	path := filepath.Join(r.dir, "device-observations.json")
	states := map[string]deviceObservation{}
	if b, e := os.ReadFile(path); e == nil {
		if json.Unmarshal(b, &states) != nil {
			return errors.New("invalid device observation state; preserved")
		}
	} else if !os.IsNotExist(e) {
		return e
	}
	s := states[p.ID]
	if s.MachineID != "" && s.MachineID != p.MachineID {
		return errors.New("observation identity mismatch")
	}
	s.MachineID = p.MachineID
	s.CheckedAt = utcNow()
	s.State = "offline"
	if online {
		s.State = "online"
		s.LastSeen = s.CheckedAt
	}
	states[p.ID] = s
	return atomicJSON(path, states)
}
func (r *Runtime) deviceCall(p runtimeRequest) (any, error) { return r.deviceCallTrace(p, nil) }
func (r *Runtime) deviceCallTrace(p runtimeRequest, visited []string) (any, error) {
	if len(visited) >= maxGatewayHops {
		return nil, errors.New("gateway hop limit reached")
	}
	for _, id := range visited {
		if id == r.MachineID {
			return nil, errors.New("gateway cycle refused")
		}
	}
	visited = append(append([]string{}, visited...), r.MachineID)
	devicesMu.Lock()
	peers, b, e := readPeers(r.home)
	devicesMu.Unlock()
	if e != nil {
		return nil, e
	}
	if p.Operation == "device_configure" || p.Operation == "device_retire" || p.Operation == "device_pair" {
		return r.mutation(p, func(_ *Receipt) (any, error) {
			devicesMu.Lock()
			defer devicesMu.Unlock()
			peers, b, e = readPeers(r.home)
			if e != nil {
				return nil, e
			}
			if p.Expected != configFingerprint(b) {
				return nil, errors.New("device configuration changed; reread device_list")
			}
			found := -1
			for i, v := range peers {
				if v.ID == p.DeviceID {
					found = i
				}
			}
			var paired *peerDevice
			credentialToRemove := ""
			defer func() {
				if credentialToRemove != "" {
					_ = os.Remove(credentialToRemove)
				}
			}()
			if p.Operation == "device_pair" {
				if filepath.Base(p.Path) != p.Path || !strings.HasSuffix(p.Path, ".json") {
					return nil, errors.New("enrollment must be a filename in Config/enrollments")
				}
				path := filepath.Join(r.home, "Config", "enrollments", p.Path)
				info, err := os.Lstat(path)
				if err != nil || !info.Mode().IsRegular() || info.Size() > 16384 {
					return nil, errors.New("private enrollment unavailable")
				}
				data, err := os.ReadFile(path)
				if err != nil {
					return nil, err
				}
				var enrollment struct {
					MachineID string `json:"machine_id"`
					Name      string `json:"name"`
					Endpoint  string `json:"endpoint"`
					Pin       string `json:"certificate_sha256"`
					Token     string `json:"token"`
				}
				d := json.NewDecoder(bytes.NewReader(data))
				d.DisallowUnknownFields()
				if d.Decode(&enrollment) != nil || d.Decode(new(any)) != io.EOF || len(enrollment.Token) < 32 || len(enrollment.Token) > 1024 {
					return nil, errors.New("invalid enrollment")
				}
				name := p.Label
				if name == "" {
					name = enrollment.Name
				}
				ref := "JARVIS_PEER_" + digest([]byte(p.RequestID))[:32]
				paired = &peerDevice{ID: p.DeviceID, Name: name, MachineID: enrollment.MachineID, Endpoint: enrollment.Endpoint, CertificateSHA256: enrollment.Pin, TokenEnv: ref, home: r.home}
				if paired.CertificateSHA256 == "" {
					return nil, errors.New("enrollment certificate pin required")
				}
				if err = validatePeers([]peerDevice{*paired}); err != nil {
					return nil, err
				}
				dir := filepath.Join(r.home, "Config", "peer-credentials")
				if err = os.MkdirAll(dir, 0700); err != nil {
					return nil, err
				}
				if err = protectRuntimeDirectory(dir); err != nil {
					return nil, err
				}
				credential := filepath.Join(dir, ref+".txt")
				f, err := os.OpenFile(credential, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
				if err != nil {
					return nil, err
				}
				credentialToRemove = credential
				_, err = f.WriteString(enrollment.Token)
				closeErr := f.Close()
				if err != nil || closeErr != nil {
					return nil, errors.New("credential persistence failed")
				}
				if err = peerIdentity(*paired); err != nil {
					_ = os.Remove(credential)
					return nil, err
				}
			}
			if p.Operation == "device_retire" {
				if found < 0 {
					return nil, errors.New("unknown device")
				}
				peers[found].Status = "retired"
			} else {
				if p.Device == nil && paired == nil {
					return nil, errors.New("device required")
				}
				var next peerDevice
				if paired != nil {
					next = *paired
				} else {
					next = *p.Device
				}
				found = -1
				for i, v := range peers {
					if v.ID == next.ID {
						found = i
						if v.MachineID != next.MachineID {
							return nil, errors.New("cannot reuse device_id for a different machine; retire old and use a new ID")
						}
					}
				}
				if found < 0 {
					peers = append(peers, next)
				} else {
					peers[found] = next
				}
			}
			if e = validatePeers(peers); e != nil {
				return nil, e
			}
			path := deviceConfigPath(r.home)
			if e = os.MkdirAll(filepath.Dir(path), 0700); e != nil {
				return nil, e
			}
			if b != nil {
				if e = os.WriteFile(filepath.Join(r.dir, "backups", "devices-"+p.RequestID+".json"), b, 0600); e != nil {
					return nil, e
				}
			}
			if e = atomicJSON(path, peers); e != nil {
				return nil, e
			}
			saved, e := os.ReadFile(path)
			if e != nil {
				return nil, e
			}
			credentialToRemove = ""
			return map[string]any{"configuration_sha256": digest(saved), "devices": peers, "state": "saved"}, nil
		})
	}
	if p.Operation == "device_list" {
		out := []any{map[string]any{"device_id": "local", "machine_id": r.MachineID, "online": true, "local": true}}
		items := make([]any, len(peers))
		var wg sync.WaitGroup
		slots := make(chan struct{}, 16)
		for i, v := range peers {
			wg.Add(1)
			go func(i int, v peerDevice) {
				defer wg.Done()
				slots <- struct{}{}
				defer func() { <-slots }()
				item := map[string]any{"device_id": v.ID, "name": v.Name, "machine_id": v.MachineID, "kind": v.Kind, "status": v.Status, "via": v.Via, "online": false}
				if v.Status == "retired" {
					item["status"] = "retired"
				} else {
					raw, _ := json.Marshal(map[string]any{"operation": "machine_info"})
					result, err := r.deviceCallTrace(runtimeRequest{DeviceID: v.ID, Call: raw}, visited[:len(visited)-1])
					online := err == nil && !toolFailed(result)
					item["online"] = online
					if !online {
						item["error"] = "device unreachable, identity refused or route unavailable"
					}
				}
				items[i] = item
			}(i, v)
		}
		wg.Wait()
		out = append(out, items...)
		states := map[string]deviceObservation{}
		devicesMu.Lock()
		data, err := os.ReadFile(filepath.Join(r.dir, "device-observations.json"))
		devicesMu.Unlock()
		if err == nil {
			if json.Unmarshal(data, &states) != nil {
				return nil, errors.New("invalid device observation state")
			}
		} else if !os.IsNotExist(err) {
			return nil, err
		}
		return map[string]any{"devices": out, "configuration_sha256": configFingerprint(b), "observations": states, "checked_at": utcNow()}, nil
	}
	var selected *peerDevice
	for i := range peers {
		if peers[i].ID == p.DeviceID {
			selected = &peers[i]
			break
		}
	}
	if selected == nil {
		return nil, errors.New("unknown device; configure trusted peer locally")
	}
	if selected.Status == "retired" {
		return nil, errors.New("device retired; operation blocked")
	}
	method, args, e := forwardArguments(p.Call, selected.MachineID)
	if e != nil {
		return nil, e
	}
	for _, id := range visited {
		if id == selected.MachineID {
			return nil, errors.New("gateway cycle refused")
		}
	}
	dest := *selected
	if selected.Via != "" {
		for i := range peers {
			if peers[i].ID == selected.Via {
				dest = peers[i]
				break
			}
		}
		if dest.Status == "retired" {
			return nil, errors.New("gateway retired; route blocked")
		}
		if dest.Via != "" {
			return nil, errors.New("next gateway must be directly reachable; configure further hops on that gateway")
		}
		u, _ := url.Parse(dest.Endpoint)
		if u == nil || u.Path != "/gateway" {
			return nil, errors.New("multi-hop requires the authenticated gateway endpoint")
		}
		normalized := map[string]any{}
		for k, v := range args {
			normalized[k] = v
		}
		normalized["operation"] = method
		method = "device_call"
		args = map[string]any{"machine_id": dest.MachineID, "device_id": selected.ID, "call": normalized}
	}
	for _, id := range visited {
		if id == dest.MachineID {
			return nil, errors.New("gateway cycle refused")
		}
	}
	if e = peerIdentity(dest); e != nil {
		_ = r.observeDevice(*selected, false)
		return nil, e
	}
	result, e := peerRPCTrace(dest, method, args, visited)
	// An operation error is not proof that the computer is offline.
	observationError := r.observeDevice(*selected, e == nil && (selected.Via == "" || !toolFailed(result)))
	if e != nil {
		return nil, e
	}
	if observationError != nil {
		return nil, observationError
	}
	return result, nil
}
