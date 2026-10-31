package main

import (
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

const maxLogReadBytes = 32768

type logReadRequest struct {
	Path      string `json:"path"`
	TailBytes int    `json:"tail_bytes"`
}

func logRoot() (string, error) {
	root, err := updateBase()
	if err != nil {
		return "", err
	}
	return filepath.Join(root, "Logs"), nil
}

// Only a relative path made of ordinary directories and regular files is allowed.
// Lstat is used at every level to reject Windows symlink/reparse-point traversal.
func safeLogPath(rel string) (string, error) {
	if rel == "" || filepath.IsAbs(rel) || filepath.VolumeName(rel) != "" || strings.Contains(rel, ":") || strings.Contains(rel, "\\") && os.PathSeparator != '\\' {
		return "", errors.New("invalid log path")
	}
	norm := filepath.Clean(rel)
	if norm == "." || norm == ".." || strings.HasPrefix(norm, ".."+string(filepath.Separator)) {
		return "", errors.New("log path escapes root")
	}
	if filepath.Ext(norm) != ".log" {
		return "", errors.New("only .log files are accessible")
	}
	root, err := logRoot()
	if err != nil {
		return "", err
	}
	parts := strings.Split(norm, string(filepath.Separator))
	current := root
	for _, part := range parts {
		if part == "" || part == "." || part == ".." {
			return "", errors.New("invalid log path segment")
		}
		current = filepath.Join(current, part)
		info, e := os.Lstat(current)
		if e != nil {
			return "", e
		}
		if info.Mode()&os.ModeSymlink != 0 || info.Mode()&os.ModeIrregular != 0 {
			return "", errors.New("log path contains link or unsupported file")
		}
	}
	return current, nil
}

func logRead(req logReadRequest) (any, error) {
	if req.TailBytes == 0 {
		req.TailBytes = 8192
	}
	if req.TailBytes < 1 || req.TailBytes > maxLogReadBytes {
		return nil, errors.New("tail_bytes must be 1..32768")
	}
	path, err := safeLogPath(req.Path)
	if err != nil {
		return nil, err
	}
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	info, err := f.Stat()
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() {
		return nil, errors.New("not a regular log file")
	}
	size := info.Size()
	start := size - int64(req.TailBytes)
	if start < 0 {
		start = 0
	}
	if _, err = f.Seek(start, io.SeekStart); err != nil {
		return nil, err
	}
	b, err := io.ReadAll(io.LimitReader(f, int64(req.TailBytes)))
	if err != nil {
		return nil, err
	}
	return map[string]any{"path": req.Path, "size_bytes": size, "offset": start, "content": string(b), "truncated": start > 0}, nil
}

func logList() (any, error) {
	root, err := logRoot()
	if err != nil {
		return nil, err
	}
	if _, err = os.Stat(root); os.IsNotExist(err) {
		return map[string]any{"logs": []any{}}, nil
	} else if err != nil {
		return nil, err
	}
	items := []map[string]any{}
	err = filepath.WalkDir(root, func(path string, d os.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if d.Type()&os.ModeSymlink != 0 {
			return nil
		}
		if d.IsDir() {
			return nil
		}
		if !d.Type().IsRegular() || filepath.Ext(d.Name()) != ".log" {
			return nil
		}
		if len(items) >= 300 {
			return nil
		}
		info, e := d.Info()
		if e != nil {
			return e
		}
		rel, e := filepath.Rel(root, path)
		if e != nil {
			return e
		}
		items = append(items, map[string]any{"path": rel, "size_bytes": info.Size(), "modified_at": info.ModTime().UTC().Format(time.RFC3339)})
		return nil
	})
	if err != nil {
		return nil, err
	}
	return map[string]any{"logs": items, "limit": 300}, nil
}

func logRetentionDays() int {
	days := 30
	if raw := strings.TrimSpace(os.Getenv("JARVIS_LOG_RETENTION_DAYS")); raw != "" {
		if n, e := strconv.Atoi(raw); e == nil && n >= 1 && n <= 365 {
			days = n
		} else {
			fmt.Fprintln(os.Stderr, "Invalid JARVIS_LOG_RETENTION_DAYS, using 30")
		}
	}
	return days
}

func cleanupOldLogs(now time.Time) (int, error) {
	root, err := logRoot()
	if err != nil {
		return 0, err
	}
	if _, err = os.Stat(root); os.IsNotExist(err) {
		return 0, nil
	} else if err != nil {
		return 0, err
	}
	cutoff := now.Add(-time.Duration(logRetentionDays()) * 24 * time.Hour)
	removed := 0
	err = filepath.WalkDir(root, func(path string, d os.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if d.Type()&os.ModeSymlink != 0 || d.IsDir() || !d.Type().IsRegular() || filepath.Ext(d.Name()) != ".log" {
			return nil
		}
		info, e := d.Info()
		if e != nil {
			return e
		}
		if info.ModTime().Before(cutoff) {
			if e = os.Remove(path); e != nil {
				return e
			}
			removed++
		}
		return nil
	})
	return removed, err
}

func startLogMaintenance() {
	run := func() {
		removed, err := cleanupOldLogs(time.Now())
		if err != nil {
			fmt.Fprintf(os.Stderr, "Log cleanup error: %v\n", err)
		} else if removed > 0 {
			fmt.Fprintf(os.Stderr, "Log cleanup removed %d old files\n", removed)
		}
	}
	run()
	go func() {
		tick := time.NewTicker(24 * time.Hour)
		defer tick.Stop()
		for range tick.C {
			run()
		}
	}()
}