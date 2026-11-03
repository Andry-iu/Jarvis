package main

import (
	"encoding/json"
	"errors"
	"log"
	"os"
	"path/filepath"
	"sort"
	"sync"
)

// Only names and aggregate counts are stored; never arguments, results or IDs.
type toolCounter struct {
	Total     uint64 `json:"total"`
	Succeeded uint64 `json:"succeeded"`
	Failed    uint64 `json:"failed"`
}
type toolUsageState struct {
	Format    int                    `json:"format"`
	StartedAt string                 `json:"started_at"`
	UpdatedAt string                 `json:"updated_at"`
	Tools     map[string]toolCounter `json:"tools"`
}
type toolUsageStore struct {
	mu      sync.Mutex
	path    string
	state   toolUsageState
	problem string
}

func (a *Agent) usageStore() (*toolUsageStore, error) {
	a.usageMu.Lock()
	defer a.usageMu.Unlock()
	if a.usage != nil {
		return a.usage, nil
	}
	home := os.Getenv("JARVIS_HOME")
	if home == "" || !filepath.IsAbs(home) {
		return nil, errors.New("usage home unavailable")
	}
	dir := filepath.Join(home, "State")
	if err := os.MkdirAll(dir, 0700); err != nil {
		return nil, err
	}
	if err := protectRuntimeDirectory(dir); err != nil {
		return nil, err
	}
	s := &toolUsageStore{path: filepath.Join(dir, "tool-usage.json")}
	b, err := os.ReadFile(s.path)
	if os.IsNotExist(err) {
		s.state = toolUsageState{Format: 1, StartedAt: utcNow(), Tools: map[string]toolCounter{}}
	} else if err != nil {
		return nil, err
	} else if json.Unmarshal(b, &s.state) != nil || s.state.Format != 1 || s.state.StartedAt == "" || s.state.Tools == nil {
		return nil, errors.New("invalid usage state; preserved without reset")
	}
	for _, v := range s.state.Tools {
		if v.Succeeded > v.Total || v.Failed > v.Total-v.Succeeded {
			return nil, errors.New("invalid usage counts; preserved without reset")
		}
	}
	a.usage = s
	return s, nil
}
func (s *toolUsageStore) save() error {
	s.state.UpdatedAt = utcNow()
	err := atomicJSON(s.path, s.state)
	if err != nil {
		s.problem = "statistics persistence failed; counts may be incomplete"
		log.Print(s.problem)
	}
	return err
}
func (s *toolUsageStore) begin(name string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	v := s.state.Tools[name]
	v.Total++
	s.state.Tools[name] = v
	return s.save()
}
func (s *toolUsageStore) finish(name string, failed bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	v := s.state.Tools[name]
	if failed {
		v.Failed++
	} else {
		v.Succeeded++
	}
	s.state.Tools[name] = v
	_ = s.save()
}
func usageName(name string, args json.RawMessage) string {
	if name == "task_start" {
		var p struct {
			Operation string          `json:"operation"`
			Request   json.RawMessage `json:"request"`
		}
		if json.Unmarshal(args, &p) == nil && p.Operation == "runtime" {
			var nested struct {
				Operation string `json:"operation"`
			}
			if json.Unmarshal(p.Request, &nested) == nil && isRuntimeTool(nested.Operation) {
				return nested.Operation
			}
		}
	}
	if isRuntimeTool(name) {
		return name
	}
	for _, raw := range legacyToolsList().(map[string]any)["tools"].([]any) {
		if raw.(map[string]any)["name"] == name {
			return name
		}
	}
	return "_unknown"
}
func toolFailed(result any) bool {
	b, err := json.Marshal(result)
	if err != nil {
		return true
	}
	var v struct {
		IsError bool `json:"isError"`
	}
	if json.Unmarshal(b, &v) != nil {
		return true
	}
	return v.IsError
}
func (a *Agent) invoke(name string, args json.RawMessage) (result any) {
	s, err := a.usageStore()
	if err != nil {
		// Telemetry must never turn a completed mutation into a retryable failure.
		log.Print("tool usage unavailable")
		return a.invokeUncounted(name, args)
	}
	logical := usageName(name, args)
	if err = s.begin(logical); err != nil {
		return a.invokeUncounted(name, args)
	}
	defer func() { s.finish(logical, result == nil || toolFailed(result)) }()
	return a.invokeUncounted(name, args)
}
func (a *Agent) toolUsage(query string) (any, error) {
	s, err := a.usageStore()
	if err != nil {
		return nil, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	type row struct {
		Name string `json:"name"`
		toolCounter
		Unfinished uint64 `json:"unfinished"`
	}
	rows := []row{}
	// Include unused catalog tools as zeroes.
	names := map[string]bool{}
	for _, t := range runtimeCatalog {
		names[t.Name] = true
	}
	for _, t := range legacyToolsList().(map[string]any)["tools"].([]any) {
		names[t.(map[string]any)["name"].(string)] = true
	}
	for n := range s.state.Tools {
		names[n] = true
	}
	for n := range names {
		if query != "" && n != query {
			continue
		}
		v := s.state.Tools[n]
		rows = append(rows, row{n, v, v.Total - v.Succeeded - v.Failed})
	}
	sort.Slice(rows, func(i, j int) bool {
		if rows[i].Total == rows[j].Total {
			return rows[i].Name < rows[j].Name
		}
		return rows[i].Total > rows[j].Total
	})
	if query != "" && len(rows) == 0 {
		return nil, errors.New("unknown tool name")
	}
	return map[string]any{"started_at": s.state.StartedAt, "updated_at": s.state.UpdatedAt, "tools": rows,
		"persistence_warning": s.problem, "semantics": "authenticated tool invocations; success means immediate tool response, not completion of queued work; unfinished includes active calls or interrupted calls; current tool_usage includes itself before completion"}, nil
}
