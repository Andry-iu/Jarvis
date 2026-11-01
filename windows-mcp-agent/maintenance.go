package main

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

const maintenanceInterval = 24 * time.Hour

var maintenanceState struct {
	sync.Mutex
	LastRun time.Time
	LogsRemoved, BatchesRemoved int
	Error string
}

func updateRetentionDays() int {
	n, err := strconv.Atoi(strings.TrimSpace(os.Getenv("JARVIS_UPDATE_RETENTION_DAYS")))
	if err != nil || n < 1 || n > 365 { return 30 }
	return n
}

// Only immutable, agent-generated update batches are temporary. Startup scripts,
// backups, state and unrelated files never belong to this retention policy.
func cleanupOldUpdateBatches(now time.Time) (int, error) {
	root, err := updateBase()
	if err != nil { return 0, err }
	dir := filepath.Join(root, "Scripts", "updates")
	for _, part := range []string{root, filepath.Join(root, "Scripts")} {
		st, e := os.Lstat(part)
		if os.IsNotExist(e) { return 0, nil }
		if e != nil { return 0, e }
		if !st.IsDir() || st.Mode()&(os.ModeSymlink|os.ModeIrregular) != 0 { return 0, errors.New("update path contains a link or unsupported directory") }
	}
	info, err := os.Lstat(dir)
	if os.IsNotExist(err) { return 0, nil }
	if err != nil { return 0, err }
	if !info.IsDir() || info.Mode()&(os.ModeSymlink|os.ModeIrregular) != 0 { return 0, errors.New("update directory is not an ordinary directory") }
	entries, err := os.ReadDir(dir)
	if err != nil { return 0, err }
	cutoff := now.Add(-time.Duration(updateRetentionDays()) * 24 * time.Hour)
	removed := 0
	var failures []error
	for _, entry := range entries {
		ext := strings.ToLower(filepath.Ext(entry.Name()))
		if ext != ".cmd" && ext != ".bat" { continue }
		id := strings.TrimSuffix(entry.Name(), filepath.Ext(entry.Name()))
		if !updateIDPattern.MatchString(id) { continue }
		path := filepath.Join(dir, entry.Name())
		info, e := os.Lstat(path)
		if e != nil { failures = append(failures, e); continue }
		if !info.Mode().IsRegular() || !info.ModTime().Before(cutoff) || info.Size() > 262144 { continue }
		updateState.Lock()
		busy := (updateState.Pending != nil && updateState.Pending.ID == id) || updateState.Running[id]
		if !busy {
			content, readErr := os.ReadFile(path)
			if readErr != nil { failures = append(failures, readErr) } else if strings.HasPrefix(string(content), "REM JARVIS-UPDATE-ID: "+id+"\r\n") {
				if e = os.Remove(path); e != nil { failures = append(failures, e) } else { removed++ }
			}
		}
		updateState.Unlock()
	}
	return removed, errors.Join(failures...)
}

func runMaintenance(now time.Time) {
	logs, logErr := cleanupOldLogs(now)
	batches, batchErr := cleanupOldUpdateBatches(now)
	err := errors.Join(logErr, batchErr)
	maintenanceState.Lock()
	maintenanceState.LastRun = now.UTC()
	maintenanceState.LogsRemoved = logs
	maintenanceState.BatchesRemoved = batches
	maintenanceState.Error = ""
	if err != nil { maintenanceState.Error = "cleanup_incomplete" }
	maintenanceState.Unlock()
	if err != nil { fmt.Fprintf(os.Stderr, "Maintenance error: %v\n", err) }
}

func maintenanceStatus() any {
	maintenanceState.Lock()
	defer maintenanceState.Unlock()
	next := ""
	last := ""
	if !maintenanceState.LastRun.IsZero() {
		last = maintenanceState.LastRun.Format(time.RFC3339)
		next = maintenanceState.LastRun.Add(maintenanceInterval).Format(time.RFC3339)
	}
	return map[string]any{"log_retention_days":logRetentionDays(), "batch_retention_days":updateRetentionDays(), "interval_hours":24, "last_check":last, "next_check":next, "logs_removed":maintenanceState.LogsRemoved, "batches_removed":maintenanceState.BatchesRemoved, "error":maintenanceState.Error}
}
