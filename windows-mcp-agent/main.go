package main

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"syscall"
	"time"
)

const version = "0.5.8"

type RPCRequest struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      json.RawMessage `json:"id,omitempty"`
	Method  string          `json:"method"`
	Params  json.RawMessage `json:"params,omitempty"`
}
type RPCError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}
type RPCResponse struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      json.RawMessage `json:"id"`
	Result  any             `json:"result,omitempty"`
	Error   *RPCError       `json:"error,omitempty"`
}
type Task struct {
	ID         string `json:"id"`
	Name       string `json:"name"`
	Status     string `json:"status"`
	StartedAt  string `json:"started_at"`
	FinishedAt string `json:"finished_at,omitempty"`
	Progress   int    `json:"progress_percent"`
	Result     string `json:"result,omitempty"`
	cancel     context.CancelFunc
}
type Agent struct {
	runtimeMu sync.Mutex
	runtime   *Runtime
	mu        sync.Mutex
	task      *Task
	next      int
	desktop   *desktopSnapshot
}

func (a *Agent) snapshot() any {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.task == nil {
		return map[string]any{"version": version, "state": "idle", "task": nil}
	}
	t := *a.task
	t.cancel = nil
	return map[string]any{"version": version, "state": t.Status, "task": t}
}
func (a *Agent) start(name string, seconds int) (any, error) {
	if seconds < 1 || seconds > 300 {
		return nil, errors.New("duration_seconds must be between 1 and 300")
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.task != nil && a.task.Status == "running" {
		return nil, errors.New("a task is already running")
	}
	a.next++
	ctx, cancel := context.WithCancel(context.Background())
	t := &Task{ID: fmt.Sprintf("task-%d", a.next), Name: name, Status: "running", StartedAt: time.Now().Format(time.RFC3339), cancel: cancel}
	a.task = t
	go func() {
		ticker := time.NewTicker(time.Second)
		defer ticker.Stop()
		for i := 0; i < seconds; i++ {
			select {
			case <-ctx.Done():
				a.mu.Lock()
				t.Status = "cancelled"
				t.FinishedAt = time.Now().Format(time.RFC3339)
				t.Result = "Task cancelled by operator"
				a.mu.Unlock()
				return
			case <-ticker.C:
				a.mu.Lock()
				t.Progress = (i + 1) * 100 / seconds
				a.mu.Unlock()
			}
		}
		host, _ := os.Hostname()
		a.mu.Lock()
		t.Status = "completed"
		t.FinishedAt = time.Now().Format(time.RFC3339)
		t.Result = fmt.Sprintf("Diagnostic completed on %s (%s/%s)", host, runtime.GOOS, runtime.GOARCH)
		a.mu.Unlock()
	}()
	return map[string]any{"task_id": t.ID, "message": "Diagnostic started (no shell commands or desktop actions executed)"}, nil
}
func (a *Agent) stop() (any, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.task == nil || a.task.Status != "running" {
		return nil, errors.New("no running task")
	}
	a.task.cancel()
	return map[string]any{"message": "Cancellation requested", "task_id": a.task.ID}, nil
}
func toolResult(value any, isError bool) any {
	b, _ := json.Marshal(value)
	return map[string]any{"content": []any{map[string]any{"type": "text", "text": string(b)}}, "isError": isError}
}
func (a *Agent) invoke(name string, args json.RawMessage) any {
	if name == "task_start" {
		var bridge struct {
			Operation string          `json:"operation"`
			Request   json.RawMessage `json:"request"`
		}
		if json.Unmarshal(args, &bridge) == nil && bridge.Operation == "runtime" {
			return a.invokeRuntime("", bridge.Request)
		}
	}
	if isRuntimeTool(name) {
		return a.invokeRuntime(name, args)
	}
	switch name {
	case "agent_status":
		return toolResult(a.snapshot(), false)
	case "project_list":
		value, err := projectList()
		if err != nil {
			return toolResult(map[string]string{"error": err.Error()}, true)
		}
		return toolResult(value, false)
	case "project_status":
		var p struct {
			Project string          `json:"project"`
			Desktop json.RawMessage `json:"desktop"`
		}
		if err := json.Unmarshal(args, &p); err != nil {
			return toolResult(map[string]string{"error": "invalid arguments"}, true)
		}
		value, err := projectStatus(p.Project)
		if err != nil {
			return toolResult(map[string]string{"error": err.Error()}, true)
		}
		return toolResult(value, false)
	case "project_clone":
		var p struct {
			RepositoryURL string `json:"repository_url"`
			Project       string `json:"project"`
		}
		if err := json.Unmarshal(args, &p); err != nil {
			return toolResult(map[string]string{"error": "invalid arguments"}, true)
		}
		value, err := a.cloneProject(p.RepositoryURL, p.Project)
		if err != nil {
			return toolResult(map[string]string{"error": err.Error()}, true)
		}
		return toolResult(value, false)
	case "task_start":
		var p struct {
			Name      string          `json:"name"`
			Duration  int             `json:"duration_seconds"`
			Operation string          `json:"operation"`
			Project   string          `json:"project"`
			Desktop   json.RawMessage `json:"desktop"`
		}
		if err := json.Unmarshal(args, &p); err != nil {
			return toolResult(map[string]string{"error": "invalid arguments"}, true)
		}
		if p.Name == "" {
			p.Name = "Diagnostic"
		}
		if p.Duration == 0 {
			p.Duration = 15
		}
		var v any
		var err error
		if p.Operation == "desktop_inspect" || p.Operation == "desktop_action" {
			v, err = a.startDesktopTask(p.Operation, p.Desktop)
		} else if p.Operation != "" {
			v, err = a.startGoTask(p.Operation, p.Project)
		} else {
			v, err = a.start(p.Name, p.Duration)
		}
		if err != nil {
			return toolResult(map[string]string{"error": err.Error()}, true)
		}
		return toolResult(v, false)
	case "update_prepare":
		var p updateRequest
		if err := json.Unmarshal(args, &p); err != nil {
			return toolResult(map[string]string{"error": "invalid update parameters"}, true)
		}
		v, err := prepareUpdate(p)
		if err != nil {
			return toolResult(map[string]string{"error": err.Error()}, true)
		}
		return toolResult(v, false)
	case "update_execute":
		var p struct {
			UpdateID string `json:"update_id"`
			SHA256   string `json:"sha256"`
		}
		if err := json.Unmarshal(args, &p); err != nil {
			return toolResult(map[string]string{"error": "invalid update parameters"}, true)
		}
		v, err := executeUpdate(p.UpdateID, p.SHA256)
		if err != nil {
			return toolResult(map[string]string{"error": err.Error()}, true)
		}
		return toolResult(v, false)
	case "diagnostic_status":
		var p struct {
			ID string `json:"id"`
		}
		if err := json.Unmarshal(args, &p); err != nil {
			return toolResult(map[string]string{"error": "invalid parameters"}, true)
		}
		v, err := diagnosticStatus(p.ID)
		if err != nil {
			return toolResult(map[string]string{"error": err.Error()}, true)
		}
		return toolResult(v, false)
	case "log_list":
		value, err := logList()
		if err != nil {
			return toolResult(map[string]string{"error": err.Error()}, true)
		}
		return toolResult(value, false)
	case "log_read":
		var p logReadRequest
		if err := json.Unmarshal(args, &p); err != nil {
			return toolResult(map[string]string{"error": "invalid log parameters"}, true)
		}
		value, err := logRead(p)
		if err != nil {
			return toolResult(map[string]string{"error": err.Error()}, true)
		}
		return toolResult(value, false)
	case "task_stop":
		v, err := a.stop()
		if err != nil {
			return toolResult(map[string]string{"error": err.Error()}, true)
		}
		return toolResult(v, false)
	}
	return toolResult(map[string]string{"error": "unknown tool"}, true)
}
func legacyToolsList() any {
	return map[string]any{"tools": []any{
		map[string]any{"name": "project_list", "description": "List immediate Git repositories within the explicitly authorized JARVIS_PROJECTS_ROOT (read-only)", "inputSchema": map[string]any{"type": "object", "properties": map[string]any{}}},
		map[string]any{"name": "project_clone", "description": "Clone a GitHub HTTPS repository from approved owner Andry-iu into JARVIS_PROJECTS_ROOT without overwriting an existing directory. Runs as a cancellable task; use agent_status to monitor. Requires Git credentials preconfigured on the notebook.", "inputSchema": map[string]any{"type": "object", "properties": map[string]any{"repository_url": map[string]any{"type": "string"}, "project": map[string]any{"type": "string"}}, "required": []string{"repository_url"}}},
		map[string]any{"name": "project_status", "description": "Read current Git branch, commit and tracked dirty file count for a repository within the authorized projects root (read-only)", "inputSchema": map[string]any{"type": "object", "properties": map[string]any{"project": map[string]any{"type": "string"}}, "required": []string{"project"}}},
		map[string]any{"name": "agent_status", "description": "Read Windows agent status and most recent task", "inputSchema": map[string]any{"type": "object", "properties": map[string]any{}}},
		map[string]any{"name": "task_start", "description": "Start a cancellable diagnostic or restricted Go test/build task in an authorized module directory. No arbitrary commands.", "inputSchema": map[string]any{"type": "object", "properties": map[string]any{"name": map[string]any{"type": "string"}, "duration_seconds": map[string]any{"type": "integer", "minimum": 1, "maximum": 300}, "operation": map[string]any{"type": "string", "enum": []string{"go_test", "go_build", "desktop_inspect", "desktop_action"}}, "project": map[string]any{"type": "string"}, "desktop": map[string]any{"type": "object", "additionalProperties": false, "properties": map[string]any{"snapshot_id": map[string]any{"type": "string"}, "element_id": map[string]any{"type": "string"}, "action": map[string]any{"type": "string", "enum": []string{"invoke", "set_text", "select", "toggle"}}, "text": map[string]any{"type": "string", "maxLength": 4096}}}}}},
		map[string]any{"name": "update_prepare", "description": "Stage a single-use Windows update batch; requires Cloudflare Access authentication. Submit exact UTF-8 CRLF content, 32-hex update ID in first REM line, SHA-256 of bytes. Does not execute.", "inputSchema": map[string]any{"type": "object", "properties": map[string]any{"update_id": map[string]any{"type": "string"}, "sha256": map[string]any{"type": "string"}, "content": map[string]any{"type": "string"}}, "required": []string{"update_id", "sha256", "content"}}},
		map[string]any{"name": "update_execute", "description": "Rehash and start a previously staged batch as separate Windows process; requires Cloudflare Access authentication. Does not report completed deployment.", "inputSchema": map[string]any{"type": "object", "properties": map[string]any{"update_id": map[string]any{"type": "string"}, "sha256": map[string]any{"type": "string"}}, "required": []string{"update_id", "sha256"}}},
		map[string]any{"name": "diagnostic_status", "description": "Read bounded non-secret structured status of a diagnostic batch by 32-hex ID; no arbitrary file or log reading", "inputSchema": map[string]any{"type": "object", "properties": map[string]any{"id": map[string]any{"type": "string"}}, "required": []string{"id"}}},
		map[string]any{"name": "log_list", "description": "List files under the restricted Jarvis Logs directory (maximum 300 records); Cloudflare Access required.", "inputSchema": map[string]any{"type": "object", "properties": map[string]any{}}},
		map[string]any{"name": "log_read", "description": "Read the last N bytes of one .log within Jarvis Logs; no arbitrary path access. Cloudflare Access required.", "inputSchema": map[string]any{"type": "object", "properties": map[string]any{"path": map[string]any{"type": "string"}, "tail_bytes": map[string]any{"type": "integer", "minimum": 1, "maximum": 32768}}, "required": []string{"path"}}},
		map[string]any{"name": "task_stop", "description": "Request cancellation of the current diagnostic task", "inputSchema": map[string]any{"type": "object", "properties": map[string]any{}}},
	}}
}
func response(id json.RawMessage, result any, e *RPCError) RPCResponse {
	if len(id) == 0 {
		id = json.RawMessage("null")
	}
	return RPCResponse{JSONRPC: "2.0", ID: id, Result: result, Error: e}
}
func handler(a *Agent, token string, access *AccessVerifier) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" {
			http.Error(w, "method not allowed", 405)
			return
		}
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		_ = json.NewEncoder(w).Encode(map[string]any{"ok": true, "version": version})
	})
	mux.HandleFunc("/mcp", func(w http.ResponseWriter, r *http.Request) {
		log.Printf("MCP request received method=%s access_assertion=%t", r.Method, r.Header.Get("Cf-Access-Jwt-Assertion") != "")
		// Stateless Streamable HTTP: GET streams and session IDs are optional.
		// Clients receive 405 on GET rather than an HTML error page.
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("Access-Control-Expose-Headers", "MCP-Protocol-Version")
		if r.Method != http.MethodPost && r.Method != http.MethodGet && r.Method != http.MethodDelete {
			w.Header().Set("Allow", "POST, GET, DELETE")
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		// Requests from a browser with an untrusted Origin must not be allowed to
		// reach the local endpoint; non-browser MCP clients typically send none.
		if origin := r.Header.Get("Origin"); origin != "" {
			if origin != "http://127.0.0.1:8765" && origin != "http://localhost:8765" {
				http.Error(w, "invalid origin", http.StatusForbidden)
				return
			}
		}
		h := r.Header.Get("Authorization")
		expected := "Bearer " + token
		validLocal := len(token) >= 24 && subtle.ConstantTimeCompare([]byte(h), []byte(expected)) == 1
		validAccess := false
		authReason := "missing local bearer token or Cloudflare assertion"
		if !validLocal && access != nil {
			jwt := r.Header.Get("Cf-Access-Jwt-Assertion")
			if jwt != "" {
				if err := access.Verify(r.Context(), jwt); err == nil {
					validAccess = true
				} else {
					authReason = err.Error()
				}
			}
		}
		if !validLocal && !validAccess {
			log.Printf("MCP authentication rejected method=%s path=%s access_enabled=%t assertion_present=%t reason=%s", r.Method, r.URL.Path, access != nil, r.Header.Get("Cf-Access-Jwt-Assertion") != "", authReason)
			w.Header().Set("WWW-Authenticate", `Bearer realm="jarvis-mcp"`)
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		if r.Method == http.MethodGet || r.Method == http.MethodDelete {
			w.Header().Set("Allow", "POST")
			http.Error(w, "stateless server: no GET stream or session deletion", http.StatusMethodNotAllowed)
			return
		}
		if !strings.HasPrefix(strings.ToLower(r.Header.Get("Content-Type")), "application/json") {
			http.Error(w, "Content-Type must be application/json", http.StatusUnsupportedMediaType)
			return
		}
		accept := r.Header.Get("Accept")
		if accept != "" && (!strings.Contains(accept, "application/json") || !strings.Contains(accept, "text/event-stream")) {
			http.Error(w, "Accept must include application/json and text/event-stream", http.StatusNotAcceptable)
			return
		}
		// A 1 MiB file can need 4/3 base64 or up to 6x JSON escaping.
		r.Body = http.MaxBytesReader(w, r.Body, 8*1024*1024)
		data, err := io.ReadAll(r.Body)
		if err != nil {
			http.Error(w, "request too large", http.StatusRequestEntityTooLarge)
			return
		}
		var q RPCRequest
		if err := json.Unmarshal(data, &q); err != nil || q.JSONRPC != "2.0" || q.Method == "" {
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusBadRequest)
			_ = json.NewEncoder(w).Encode(response(nil, nil, &RPCError{Code: -32700, Message: "invalid JSON-RPC request"}))
			return
		}
		if len(q.ID) == 0 {
			// MCP notifications are accepted without a response body.
			w.WriteHeader(http.StatusAccepted)
			return
		}
		var res RPCResponse
		switch q.Method {
		case "initialize":
			var p struct {
				ProtocolVersion string `json:"protocolVersion"`
			}
			if json.Unmarshal(q.Params, &p) != nil {
				res = response(q.ID, nil, &RPCError{Code: -32602, Message: "invalid initialization parameters"})
			} else {
				protocol := "2025-03-26"
				if p.ProtocolVersion == "2025-06-18" || p.ProtocolVersion == "2025-11-25" {
					protocol = p.ProtocolVersion
				}
				res = response(q.ID, map[string]any{"protocolVersion": protocol, "capabilities": map[string]any{"tools": map[string]any{"listChanged": false}}, "serverInfo": map[string]string{"name": "jarvis-windows-agent", "version": version}}, nil)
			}
		case "ping":
			res = response(q.ID, map[string]any{}, nil)
		case "tools/list":
			res = response(q.ID, toolsList(), nil)
		case "tools/call":
			var p struct {
				Name      string          `json:"name"`
				Arguments json.RawMessage `json:"arguments"`
			}
			if err := json.Unmarshal(q.Params, &p); err != nil || p.Name == "" {
				res = response(q.ID, nil, &RPCError{Code: -32602, Message: "invalid parameters"})
			} else {
				if (p.Name == "update_prepare" || p.Name == "update_execute" || p.Name == "log_list" || p.Name == "log_read" || p.Name == "diagnostic_status") && !validAccess {
					res = response(q.ID, toolResult(map[string]string{"error": "Cloudflare Access authentication required for update tools"}, true), nil)
				} else {
					res = response(q.ID, a.invoke(p.Name, p.Arguments), nil)
				}
			}
		default:
			res = response(q.ID, nil, &RPCError{Code: -32601, Message: "method not found"})
		}
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.Header().Set("MCP-Protocol-Version", "2025-03-26")
		_ = json.NewEncoder(w).Encode(res)
	})

	return mux
}
func main() {
	listen := flag.String("listen", "127.0.0.1:8765", "listen address (localhost by default)")
	flag.Parse()
	if err := loadLocalConfig(); err != nil {
		log.Fatalf("Configuration error: %v", err)
	}
	token := os.Getenv("JARVIS_MCP_TOKEN")
	teamDomain := strings.TrimSuffix(os.Getenv("JARVIS_CF_TEAM_DOMAIN"), "/")
	audience := os.Getenv("JARVIS_CF_APP_AUD")
	if (teamDomain == "") != (audience == "") {
		fmt.Fprintln(os.Stderr, "ERROR: both JARVIS_CF_TEAM_DOMAIN and JARVIS_CF_APP_AUD are required")
		os.Exit(2)
	}
	if len(token) < 24 && teamDomain == "" {

		fmt.Fprintln(os.Stderr, "ERROR: set JARVIS_MCP_TOKEN to a random secret of at least 24 characters before starting")
		os.Exit(2)
	}
	if host, _, e := net.SplitHostPort(*listen); e != nil || (host != "127.0.0.1" && host != "localhost" && host != "::1") {
		fmt.Fprintln(os.Stderr, "ERROR: prototype only permits localhost binding. Use a trusted HTTPS reverse proxy / tunnel.")
		os.Exit(2)
	}
	startLogMaintenance()
	a := &Agent{}
	if _, err := a.getRuntime(); err != nil {
		log.Fatalf("Runtime initialization failed: %v", err)
	}
	defer a.closeRuntime()
	var access *AccessVerifier
	if teamDomain != "" {
		u, err := url.Parse(teamDomain)
		if err != nil || u.Scheme != "https" || !strings.HasSuffix(u.Hostname(), ".cloudflareaccess.com") || u.Path != "" || u.RawQuery != "" || u.Fragment != "" || u.User != nil {
			log.Fatal("Invalid JARVIS_CF_TEAM_DOMAIN: expected https://<team>.cloudflareaccess.com")
		}
		access = &AccessVerifier{TeamDomain: teamDomain, Audience: audience}
	}
	server := &http.Server{Addr: *listen, Handler: handler(a, token, access), ReadHeaderTimeout: 5 * time.Second}
	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt, syscall.SIGTERM)
	go func() {
		<-c
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		server.Shutdown(ctx)
	}()
	exe, _ := os.Executable()
	log.Printf("Jarvis MCP %s listening on http://%s/mcp (binary: %s)", version, *listen, filepath.Base(exe))
	log.Printf("Authentication configured: local_bearer=%t cloudflare_access=%t", len(token) >= 24, access != nil)
	log.Println("Local-only endpoint: configure authenticated HTTPS tunnel before attaching ChatGPT")
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}
