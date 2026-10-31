package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
)

type runtimeRequest struct {
	Operation   string `json:"operation,omitempty"`
	MachineID   string `json:"machine_id,omitempty"`
	Project     string `json:"project,omitempty"`
	RequestID   string `json:"request_id,omitempty"`
	Path        string `json:"path,omitempty"`
	Destination string `json:"destination,omitempty"`
	Expected    string `json:"expected_sha256,omitempty"`
	Content     string `json:"content,omitempty"`
	Encoding    string `json:"encoding,omitempty"`
	Query       string `json:"query,omitempty"`
	Recursive   bool   `json:"recursive,omitempty"`
	Offset      int    `json:"offset,omitempty"`
	Limit       int    `json:"limit,omitempty"`
	RestoreID   string `json:"restore_id,omitempty"`
	Profile     string `json:"profile,omitempty"`
	Timeout     int    `json:"timeout_seconds,omitempty"`
	JobID       string `json:"job_id,omitempty"`
	ThreadID    string `json:"thread_id,omitempty"`
	TurnID      string `json:"turn_id,omitempty"`
	Text        string `json:"text,omitempty"`
	Cursor      string `json:"cursor,omitempty"`
	After       int64  `json:"after,omitempty"`
	ApprovalID  string `json:"approval_id,omitempty"`
	Decision    string `json:"decision,omitempty"`
	Answers     map[string]struct {
		Answers []string `json:"answers"`
	} `json:"answers,omitempty"`
	Monitor  int             `json:"monitor,omitempty"`
	Region   []int           `json:"region,omitempty"`
	MaxWidth int             `json:"max_width,omitempty"`
	Quality  int             `json:"quality,omitempty"`
	DeviceID string          `json:"device_id,omitempty"`
	Call     json.RawMessage `json:"call,omitempty"`
}
type runtimeTool struct {
	Name, Description string
	Fields, Required  []string
	ReadOnly          bool
}

