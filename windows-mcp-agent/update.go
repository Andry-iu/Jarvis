package main

import (
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"sync"
)

var updateIDPattern = regexp.MustCompile(`^[A-Fa-f0-9]{32}$`)
var updateHashPattern = regexp.MustCompile(`^[a-fA-F0-9]{64}$`)

type updateRequest struct {
	ID      string `json:"update_id"`
	Content string `json:"content"`
	SHA256  string `json:"sha256"`
}

type preparedUpdate struct {
	ID     string
	SHA256 string
	Path   string
}

var updateState = struct {
	sync.Mutex
	Pending *preparedUpdate
	Used    map[string]bool
}{Used: map[string]bool{}}

func updateBase() (string, error) {
	root := os.Getenv("JARVIS_HOME")
	if root == "" {
		root = `D:\Jarvis`
	}
	if !filepath.IsAbs(root) {
		return "", errors.New("JARVIS_HOME must be absolute")
	}
	return filepath.Clean(root), nil
}

func prepareUpdate(req updateRequest) (any, error) {
	if runtime.GOOS != "windows" {
		return nil, errors.New("updates supported on Windows only")
	}
	if !updateIDPattern.MatchString(req.ID) || !updateHashPattern.MatchString(req.SHA256) {
		return nil, errors.New("invalid update ID or SHA-256")
	}
	if len(req.Content) == 0 || len(req.Content) > 256*1024 {
		return nil, errors.New("batch must be 1..262144 UTF-8 bytes")
	}
	if !strings.HasPrefix(req.Content, "REM JARVIS-UPDATE-ID: "+req.ID+"\r\n") {
		return nil, errors.New("first line must be exact REM update ID followed by CRLF")
	}
	if strings.ContainsRune(req.Content, 0) {
		return nil, errors.New("NUL forbidden")
	}
	raw := []byte(req.Content)
	h := sha256.Sum256(raw)
	expected, _ := hex.DecodeString(req.SHA256)
	if subtle.ConstantTimeCompare(h[:], expected) != 1 {
		return nil, errors.New("SHA-256 mismatch before writing")
	}
	root, err := updateBase()
	if err != nil {
		return nil, err
	}
	dir := filepath.Join(root, "Scripts", "updates")
	if err = os.MkdirAll(dir, 0700); err != nil {
		return nil, err
	}
	// One immutable file per update. Never overwrite previously prepared scripts.
	path := filepath.Join(dir, req.ID+".cmd")
	updateState.Lock()
	defer updateState.Unlock()
	if updateState.Used[req.ID] {
		return nil, errors.New("update ID already used")
	}
	if _, err = os.Lstat(path); err == nil {
		return nil, errors.New("update ID file already exists")
	}
	if !os.IsNotExist(err) {
		return nil, err
	}
	f, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if err != nil {
		return nil, err
	}
	n, werr := f.Write(raw)
	cerr := f.Close()
	if werr != nil || cerr != nil || n != len(raw) {
		os.Remove(path)
		return nil, errors.New("batch write failed")
	}
	got, err := os.ReadFile(path)
	if err != nil || subtle.ConstantTimeCompare(got, raw) != 1 {
		os.Remove(path)
		return nil, errors.New("batch differs from submitted bytes")
	}
	updateState.Pending = &preparedUpdate{ID: req.ID, SHA256: strings.ToLower(req.SHA256), Path: path}
	return map[string]any{"update_id": req.ID, "sha256": strings.ToLower(req.SHA256), "path": path, "state": "prepared"}, nil
}

func executeUpdate(id string, expectedHash string) (any, error) {
	if runtime.GOOS != "windows" {
		return nil, errors.New("updates supported on Windows only")
	}
	updateState.Lock()
	defer updateState.Unlock()
	p := updateState.Pending
	if p == nil || p.ID != id || !updateIDPattern.MatchString(id) || !updateHashPattern.MatchString(expectedHash) {
		return nil, errors.New("no matching prepared update")
	}
	if subtle.ConstantTimeCompare([]byte(strings.ToLower(expectedHash)), []byte(p.SHA256)) != 1 {
		return nil, errors.New("hash differs from prepared update")
	}
	b, err := os.ReadFile(p.Path)
	if err != nil {
		return nil, err
	}
	h := sha256.Sum256(b)
	if subtle.ConstantTimeCompare([]byte(hex.EncodeToString(h[:])), []byte(p.SHA256)) != 1 || !strings.HasPrefix(string(b), "REM JARVIS-UPDATE-ID: "+p.ID+"\r\n") {
		return nil, errors.New("batch verification failed")
	}
	// Start cmd directly, without the CMD internal START command. START's
	// empty-title quoting is ambiguous when exec.Command quotes its arguments.
	// Windows process flags detach the batch from the agent's console.
	cmd := detachedBatchCommand(p.Path)
	cmd.Dir = filepath.Dir(p.Path)
	if err = cmd.Start(); err != nil {
		return nil, fmt.Errorf("start batch: %w", err)
	}
	pid := cmd.Process.Pid
	_ = cmd.Process.Release()
	updateState.Used[id] = true
	updateState.Pending = nil
	return map[string]any{"state": "launched", "update_id": id, "launcher_pid": pid, "message": "Execution delegated; success must be checked after MCP reconnect"}, nil
}