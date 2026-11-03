package main

import (
	"crypto/subtle"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

type agentPrivileges struct {
	Elevated  *bool  `json:"elevated"`
	PID       int    `json:"pid"`
	CheckedAt string `json:"checked_at"`
	Source    string `json:"source"`
	Error     string `json:"error,omitempty"`
}

func currentAgentPrivileges() agentPrivileges {
	p := agentPrivileges{PID: os.Getpid(), CheckedAt: utcNow(), Source: "process_token"}
	v, e := currentProcessElevated()
	if e != nil {
		p.Error = "token_query_failed"
	} else {
		p.Elevated = &v
	}
	return p
}

type adminRestart struct {
	ID            string    `json:"request_id"`
	Machine       string    `json:"machine_id"`
	Phase         string    `json:"phase"`
	RequestedAt   string    `json:"requested_at"`
	UpdatedAt     string    `json:"updated_at"`
	ExpiresAt     time.Time `json:"expires_at"`
	ParentPID     int       `json:"old_pid"`
	ParentCreated uint64    `json:"parent_created"`
	NewPID        int       `json:"new_pid,omitempty"`
	Reason        string    `json:"reason,omitempty"`
	Home          string    `json:"home"`
	StateDir      string    `json:"state_dir"`
	Executable    string    `json:"executable"`
	SHA256        string    `json:"sha256"`
	Listen        string    `json:"listen"`
	SID           string    `json:"sid"`
	Nonce         string    `json:"nonce"`
	ConfigHash    string    `json:"config_hash"`
}

func (x *adminRestart) public() any {
	return map[string]any{"request_id": x.ID, "machine_id": x.Machine, "phase": x.Phase, "old_pid": x.ParentPID, "new_pid": x.NewPID, "requested_at": x.RequestedAt, "updated_at": x.UpdatedAt, "reason": x.Reason, "verified_elevated": x.Phase == "completed", "original_stop_requested": x.Phase == "handoff" || x.Phase == "completed" || x.Phase == "failed_after_handoff"}
}
func restartConfigHash() string {
	keys := []string{"JARVIS_HOME", "JARVIS_PROJECTS_ROOT", "JARVIS_STATE_DIR", "JARVIS_MCP_TOKEN", "JARVIS_CF_TEAM_DOMAIN", "JARVIS_CF_APP_AUD", "JARVIS_CODEX_ENABLED", "JARVIS_CODEX_EXE", "JARVIS_DESKTOP_ENABLED"}
	sort.Strings(keys)
	m := map[string]string{}
	for _, k := range keys {
		m[k] = os.Getenv(k)
	}
	b, _ := json.Marshal(m)
	return digest(b)
}
func restartPath(dir, id string) (string, error) {
	if !requestIDPattern.MatchString(id) {
		return "", errors.New("invalid restart request_id")
	}
	return filepath.Join(dir, "agent-control", id+".json"), nil
}
func readRestart(dir, id string) (*adminRestart, error) {
	p, e := restartPath(dir, id)
	if e != nil {
		return nil, e
	}
	b, e := os.ReadFile(p)
	if e != nil {
		return nil, e
	}
	var x adminRestart
	if json.Unmarshal(b, &x) != nil || x.ID != id || x.Nonce == "" {
		return nil, errors.New("invalid restart record")
	}
	return &x, nil
}
func saveRestart(x *adminRestart) error {
	p, e := restartPath(x.StateDir, x.ID)
	if e != nil {
		return e
	}
	x.UpdatedAt = utcNow()
	return atomicJSON(p, x)
}
func toolReadOnly(name string) bool {
	for _, v := range runtimeCatalog {
		if v.Name == name {
			return v.ReadOnly
		}
	}
	switch name {
	case "agent_status", "project_list", "project_status", "log_list", "log_read", "diagnostic_status":
		return true
	}
	return false
}
func (a *Agent) enterControlGate(name string, args json.RawMessage) (func(), error) {
	logical := usageName(name, args)
	if logical == "agent_restart_admin" {
		return func() {}, nil
	}
	a.controlMu.RLock()
	if a.restartPending && !toolReadOnly(logical) {
		a.controlMu.RUnlock()
		return nil, errors.New("agent restart pending; read agent_restart_status before new mutations")
	}
	return a.controlMu.RUnlock, nil
}
func (a *Agent) restartIdle(r *Runtime) bool {
	a.mu.Lock()
	busy := a.task != nil && a.task.Status == "running"
	a.mu.Unlock()
	if busy {
		return false
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.closing || r.queueBroken || r.codexStarting || len(r.codexSessionOps) > 0 {
		return false
	}
	if r.queue != nil {
		for _, v := range r.queue.Entries {
			if !terminalActivity(v.Status) {
				return false
			}
		}
	}
	for _, j := range r.Jobs {
		if j.Status == "running" || j.Status == "queued" {
			return false
		}
	}
	return true
}
func (a *Agent) restartAdmin(r *Runtime, p runtimeRequest) (any, error) {
	a.controlMu.Lock()
	defer a.controlMu.Unlock()
	if !requestIDPattern.MatchString(p.RequestID) {
		return nil, errors.New("unique request_id required")
	}
	// Replay the immutable acknowledgement; completion is read through restart_status.
	path := filepath.Join(r.dir, "requests", p.RequestID+".json")
	if _, e := os.Stat(path); e == nil {
		return r.mutation(p, func(_ *Receipt) (any, error) { return nil, errors.New("unreachable") })
	}
	status := currentAgentPrivileges()
	if a.testPrivileges != nil {
		status = a.testPrivileges()
	}
	if status.Elevated == nil {
		return nil, errors.New("agent privilege verification failed; no restart attempted")
	}
	if *status.Elevated {
		return r.mutation(p, func(_ *Receipt) (any, error) {
			return map[string]any{"state": "already_elevated", "privileges": status, "restarted": false}, nil
		})
	}
	if a.restartPending {
		return nil, errors.New("another restart is pending")
	}
	if !a.restartIdle(r) {
		return nil, errors.New("agent has active, queued or uncertain work; no restart attempted")
	}
	home := os.Getenv("JARVIS_HOME")
	if !filepath.IsAbs(home) || a.listenAddress == "" {
		return nil, errors.New("restart launch configuration unavailable")
	}
	exe, e := os.Executable()
	if e != nil {
		return nil, e
	}
	b, e := os.ReadFile(exe)
	if e != nil {
		return nil, e
	}
	sid, created, e := restartIdentity()
	if e != nil && a.testLaunchRestart == nil {
		return nil, e
	}
	dir := filepath.Join(r.dir, "agent-control")
	if e = os.MkdirAll(dir, 0700); e != nil {
		return nil, e
	}
	if e = protectRuntimeDirectory(dir); e != nil {
		return nil, e
	}
	x := &adminRestart{ID: p.RequestID, Machine: r.MachineID, Phase: "awaiting_elevation", RequestedAt: utcNow(), ExpiresAt: time.Now().UTC().Add(2 * time.Minute), ParentPID: os.Getpid(), ParentCreated: created, Home: home, StateDir: r.dir, Executable: exe, SHA256: digest(b), Listen: a.listenAddress, SID: sid, Nonce: newID() + newID(), ConfigHash: restartConfigHash()}
	var launch bool
	result, e := r.mutation(p, func(_ *Receipt) (any, error) {
		if e := saveRestart(x); e != nil {
			return nil, e
		}
		a.restartPending = true
		launch = true
		return map[string]any{"state": "awaiting_elevation", "request_id": x.ID, "restarted": false, "message": "Elevation requested; UAC may require local approval. Read agent_restart_status; do not replay."}, nil
	})
	if e != nil {
		a.restartPending = false
		return nil, e
	}
	if launch {
		fn := elevationLauncher
		if a.testLaunchRestart != nil {
			fn = a.testLaunchRestart
		}
		go func() {
			if e := fn(x); e != nil {
				reason := "elevation_launch_failed"
				phase := "failed_before_handoff"
				if errors.Is(e, errElevationCancelled) {
					phase = "cancelled"
					reason = "uac_cancelled"
				}
				a.finishPendingRestart(r, x.ID, phase, reason)
			}
		}()
		go func() {
			timer := time.NewTimer(time.Until(x.ExpiresAt))
			defer timer.Stop()
			<-timer.C
			a.finishPendingRestart(r, x.ID, "expired", "elevation_approval_expired")
		}()
	}
	return result, nil
}

var elevationLauncher = launchAdminRestart
var errElevationCancelled = errors.New("elevation cancelled")

func (a *Agent) finishPendingRestart(r *Runtime, id, phase, reason string) {
	a.controlMu.Lock()
	defer a.controlMu.Unlock()
	x, e := readRestart(r.dir, id)
	if e != nil {
		return
	}
	if x.Phase == "failed_before_handoff" || x.Phase == "cancelled" || x.Phase == "expired" {
		a.restartPending = false
		return
	}
	if x.Phase != "awaiting_elevation" {
		return
	}
	x.Phase = phase
	x.Reason = reason
	if saveRestart(x) == nil {
		a.restartPending = false
	}
}
func (a *Agent) restartStatus(r *Runtime, id string) (any, error) {
	x, e := readRestart(r.dir, id)
	if e != nil {
		return nil, e
	}
	if x.Machine != r.MachineID {
		return nil, errors.New("restart machine mismatch")
	}
	return x.public(), nil
}
func (a *Agent) restartHandoff(w http.ResponseWriter, req *http.Request, token string) {
	host, _, e := net.SplitHostPort(req.RemoteAddr)
	if e != nil || net.ParseIP(host) == nil || !net.ParseIP(host).IsLoopback() || req.Method != "POST" || len(token) < 24 || subtle.ConstantTimeCompare([]byte(req.Header.Get("Authorization")), []byte("Bearer "+token)) != 1 {
		http.Error(w, "forbidden", 403)
		return
	}
	var p struct {
		ID    string `json:"request_id"`
		Nonce string `json:"nonce"`
	}
	dec := json.NewDecoder(http.MaxBytesReader(w, req.Body, 2048))
	dec.DisallowUnknownFields()
	if dec.Decode(&p) != nil {
		http.Error(w, "invalid request", 400)
		return
	}
	a.controlMu.Lock()
	defer a.controlMu.Unlock()
	r, e := a.getRuntime()
	if e != nil {
		http.Error(w, "runtime unavailable", 409)
		return
	}
	x, e := readRestart(r.dir, p.ID)
	if e != nil || !a.restartPending || x.Phase != "awaiting_elevation" || x.ParentPID != os.Getpid() || x.Machine != r.MachineID || time.Now().After(x.ExpiresAt) || subtle.ConstantTimeCompare([]byte(x.Nonce), []byte(p.Nonce)) != 1 || a.shutdownRequest == nil {
		http.Error(w, "handoff unavailable", 409)
		return
	}
	if !a.restartIdle(r) {
		http.Error(w, "work active", 409)
		return
	}
	x.Phase = "handoff"
	if saveRestart(x) != nil {
		http.Error(w, "persistence failed", 500)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	fmt.Fprint(w, `{"accepted":true}`)
	go func() { time.Sleep(250 * time.Millisecond); a.shutdownRequest() }()
}
func restartHomeState(home string) string {
	if p := os.Getenv("JARVIS_STATE_DIR"); p != "" {
		return p
	}
	return filepath.Join(home, "State", "runtime-v1")
}
func validRestartListen(s string) bool {
	h, _, e := net.SplitHostPort(s)
	return e == nil && (h == "127.0.0.1" || h == "localhost" || h == "::1")
}
func validateRestartRecord(x *adminRestart, home, exe, sid string) error {
	if !filepath.IsAbs(home) || !samePath(x.Home, home) || !samePath(x.Executable, exe) || x.SID != sid || !validRestartListen(x.Listen) || time.Now().After(x.ExpiresAt) || x.Phase != "awaiting_elevation" || x.ConfigHash != restartConfigHash() {
		return errors.New("restart identity, expiry or configuration mismatch")
	}
	rel, e := filepath.Rel(home, exe)
	if e != nil || !(strings.EqualFold(rel, "jarvis-mcp.exe") || strings.EqualFold(rel, filepath.Join("Agent", "jarvis-mcp.exe"))) {
		return errors.New("restart executable outside installed layout")
	}
	for _, p := range []string{home, filepath.Join(home, "Config"), x.StateDir, exe} {
		st, e := os.Lstat(p)
		if e != nil || st.Mode()&os.ModeSymlink != 0 || isReparse(st) {
			return errors.New("restart path invalid or reparse point")
		}
	}
	b, e := os.ReadFile(exe)
	if e != nil || digest(b) != x.SHA256 {
		return errors.New("restart binary hash changed")
	}
	return nil
}