var runtimeCatalog = []runtimeTool{
	{"machine_info", "Read stable computer identity, available tool paths and runtime limits. Call first; all other local operations require its machine_id.", nil, nil, true},
	{"project_catalog", "List usable project paths and stable project IDs under the authorized root.", nil, nil, true},
	{"file_list", "List project files/directories with pagination; protected paths and links are excluded.", []string{"project", "path", "offset", "limit", "recursive"}, []string{"project"}, true},
	{"file_read", "Read a bounded regular file and its SHA-256. UTF-8 or base64; no credentials/configuration access.", []string{"project", "path", "encoding"}, []string{"project", "path"}, true},
	{"file_search", "Find filenames or UTF-8 literal text in a project; bounded results and scan, protected paths excluded.", []string{"project", "path", "query", "offset", "limit"}, []string{"project", "query"}, true},
	{"file_write", "Create/replace a file using expected_sha256 (missing for creation). Private backup and request receipt; never silently overwrite changes.", []string{"project", "path", "content", "encoding", "expected_sha256", "request_id"}, []string{"project", "path", "content", "expected_sha256", "request_id"}, false},
	{"directory_create", "Create a directory tree without overwriting files; journal for reversible empty-directory removal.", []string{"project", "path", "request_id"}, []string{"project", "path", "request_id"}, false},
	{"path_move", "Rename or move a file OR directory inside one project, same filesystem, destination must not exist; expected_sha256 required from file_list/file_read.", []string{"project", "path", "destination", "expected_sha256", "request_id"}, []string{"project", "path", "destination", "expected_sha256", "request_id"}, false},
	{"file_restore", "Restore a recorded write or undo a move/create, only if the current fingerprint matches the receipt. No silent overwrites.", []string{"restore_id", "request_id"}, []string{"restore_id", "request_id"}, false},
	{"request_status", "Read a durable receipt, including uncertain outcomes; do not retry an uncertain operation automatically.", []string{"request_id"}, []string{"request_id"}, true},
	{"command_start", "Start an asynchronous fixed development profile in a project. No shell strings; output bounded, process-tree timeout/cancellation. Trusted project code executes with the agent's OS rights.", []string{"project", "profile", "timeout_seconds", "request_id"}, []string{"project", "profile", "request_id"}, false},
	{"job_status", "Read durable job status and current bounded output.", []string{"job_id"}, []string{"job_id"}, true},
	{"job_list", "List persistent jobs, including interrupted jobs from older processes.", []string{"offset", "limit"}, nil, true},
	{"job_cancel", "Cancel the selected running job and its process tree.", []string{"job_id", "request_id"}, []string{"job_id", "request_id"}, false},
	{"screen_capture", "Capture current interactive desktop as a direct MCP JPEG image, with monitor/region, original dimensions, origin, scale and timestamp. Locked/secure desktop refused; never use image coordinates alone for actions.", []string{"monitor", "region", "max_width", "quality"}, nil, true},
	{"codex_status", "Read current remaining quota, credit balance and reset times when the user asks how much usage or credits remain. Also returns account/eligibility status. Read-only, no model call; unknown data stays null.", nil, nil, true},
	{"codex_threads", "List local Codex sessions for this exact project, with cursor. Visibility depends on the installed Codex profile; does not list arbitrary ChatGPT chats.", []string{"project", "cursor", "limit"}, []string{"project"}, true},
	{"codex_read", "Read a local session only when its canonical working directory equals this project.", []string{"project", "thread_id"}, []string{"project", "thread_id"}, true},
	{"codex_start", "Create and save a Jarvis-owned Codex session in this project. ChatGPT-only, normal tier, sandbox and approval policies fixed; no inference until codex_turn.", []string{"project", "request_id"}, []string{"project", "request_id"}, false},
	{"codex_resume", "Attach an existing idle local session to this project; verifies cwd and active state, never steals an active turn.", []string{"project", "thread_id", "request_id"}, []string{"project", "thread_id", "request_id"}, false},
	{"codex_turn", "Start one turn in an attached session after ChatGPT/plan/zero-credit quota checks. No paid fallback, arbitrary provider/model or auto-replay.", []string{"project", "thread_id", "text", "request_id"}, []string{"project", "thread_id", "text", "request_id"}, false},
	{"codex_steer", "Append instructions to the exact active turn, guarded by turn_id.", []string{"project", "thread_id", "turn_id", "text", "request_id"}, []string{"project", "thread_id", "turn_id", "text", "request_id"}, false},
	{"codex_interrupt", "Interrupt the specified Codex turn; verify final event before claiming stopped.", []string{"project", "thread_id", "turn_id", "request_id"}, []string{"project", "thread_id", "turn_id", "request_id"}, false},
	{"codex_events", "Read durable bounded Codex events and pending approvals using the sequence cursor. After turn/completed, wait for jarvis/usage for that turn: final account-wide quota/credits snapshot or explicit unavailability.", []string{"project", "thread_id", "after", "limit"}, []string{"project", "thread_id"}, true},
	{"codex_respond", "Answer one exact pending approval/user-input request. No session-wide grants; approvals require explicit user authorization for the presented action.", []string{"project", "thread_id", "approval_id", "decision", "answers", "request_id"}, []string{"project", "thread_id", "approval_id", "request_id"}, false},
	{"device_list", "List this computer plus locally configured trusted peers; offline status is explicit. Does not scan the network.", nil, nil, true},
	{"device_call", "Forward one allowlisted operation to an explicitly configured peer, verifying its stable identity before dispatch. Never forwards update batches or nested routing.", []string{"device_id", "call"}, []string{"device_id", "call"}, false},
}

