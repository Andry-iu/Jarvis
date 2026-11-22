package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
)

type archivedActivity struct {
	Version   int         `json:"version"`
	MachineID string      `json:"machine_id"`
	Entry     *queueEntry `json:"entry"`
}

func terminalActivity(status string) bool {
	switch status {
	case "completed", "failed", "cancelled", "interrupted", "timed_out":
		return true
	}
	return false
}
func (r *Runtime) archivePath(id string) (string, error) {
	if !requestIDPattern.MatchString(id) {
		return "", errors.New("invalid activity ID")
	}
	// All generated queue IDs are lower-case hexadecimal; hash also supports old safe IDs.
	return filepath.Join(r.dir, "archive", digest([]byte(id))[:2], id+".json"), nil
}
func (r *Runtime) archivedEntry(id string) (*queueEntry, error) {
	path, err := r.archivePath(id)
	if err != nil {
		return nil, err
	}
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	var a archivedActivity
	d := json.NewDecoder(io.LimitReader(f, 1024*1024))
	if err = d.Decode(&a); err != nil {
		return nil, err
	}
	if a.Version != 1 || a.MachineID != r.MachineID || a.Entry == nil || a.Entry.ID != id || !terminalActivity(a.Entry.Status) {
		return nil, errors.New("invalid activity archive; inspect before recovery")
	}
	return a.Entry, nil
}
func (r *Runtime) archiveTerminalLocked() ([]*queueEntry, error) {
	kept := make([]*queueEntry, 0, len(r.queue.Entries))
	for _, e := range r.queue.Entries {
		if !terminalActivity(e.Status) {
			kept = append(kept, e)
			continue
		}
		path, err := r.archivePath(e.ID)
		if err != nil {
			return nil, err
		}
		a := archivedActivity{1, r.MachineID, e}
		expected, err := json.Marshal(a)
		if err != nil {
			return nil, err
		}
		b, err := os.ReadFile(path)
		if err == nil {
			if !bytes.Equal(bytes.TrimSpace(b), expected) {
				return nil, errors.New("archive conflict; processing stopped; no overwrite")
			}
		} else if os.IsNotExist(err) {
			if err = os.MkdirAll(filepath.Dir(path), 0700); err != nil {
				return nil, err
			}
			if err = atomicJSON(path, a); err != nil {
				return nil, fmt.Errorf("activity archive failed: %w", err)
			}
		} else {
			return nil, err
		}
	}
	// Only after every immutable archive copy exists may queue.json omit entries.
	return kept, nil
}
func archivedView(e *queueEntry) map[string]any {
	return map[string]any{"queue_id": e.ID, "project": e.Project, "label": e.Label, "status": e.Status, "created_at": e.CreatedAt, "finished_at": e.FinishedAt, "job_id": e.JobID, "thread_id": e.Request.ThreadID, "turn_id": e.TurnID, "error": e.Error, "depends_on": e.DependsOn, "resources": e.Claims, "final_usage": e.FinalUsage}
}

// ReadDir(n), unlike os.ReadDir, never loads the entire directory into memory.
func visitJSON(dir string, fn func(string) (bool, error)) error {
	f, err := os.Open(dir)
	if os.IsNotExist(err) {
		return nil
	}
	if err != nil {
		return err
	}
	defer f.Close()
	for {
		entries, err := f.ReadDir(64)
		if err != nil && err != io.EOF {
			return err
		}
		for _, de := range entries {
			if !de.IsDir() && filepath.Ext(de.Name()) == ".json" {
				stop, e := fn(filepath.Join(dir, de.Name()))
				if e != nil {
					return e
				}
				if stop {
					return io.EOF
				}
			}
		}
		if err == io.EOF {
			return nil
		}
	}
}
func (r *Runtime) activityHistory(p runtimeRequest) (any, error) {
	if p.QueueID != "" {
		e, err := r.archivedEntry(p.QueueID)
		if err != nil {
			return nil, err
		}
		if p.Project != "" && p.Project != e.Project {
			return nil, errors.New("activity project mismatch")
		}
		return map[string]any{"machine_id": r.MachineID, "activity": archivedView(e)}, nil
	}
	limit := p.Limit
	if limit == 0 {
		limit = 50
	}
	if limit < 1 || limit > 200 || p.Offset < 0 {
		return nil, errors.New("invalid history pagination")
	}
	out := []any{}
	seen := 0
	more := false
	for bucket := 0; bucket < 256; bucket++ {
		err := visitJSON(filepath.Join(r.dir, "archive", fmt.Sprintf("%02x", bucket)), func(path string) (bool, error) {
			id := filepath.Base(path)
			id = id[:len(id)-5]
			e, err := r.archivedEntry(id)
			if err != nil {
				return false, err
			}
			if p.Project != "" && e.Project != p.Project {
				return false, nil
			}
			seen++
			if seen <= p.Offset {
				return false, nil
			}
			if len(out) == limit {
				more = true
				return true, nil
			}
			out = append(out, archivedView(e))
			return false, nil
		})
		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, err
		}
	}
	return map[string]any{"machine_id": r.MachineID, "activities": out, "next_offset": p.Offset + len(out), "has_more": more, "order": "storage; not an atomic snapshot"}, nil
}
func (r *Runtime) lookupJobLocked(id string) *Job {
	if j := r.Jobs[id]; j != nil {
		return j
	}
	if !requestIDPattern.MatchString(id) {
		return nil
	}
	var j Job
	b, err := os.ReadFile(filepath.Join(r.dir, "jobs", id+".json"))
	if err != nil || json.Unmarshal(b, &j) != nil || j.ID != id || j.MachineID != r.MachineID {
		return nil
	}
	return &j
}
