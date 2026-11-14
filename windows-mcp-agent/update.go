package main

import (
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
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

type updateReceipt struct {
	ID       string `json:"update_id"`
	SHA256   string `json:"sha256"`
	State    string `json:"state"`
	PID      int    `json:"launcher_pid,omitempty"`
	ExitCode *int   `json:"exit_code,omitempty"`
	At       string `json:"at"`
}

func updateReceiptPath(id string) (string, error) {
	if !updateIDPattern.MatchString(id) {
		return "", errors.New("invalid update ID")
	}
	root, e := updateBase()
	if e != nil {
		return "", e
	}
	return filepath.Join(root, "State", "update-receipts", id+".json"), nil
}
func loadUpdateReceipt(id string) (*updateReceipt, error) {
	path, e := updateReceiptPath(id)
	if e != nil {
		return nil, e
	}
	b, e := os.ReadFile(path)
	if e != nil {
		return nil, e
	}
	var v updateReceipt
	if json.Unmarshal(b, &v) != nil || v.ID != id || !updateHashPattern.MatchString(v.SHA256) {
		return nil, errors.New("invalid update receipt; preserved")
	}
	return &v, nil
}
func saveUpdateReceipt(v *updateReceipt) error {
	path, e := updateReceiptPath(v.ID)
	if e != nil {
		return e
	}
	if e = os.MkdirAll(filepath.Dir(path), 0700); e != nil {
		return e
	}
	if e = protectRuntimeDirectory(filepath.Dir(path)); e != nil {
		return e
	}
	v.At = utcNow()
	return atomicJSON(path, v)
}
func readUpdateStatus(id string) (any, error) {
	updateState.Lock()
	defer updateState.Unlock()
	v, e := loadUpdateReceipt(id)
	if e != nil {
		return nil, e
	}
	uncertain := (v.State == "dispatching" || v.State == "launched") && !updateState.Running[id]
	return map[string]any{"receipt": v, "verification_required": uncertain, "message": "Never replay an uncertain update; verify agent identity/version/hash and the canonical updater receipt. Batch exit is not installation verification."}, nil
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
	Running map[string]bool
}{Used: map[string]bool{}, Running: map[string]bool{}}

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
	if receipt, e := loadUpdateReceipt(req.ID); e == nil {
		if receipt.SHA256 != strings.ToLower(req.SHA256) {
			return nil, errors.New("update ID already bound to different bytes")
		}
		return receipt, nil
	} else if !os.IsNotExist(e) {
		return nil, e
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
	if err = saveUpdateReceipt(&updateReceipt{ID: req.ID, SHA256: strings.ToLower(req.SHA256), State: "prepared"}); err != nil {
		return nil, err
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
	receipt, err := loadUpdateReceipt(id)
	if err != nil {
		return nil, err
	}
	if !updateHashPattern.MatchString(expectedHash) || strings.ToLower(expectedHash) != receipt.SHA256 {
		return nil, errors.New("hash differs from prepared update")
	}
	if receipt.State != "prepared" {
		return receipt, nil
	}
	root, err := updateBase()
	if err != nil {
		return nil, err
	}
	p := &preparedUpdate{ID: id, SHA256: receipt.SHA256, Path: filepath.Join(root, "Scripts", "updates", id+".cmd")}
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
	receipt.State = "dispatching"
	if err = saveUpdateReceipt(receipt); err != nil {
		return nil, err
	}
	cmd := detachedBatchCommand(p.Path)
	cmd.Dir = filepath.Dir(p.Path)
	if err = cmd.Start(); err != nil {
		receipt.State = "launch_failed"
		_ = saveUpdateReceipt(receipt)
		return nil, fmt.Errorf("start batch: %w", err)
	}
	pid := cmd.Process.Pid
	receipt.PID = pid
	receipt.State = "launched"
	persistError := saveUpdateReceipt(receipt)
	updateState.Running[id] = true
	go func() {
		waitErr := cmd.Wait()
		updateState.Lock()
		delete(updateState.Running, id)
		exit := cmd.ProcessState.ExitCode()
		receipt.ExitCode = &exit
		receipt.State = "batch_exited"
		if waitErr != nil {
			receipt.State = "batch_failed"
		}
		_ = saveUpdateReceipt(receipt)
		updateState.Unlock()
	}()
	updateState.Used[id] = true
	updateState.Pending = nil
	if persistError != nil {
		return nil, errors.New("batch launched but receipt persistence failed; do not retry; inspect target process and updater log")
	}
	return map[string]any{"state": "launched", "update_id": id, "launcher_pid": pid, "message": "Execution delegated; success must be checked after MCP reconnect"}, nil
}