func isRuntimeTool(name string) bool {
	for _, t := range runtimeCatalog {
		if t.Name == name {
			return true
		}
	}
	return false
}
func toolsList() any {
	v := legacyToolsList().(map[string]any)
	ts := v["tools"].([]any)
	for _, t := range runtimeCatalog {
		props := map[string]any{}
		required := append([]string{}, t.Required...)
		if t.Name != "machine_info" {
			props["machine_id"] = map[string]any{"type": "string"}
			required = append(required, "machine_id")
		}
		for _, f := range t.Fields {
			typ := "string"
			switch f {
			case "offset", "limit", "timeout_seconds", "after", "monitor", "max_width", "quality":
				typ = "integer"
			case "recursive":
				typ = "boolean"
			case "region":
				typ = "array"
			case "call", "answers":
				typ = "object"
			}
			s := map[string]any{"type": typ}
			if f == "region" {
				s["items"] = map[string]string{"type": "integer"}
				s["minItems"] = 4
				s["maxItems"] = 4
			}
			if f == "profile" {
				s["enum"] = commandProfiles
			}
			props[f] = s
		}
		ts = append(ts, map[string]any{"name": t.Name, "description": t.Description, "inputSchema": map[string]any{"type": "object", "properties": props, "required": required, "additionalProperties": false}, "annotations": map[string]any{"readOnlyHint": t.ReadOnly, "destructiveHint": !t.ReadOnly, "idempotentHint": t.ReadOnly}})
	}
	// Compatibility route for a host that has cached the previous MCP catalog.
	for _, t := range ts {
		m := t.(map[string]any)
		if m["name"] == "task_start" {
			s := m["inputSchema"].(map[string]any)
			p := s["properties"].(map[string]any)
			p["request"] = map[string]any{"type": "object", "description": "For operation=runtime: the complete named runtime request, including operation and machine_id."}
			op := p["operation"].(map[string]any)
			op["enum"] = append(op["enum"].([]string), "runtime")
			m["description"] = "Run legacy diagnostic/Go/desktop operations, or operation=runtime with a nested request for the current runtime catalog. Read machine_info first."
		}
	}
	v["tools"] = ts
	return v
}
func decodeRuntime(name string, b json.RawMessage) (runtimeRequest, error) {
	var p runtimeRequest
	d := json.NewDecoder(bytes.NewReader(b))
	d.DisallowUnknownFields()
	if e := d.Decode(&p); e != nil {
		return p, errors.New("invalid runtime request or unknown field")
	}
	var trailing any
	if d.Decode(&trailing) != io.EOF {
		return p, errors.New("unexpected trailing JSON")
	}
	if name != "" {
		if p.Operation != "" && p.Operation != name {
			return p, errors.New("operation mismatch")
		}
		p.Operation = name
	}
	if !isRuntimeTool(p.Operation) {
		return p, errors.New("unknown runtime operation")
	}
	var fields map[string]json.RawMessage
	if json.Unmarshal(b, &fields) != nil || fields == nil {
		return p, errors.New("request must be a JSON object")
	}
	for _, spec := range runtimeCatalog {
		if spec.Name != p.Operation {
			continue
		}
		allowed := map[string]bool{"operation": true, "machine_id": true}
		for _, key := range spec.Fields {
			allowed[key] = true
		}
		for key, value := range fields {
			if !allowed[key] || string(value) == "null" {
				return p, fmt.Errorf("unsupported or null field for %s: %s", p.Operation, key)
			}
		}
		for _, key := range spec.Required {
			if _, ok := fields[key]; !ok {
				return p, fmt.Errorf("required field missing: %s", key)
			}
		}
	}
	if p.Limit < 0 || p.Limit > 200 || p.Offset < 0 || p.After < 0 {
		return p, errors.New("invalid pagination")
	}
	p.Path = filepath.FromSlash(p.Path)
	p.Destination = filepath.FromSlash(p.Destination)
	return p, nil
}
func (a *Agent) invokeRuntime(name string, raw json.RawMessage) any {
	p, e := decodeRuntime(name, raw)
	if e != nil {
		return toolResult(map[string]any{"error": e.Error()}, true)
	}
	r, e := a.getRuntime()
	if e != nil {
		return toolResult(map[string]any{"error": e.Error()}, true)
	}
	if p.Operation != "machine_info" && p.MachineID != r.MachineID {
		return toolResult(map[string]any{"error": "machine_id mismatch; read machine_info and select the intended computer"}, true)
	}
	if p.Operation == "screen_capture" {
		v, e := screenCapture(p)
		if e != nil {
			return toolResult(map[string]any{"error": e.Error()}, true)
		}
		return v
	}
	v, e := r.call(p)
	if e != nil {
		return toolResult(map[string]any{"error": e.Error(), "request_id": p.RequestID, "machine_id": r.MachineID}, true)
	}
	if p.Operation == "device_call" {
		return v
	}
	b, _ := json.Marshal(v)
	var obj map[string]any
	if json.Unmarshal(b, &obj) == nil && obj != nil {
		obj["machine_id"] = r.MachineID
		obj["operation"] = p.Operation
		if p.Project != "" {
			obj["project"] = p.Project
		}
		v = obj
	}
	return toolResult(v, false)
}
func (r *Runtime) call(p runtimeRequest) (any, error) {
	switch p.Operation {
	case "machine_info":
		return r.machineInfo(), nil
	case "project_catalog":
		return r.projectCatalog()
	case "file_list", "file_read", "file_search":
		return r.filesRead(p)
	case "file_write", "directory_create", "path_move", "file_restore":
		return r.mutation(p, func(x *Receipt) (any, error) { return r.filesMutate(p, x) })
	case "request_status":
		if !requestIDPattern.MatchString(p.RequestID) {
			return nil, errors.New("invalid request_id")
		}
		r.mu.Lock()
		defer r.mu.Unlock()
		b, e := os.ReadFile(filepath.Join(r.dir, "requests", p.RequestID+".json"))
		if e != nil {
			return nil, e
		}
		var v Receipt
		e = json.Unmarshal(b, &v)
		return v, e
	case "command_start":
		return r.mutation(p, func(x *Receipt) (any, error) { return r.startCommand(p) })
	case "job_list", "job_status", "job_cancel":
		return r.jobCall(p)
	case "device_list", "device_call":
		return r.deviceCall(p)
	default:
		if strings.HasPrefix(p.Operation, "codex_") {
			return r.codexCall(p)
		}
	}
	return nil, errors.New("unsupported runtime operation")
}
func (r *Runtime) machineInfo() any {
	host, _ := os.Hostname()
	tools := map[string]any{}
	for _, n := range []string{"go", "git", "dotnet", "cmake", "MSBuild", "cl"} {
		p, e := exec.LookPath(n)
		tools[n] = map[string]any{"available": e == nil, "path": p}
	}
	return map[string]any{"machine_id": r.MachineID, "name": host, "version": version, "os": runtime.GOOS, "arch": runtime.GOARCH, "tools": tools, "profiles": commandProfiles, "capabilities": runtimeCatalogNames(), "limits": map[string]any{"file_bytes": maxFileBytes, "command_output_bytes": maxJobOutput, "command_timeout_seconds": 600, "screen_locked": "refused", "paid_fallback": false}, "checked_at": utcNow()}
}
func runtimeCatalogNames() []string {
	v := []string{}
	for _, t := range runtimeCatalog {
		v = append(v, t.Name)
	}
	return v
}
func (r *Runtime) projectCatalog() (any, error) {
	root, e := projectsRoot()
	if e != nil {
		return nil, e
	}
	dirs, e := os.ReadDir(root)
	if e != nil {
		return nil, e
	}
	names := []string{"."}
	for _, d := range dirs {
		if d.IsDir() && !protectedPart(d.Name()) {
			names = append(names, d.Name())
		}
	}
	sort.Strings(names)
	out := []any{}
	for _, n := range names {
		p, e := resolveProject(n)
		if e != nil {
			continue
		}
		id := digest([]byte(r.MachineID + "\x00" + strings.ToLower(p)))
		out = append(out, map[string]any{"project": n, "project_id": id[:24], "path": p})
	}
	return map[string]any{"machine_id": r.MachineID, "projects": out, "root": root}, nil
}
func (r *Runtime) projectRoot(project string) (*os.Root, error) {
	dir, e := resolveProject(project)
	if e != nil {
		return nil, e
	}
	// Reject aliasing an authorized directory through a link or junction.
	root, e := projectsRoot()
	if e != nil {
		return nil, e
	}
	rel, e := filepath.Rel(root, dir)
	if e != nil {
		return nil, e
	}
	if e = validRelative(rel, true); e != nil {
		return nil, e
	}
	input := filepath.Join(root, project)
	if !samePath(input, dir) {
		return nil, errors.New("project aliases are not accepted")
	}
	if protectedAbsolute(dir, r.dir) {
		return nil, errors.New("private runtime/configuration directory is not a project")
	}
	return os.OpenRoot(dir)
}
func protectedAbsolute(path, state string) bool {
	for _, p := range []string{state, filepath.Join(os.Getenv("JARVIS_HOME"), "Config"), filepath.Join(os.Getenv("JARVIS_HOME"), "Backups")} {
		if p == "" || p == "Config" || p == "Backups" {
			continue
		}
		rel, e := filepath.Rel(p, path)
		if e == nil && rel != ".." && !strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
			return true
		}
	}
	return false
}
func requireProject(p runtimeRequest) error {
	if strings.TrimSpace(p.Project) == "" {
		return fmt.Errorf("project is required")
	}
	return nil
}
