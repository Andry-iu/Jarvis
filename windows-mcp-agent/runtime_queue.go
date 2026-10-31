package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"
)

type queueEntry struct {
	ID         string         `json:"id"`
	Project    string         `json:"project"`
	Resource   string         `json:"resource"`
	Label      string         `json:"label"`
	Status     string         `json:"status"`
	CreatedAt  string         `json:"created_at"`
	FinishedAt string         `json:"finished_at,omitempty"`
	JobID      string         `json:"job_id,omitempty"`
	TurnID     string         `json:"turn_id,omitempty"`
	Error      string         `json:"error,omitempty"`
	DependsOn  []string       `json:"depends_on,omitempty"`
	Request    runtimeRequest `json:"request"`
}
type projectQueue struct {
	Version int               `json:"version"`
	Entries []*queueEntry     `json:"entries"`
	Paused  map[string]string `json:"paused"`
}

func queuePathsOverlap(a, b string) bool {
	a, b = filepath.Clean(a), filepath.Clean(b)
	return samePath(a, b) || strings.HasPrefix(strings.ToLower(a), strings.ToLower(b)+string(filepath.Separator)) || strings.HasPrefix(strings.ToLower(b), strings.ToLower(a)+string(filepath.Separator))
}
func (r *Runtime) queueSaveLocked() error {
	if err := atomicJSON(filepath.Join(r.dir, "queue.json"), r.queue); err != nil {
		r.queueBroken = true
		return errors.New("queue persistence failed; processing stopped; inspect state before retry")
	}
	return nil
}
func (r *Runtime) initQueue() error {
	r.queue = &projectQueue{Version: 1, Entries: []*queueEntry{}, Paused: map[string]string{}}
	b, err := os.ReadFile(filepath.Join(r.dir, "queue.json"))
	if err == nil {
		if json.Unmarshal(b, r.queue) != nil || r.queue.Version != 1 || r.queue.Paused == nil {
			return errors.New("invalid persisted project queue")
		}
	} else if !os.IsNotExist(err) {
		return err
	}
	if len(r.queue.Entries) > 5000 {
		return errors.New("queue history limit exceeded")
	}
	ids := map[string]bool{}
	for _, e := range r.queue.Entries {
		if e == nil || !requestIDPattern.MatchString(e.ID) || ids[e.ID] || !filepath.IsAbs(e.Resource) || (e.Request.Operation != "command_start" && e.Request.Operation != "codex_turn") {
			return errors.New("invalid queue entry; no automatic recovery")
		}
		switch e.Status {
		case "queued", "starting", "running", "completed", "failed", "cancelled", "interrupted", "uncertain", "timed_out", "persistence_failed":
		default:
			return errors.New("unknown queue state")
		}
		if e.Project != e.Request.Project || e.Request.MachineID != r.MachineID {
			return errors.New("persisted queue project/computer mismatch")
		}
		ids[e.ID] = true
		if e.Status == "running" || e.Status == "starting" {
			e.Status = "uncertain"
			e.Error = "agent restarted; inspect outcome before resuming; never replay this entry"
			r.queue.Paused[e.Resource] = e.Error
		}
		if e.Status == "queued" {
			r.queue.Paused[e.Resource] = "agent restarted; explicit queue_resume required"
		}
	}
	if err = r.queueSaveLocked(); err != nil {
		return err
	}
	r.queueStop = make(chan struct{})
	go r.queueLoop(r.queueStop)
	return nil
}
func (r *Runtime) queueFindLocked(id string) *queueEntry {
	for _, e := range r.queue.Entries {
		if e.ID == id {
			return e
		}
	}
	return nil
}
func (r *Runtime) queuePausedLocked(resource string) string {
	for path, reason := range r.queue.Paused {
		if queuePathsOverlap(path, resource) {
			return reason
		}
	}
	return ""
}
func (r *Runtime) queueBusyLocked(resource string) bool {
	for _, e := range r.queue.Entries {
		if (e.Status == "starting" || e.Status == "running" || e.Status == "uncertain") && queuePathsOverlap(e.Resource, resource) {
			return true
		}
	}
	return false
}
func (r *Runtime) projectResource(project string) (string, error) {
	root, err := r.projectRoot(project)
	if err != nil {
		return "", err
	}
	defer root.Close()
	return filepath.Clean(root.Name()), nil
}
func (r *Runtime) enqueueOperation(p runtimeRequest, label string, deps []string) (any, error) {
	return r.mutation(p, func(_ *Receipt) (any, error) { return r.enqueueLocked(p, label, deps) })
}
func (r *Runtime) enqueueLocked(p runtimeRequest, label string, deps []string) (any, error) {
	if r.queueBroken || r.closing {
		return nil, errors.New("queue unavailable")
	}
	if len(r.queue.Entries) >= 5000 {
		return nil, errors.New("queue history limit reached; archive required")
	}
	resource, err := r.projectResource(p.Project)
	if err != nil {
		return nil, err
	}
	if p.Operation == "command_start" {
		if p.Timeout < 0 || p.Timeout > 600 {
			return nil, errors.New("invalid timeout")
		}
		if _, _, err = commandSpec(p.Profile, resource, newID()); err != nil {
			return nil, err
		}
	} else if p.Operation == "codex_turn" {
		if len(p.Text) == 0 || len(p.Text) > 32768 {
			return nil, errors.New("text must contain 1-32768 bytes")
		}
		if _, err = r.codexLink(p, resource); err != nil {
			return nil, err
		}
	} else {
		return nil, errors.New("only fixed commands and attached Codex turns may be queued")
	}
	if len(label) > 160 || len(deps) > 100 {
		return nil, errors.New("label/dependencies too large")
	}
	for _, id := range deps {
		if r.queueFindLocked(id) == nil {
			return nil, fmt.Errorf("unknown dependency %s", id)
		}
	}
	if label == "" {
		label = p.Profile
		if p.Operation == "codex_turn" {
			label = "Codex: " + strings.Join(strings.Fields(p.Text), " ")
			if len(label) > 160 {
				label = label[:160]
			}
		}
	}
	e := &queueEntry{ID: newID(), Project: p.Project, Resource: resource, Label: label, Status: "queued", CreatedAt: utcNow(), DependsOn: append([]string{}, deps...), Request: p}
	r.queue.Entries = append(r.queue.Entries, e)
	if err = r.queueSaveLocked(); err != nil {
		return nil, err
	}
	return r.queueViewLocked(e), nil
}
func (r *Runtime) queueViewLocked(e *queueEntry) map[string]any {
	position := 0
	for _, v := range r.queue.Entries {
		if samePath(v.Resource, e.Resource) && v.Status == "queued" {
			position++
		}
		if v.ID == e.ID {
			break
		}
	}
	if e.Status != "queued" {
		position = 0
	}
	return map[string]any{"queue_id": e.ID, "project": e.Project, "label": e.Label, "status": e.Status, "position": position, "job_id": e.JobID, "thread_id": e.Request.ThreadID, "turn_id": e.TurnID, "created_at": e.CreatedAt, "finished_at": e.FinishedAt, "error": e.Error, "depends_on": e.DependsOn, "paused_reason": r.queuePausedLocked(e.Resource)}
}
func (r *Runtime) queueListLocked(p runtimeRequest) (any, error) {
	resource := ""
	if p.Project != "" {
		var err error
		resource, err = r.projectResource(p.Project)
		if err != nil {
			return nil, err
		}
	}
	out := []any{}
	for _, e := range r.queue.Entries {
		if resource == "" || samePath(resource, e.Resource) {
			out = append(out, r.queueViewLocked(e))
		}
	}
	start := p.Offset
	if start > len(out) {
		start = len(out)
	}
	limit := p.Limit
	if limit == 0 {
		limit = 50
	}
	end := start + limit
	if end > len(out) {
		end = len(out)
	}
	return map[string]any{"operations": out[start:end], "total": len(out), "next_offset": end, "queue_healthy": !r.queueBroken}, nil
}
func (r *Runtime) queueControl(p runtimeRequest) (any, error) {
	if p.Operation == "queue_list" {
		r.mu.Lock()
		defer r.mu.Unlock()
		return r.queueListLocked(p)
	}
	return r.mutation(p, func(_ *Receipt) (any, error) {
		if r.queueBroken || r.closing {
			return nil, errors.New("queue unavailable")
		}
		if p.Operation == "queue_add" {
			var fields map[string]json.RawMessage
			if json.Unmarshal(p.Call, &fields) != nil || fields == nil {
				return nil, errors.New("queued call must be an object")
			}
			if raw, ok := fields["project"]; ok {
				var project string
				if json.Unmarshal(raw, &project) != nil || project != p.Project {
					return nil, errors.New("queued project mismatch")
				}
			}
			fields["project"], _ = json.Marshal(p.Project)
			fields["request_id"], _ = json.Marshal(p.RequestID)
			raw, _ := json.Marshal(fields)
			req, err := decodeRuntime("", raw)
			if err != nil {
				return nil, err
			}
			if req.Operation != "command_start" && req.Operation != "codex_turn" {
				return nil, errors.New("unsupported queued operation")
			}
			if req.MachineID != "" && req.MachineID != r.MachineID {
				return nil, errors.New("queued machine mismatch")
			}
			req.MachineID = r.MachineID
			return r.enqueueLocked(req, p.Label, p.DependsOn)
		}
		if p.Operation == "queue_resume" {
			resource, err := r.projectResource(p.Project)
			if err != nil {
				return nil, err
			}
			for _, e := range r.queue.Entries {
				if !samePath(resource, e.Resource) || e.Status != "uncertain" {
					continue
				}
				if e.Request.Operation == "command_start" {
					j := r.Jobs[e.JobID]
					if e.JobID != "" && j != nil && j.Status == "running" {
						return nil, errors.New("previous command still active")
					}
					if e.JobID == "" {
						return nil, errors.New("previous start outcome unknown; inspect before resuming")
					}
				} else {
					c, err := r.getCodexLocked()
					if err != nil {
						return nil, err
					}
					thread, err := readCodexThread(c, e.Request.ThreadID, true)
					if err != nil || !sameCodexDirectory(thread, e.Resource) || !codexThreadIdle(thread) {
						return nil, errors.New("previous Codex session not verified idle")
					}
				}
				e.Status = "interrupted"
				e.FinishedAt = utcNow()
			}
			for path := range r.queue.Paused {
				if samePath(resource, path) {
					delete(r.queue.Paused, path)
				}
			}
			if err = r.queueSaveLocked(); err != nil {
				return nil, err
			}
			return r.queueListLocked(runtimeRequest{Project: p.Project})
		}
		e := r.queueFindLocked(p.QueueID)
		if e == nil {
			return nil, errors.New("queue operation not found")
		}
		if e.Status != "queued" {
			return nil, errors.New("only waiting operations may be cancelled or reordered; interrupt active job/turn explicitly")
		}
		switch p.Operation {
		case "queue_cancel":
			e.Status = "cancelled"
			e.FinishedAt = utcNow()
		case "queue_move":
			before := p.BeforeID
			if before == e.ID {
				return nil, errors.New("cannot move before itself")
			}
			if before != "" {
				v := r.queueFindLocked(before)
				if v == nil || v.Status != "queued" || !samePath(v.Resource, e.Resource) {
					return nil, errors.New("target must be waiting in the same project")
				}
			}
			order := make([]*queueEntry, 0, len(r.queue.Entries))
			inserted := false
			for _, v := range r.queue.Entries {
				if v.ID == e.ID {
					continue
				}
				if !inserted && ((before != "" && v.ID == before) || (before == "" && v.Status == "queued" && samePath(v.Resource, e.Resource))) {
					order = append(order, e)
					inserted = true
				}
				order = append(order, v)
			}
			if !inserted {
				order = append(order, e)
			}
			index := map[string]int{}
			for i, v := range order {
				index[v.ID] = i
			}
			for _, v := range order {
				if v.Status != "queued" {
					continue
				}
				for _, id := range v.DependsOn {
					dep := r.queueFindLocked(id)
					if dep != nil && dep.Status == "queued" && samePath(v.Resource, dep.Resource) && index[id] > index[v.ID] {
						return nil, errors.New("reorder would invert an explicit dependency")
					}
				}
			}
			r.queue.Entries = order
		default:
			return nil, errors.New("unknown queue control")
		}
		if err := r.queueSaveLocked(); err != nil {
			return nil, err
		}
		return r.queueListLocked(runtimeRequest{Project: e.Project})
	})
}
func (r *Runtime) queueFailLocked(e *queueEntry, status, reason string) {
	e.Status = status
	e.Error = reason
	e.FinishedAt = utcNow()
	r.queue.Paused[e.Resource] = reason
}
func (r *Runtime) queueRefreshLocked() {
	for _, e := range r.queue.Entries {
		if e.Status != "running" {
			continue
		}
		if e.Request.Operation == "command_start" {
			j := r.Jobs[e.JobID]
			if j == nil {
				r.queueFailLocked(e, "uncertain", "job result missing")
				continue
			}
			if j.Status == "running" || j.Status == "queued" {
				continue
			}
			if j.Status == "completed" {
				e.Status = "completed"
				e.FinishedAt = j.FinishedAt
			} else {
				r.queueFailLocked(e, j.Status, "command ended as "+j.Status)
			}
		} else {
			c := r.codex
			if c == nil {
				r.queueFailLocked(e, "uncertain", "Codex broker missing")
				continue
			}
			c.mu.Lock()
			finished := c.finished[e.Request.ThreadID] == e.TurnID
			closed := c.closed
			if finished {
				var events []codexEvent
				b, err := os.ReadFile(filepath.Join(c.dir, "events", e.Request.ThreadID+".json"))
				if err == nil {
					err = json.Unmarshal(b, &events)
				}
				if err != nil {
					c.mu.Unlock()
					r.queueFailLocked(e, "uncertain", "Codex final event unavailable")
					continue
				}
				status := ""
				usageSeen := false
				for _, event := range events {
					if event.Method == "turn/completed" {
						var p struct {
							Turn struct {
								ID     string `json:"id"`
								Status string `json:"status"`
							} `json:"turn"`
						}
						if json.Unmarshal(event.Params, &p) == nil && p.Turn.ID == e.TurnID {
							status = p.Turn.Status
						}
					}
					if event.Method == "jarvis/usage" {
						var p struct {
							TurnID string `json:"turn_id"`
						}
						if json.Unmarshal(event.Params, &p) == nil && p.TurnID == e.TurnID {
							usageSeen = true
						}
					}
				}
				c.mu.Unlock()
				if !usageSeen && !closed {
					continue
				}
				if status == "completed" {
					e.Status = "completed"
					e.FinishedAt = utcNow()
				} else if status == "failed" || status == "interrupted" {
					r.queueFailLocked(e, status, "Codex ended as "+status)
				} else {
					r.queueFailLocked(e, "uncertain", "Codex final status unavailable")
				}
			} else {
				c.mu.Unlock()
				if closed {
					r.queueFailLocked(e, "uncertain", "Codex disconnected; inspect session")
				}
			}
		}
	}
}
func (r *Runtime) queueDispatchLocked() {
	if r.queueBroken || r.closing {
		return
	}
	before, _ := json.Marshal(r.queue)
	r.queueRefreshLocked()
	running := 0
	for _, e := range r.queue.Entries {
		if e.Status == "running" || e.Status == "starting" {
			running++
		}
	}
	heads := []string{}
	for _, e := range r.queue.Entries {
		if e.Status != "queued" {
			continue
		}
		seen := false
		for _, path := range heads {
			if queuePathsOverlap(path, e.Resource) {
				seen = true
				break
			}
		}
		if seen {
			continue
		}
		heads = append(heads, e.Resource)
		if running >= 4 {
			break
		}
		if r.queuePausedLocked(e.Resource) != "" || r.queueBusyLocked(e.Resource) {
			continue
		}
		ready := true
		for _, id := range e.DependsOn {
			dep := r.queueFindLocked(id)
			if dep == nil || dep.Status == "cancelled" || dep.Status == "failed" || dep.Status == "interrupted" || dep.Status == "uncertain" || dep.Status == "timed_out" {
				r.queue.Paused[e.Resource] = "dependency not completed: " + id
				ready = false
				break
			}
			if dep.Status != "completed" {
				ready = false
			}
		}
		if !ready {
			continue
		}
		resource, err := r.projectResource(e.Project)
		if err != nil || !samePath(resource, e.Resource) {
			r.queueFailLocked(e, "failed", "project path changed")
			continue
		}
		e.Status = "starting"
		if r.queueSaveLocked() != nil {
			return
		}
		var value any
		if e.Request.Operation == "command_start" {
			value, err = r.startCommand(e.Request)
		} else {
			value, err = r.codexCallLocked(e.Request)
		}
		if err != nil {
			status := "uncertain"
			if e.Request.Operation == "command_start" {
				status = "failed"
			}
			r.queueFailLocked(e, status, err.Error())
		} else {
			m, ok := value.(map[string]any)
			if !ok {
				r.queueFailLocked(e, "uncertain", "unexpected dispatch result")
			} else if e.Request.Operation == "command_start" {
				e.JobID, _ = m["job_id"].(string)
				if e.JobID == "" {
					r.queueFailLocked(e, "uncertain", "job ID missing")
				} else {
					e.Status = "running"
				}
			} else {
				turn, _ := m["turn"].(map[string]any)
				e.TurnID, _ = turn["id"].(string)
				if e.TurnID == "" {
					r.queueFailLocked(e, "uncertain", "turn ID missing")
				} else {
					e.Status = "running"
				}
			}
		}
		if r.queueSaveLocked() != nil {
			return
		}
		if e.Status == "running" {
			running++
		}
	}
	after, _ := json.Marshal(r.queue)
	if string(before) != string(after) {
		_ = r.queueSaveLocked()
	}
}
func (r *Runtime) queueLoop(stop <-chan struct{}) {
	ticker := time.NewTicker(500 * time.Millisecond)
	defer ticker.Stop()
	for {
		select {
		case <-stop:
			return
		case <-ticker.C:
			r.mu.Lock()
			if r.closing {
				r.mu.Unlock()
				return
			}
			if !r.queueBroken {
				r.queueDispatchLocked()
			}
			r.mu.Unlock()
		}
	}
}

