package main

import (
	_ "embed"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"sync"
	"unicode/utf16"
)

// The same target-owned runner is used through direct MCP and every gateway hop.
// Gateways never acquire source, build, or distribute another agent's executable.
//
//go:embed scripts/update-from-git.ps1
var sourceUpdateRunner string

var sourceCommitPattern = regexp.MustCompile(`^[a-f0-9]{40}$`)
var sourceVersionPattern = regexp.MustCompile(`^[0-9]+\.[0-9]+\.[0-9]+$`)
var sourceUpdateMu sync.Mutex

type sourceUpdateRecord struct {
	RequestID       string `json:"request_id"`
	MachineID       string `json:"machine_id"`
	SourceCommit    string `json:"source_commit"`
	TargetVersion   string `json:"target_version"`
	Phase           string `json:"phase"`
	UpdatedAt       string `json:"updated_at"`
	InstalledHash   string `json:"installed_sha256,omitempty"`
	Backup          string `json:"backup,omitempty"`
	Error           string `json:"error,omitempty"`
	CleanupVerified bool   `json:"cleanup_verified"`
}

func sourceUpdatePath(home, id string) (string, error) {
	if !requestIDPattern.MatchString(id) {
		return "", errors.New("invalid update request_id")
	}
	return filepath.Join(home, "State", "source-updates", id, "status.json"), nil
}
func readSourceUpdate(home, id string) (*sourceUpdateRecord, error) {
	p, e := sourceUpdatePath(home, id)
	if e != nil {
		return nil, e
	}
	b, e := os.ReadFile(p)
	if e != nil {
		return nil, e
	}
	var v sourceUpdateRecord
	if json.Unmarshal(b, &v) != nil || v.RequestID != id || !sourceCommitPattern.MatchString(v.SourceCommit) || !sourceVersionPattern.MatchString(v.TargetVersion) {
		return nil, errors.New("invalid source update record; preserved")
	}
	return &v, nil
}
func terminalSourceUpdate(v *sourceUpdateRecord) bool {
	return (v.Phase == "completed" || v.Phase == "failed" || v.Phase == "prepared") && v.CleanupVerified
}
func psLiteral(s string) string { return "'" + strings.ReplaceAll(s, "'", "''") + "'" }
func sourceUpdateBatch(id, script, home string, p runtimeRequest, previousHash string) string {
	command := "& " + psLiteral(script) + " -HomeDir " + psLiteral(home) + " -SourceCommit " + psLiteral(p.SourceCommit) + " -TargetVersion " + psLiteral(p.TargetVersion) + " -RequestId " + psLiteral(p.RequestID) + " -ExpectedMachine " + psLiteral(p.MachineID) + " -ExpectedPreviousVersion " + psLiteral(version) + " -ExpectedPreviousHash " + psLiteral(previousHash)
	units := utf16.Encode([]rune(command))
	b := make([]byte, len(units)*2)
	for i, v := range units {
		binary.LittleEndian.PutUint16(b[i*2:], v)
	}
	return "REM JARVIS-UPDATE-ID: " + id + "\r\n@echo off\r\npowershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand " + base64.StdEncoding.EncodeToString(b) + "\r\nexit /b %errorlevel%\r\n"
}
func (a *Agent) startSourceUpdate(r *Runtime, p runtimeRequest) (any, error) {
	if !requestIDPattern.MatchString(p.RequestID) || !sourceCommitPattern.MatchString(p.SourceCommit) || !sourceVersionPattern.MatchString(p.TargetVersion) {
		return nil, errors.New("exact 40-hex source_commit, target_version and unique request_id required")
	}
	if runtime.GOOS != "windows" && a.testLaunchSourceUpdate == nil {
		return nil, errors.New("source updates require Windows")
	}
	sourceUpdateMu.Lock()
	defer sourceUpdateMu.Unlock()
	// Repeated requests go through the immutable request receipt, never relaunch.
	if _, e := os.Stat(filepath.Join(r.dir, "requests", p.RequestID+".json")); e == nil {
		return r.mutation(p, func(_ *Receipt) (any, error) { return nil, errors.New("unreachable replay") })
	}
	priv := currentAgentPrivileges()
	if a.testPrivileges != nil {
		priv = a.testPrivileges()
	}
	if priv.Elevated == nil || !*priv.Elevated {
		return nil, errors.New("verified elevated agent required")
	}
	if !a.restartIdle(r) {
		return nil, errors.New("agent has active, queued or uncertain work")
	}
	active := filepath.Join(r.home, "State", "source-updates", "active.json")
	if b, e := os.ReadFile(active); e == nil {
		var old struct {
			RequestID string `json:"request_id"`
		}
		if json.Unmarshal(b, &old) != nil {
			return nil, errors.New("invalid active update record; preserved")
		}
		v, e := readSourceUpdate(r.home, old.RequestID)
		if e != nil || !terminalSourceUpdate(v) {
			return nil, errors.New("another source update is active or uncertain; inspect agent_update_status")
		}
	} else if !os.IsNotExist(e) {
		return nil, e
	}
	exe, e := os.Executable()
	if e != nil {
		return nil, e
	}
	binaryBytes, e := os.ReadFile(exe)
	if e != nil {
		return nil, e
	}
	return r.mutation(p, func(_ *Receipt) (any, error) {
		statusPath, _ := sourceUpdatePath(r.home, p.RequestID)
		dir := filepath.Dir(statusPath)
		if e := os.MkdirAll(dir, 0700); e != nil {
			return nil, e
		}
		if e := protectRuntimeDirectory(dir); e != nil {
			return nil, e
		}
		v := &sourceUpdateRecord{RequestID: p.RequestID, MachineID: r.MachineID, SourceCommit: p.SourceCommit, TargetVersion: p.TargetVersion, Phase: "dispatching", UpdatedAt: utcNow()}
		if e := atomicJSON(statusPath, v); e != nil {
			return nil, e
		}
		if e := atomicJSON(active, map[string]string{"request_id": p.RequestID}); e != nil {
			return nil, e
		}
		script := filepath.Join(dir, "runner.ps1")
		if e := os.WriteFile(script, append([]byte{0xef, 0xbb, 0xbf}, []byte(sourceUpdateRunner)...), 0600); e != nil {
			return nil, e
		}
		id := newID()
		batch := sourceUpdateBatch(id, script, r.home, p, digest(binaryBytes))
		if a.testLaunchSourceUpdate != nil {
			if e := a.testLaunchSourceUpdate(batch); e != nil {
				return nil, e
			}
		} else {
			if base, e := updateBase(); e != nil || !strings.EqualFold(base, r.home) {
				return nil, errors.New("update home mismatch")
			}
			if _, e := prepareUpdate(updateRequest{ID: id, Content: batch, SHA256: digest([]byte(batch))}); e != nil {
				return nil, e
			}
			if _, e := executeUpdate(id, digest([]byte(batch))); e != nil {
				return nil, e
			}
		}
		return map[string]any{"state": "dispatched", "request_id": p.RequestID, "source_commit": p.SourceCommit, "target_version": p.TargetVersion, "message": "Target downloads Git sources, tests/builds locally and runs the canonical updater. Read agent_update_status; dispatch is not completion."}, nil
	})
}
func (a *Agent) sourceUpdateStatus(r *Runtime, id string) (any, error) {
	v, e := readSourceUpdate(r.home, id)
	if e != nil {
		return nil, e
	}
	if v.MachineID != r.MachineID {
		return nil, errors.New("source update machine identity mismatch")
	}
	if v.Phase == "completed" {
		exe, e := os.Executable()
		if e != nil {
			return nil, e
		}
		b, e := os.ReadFile(exe)
		if e != nil {
			return nil, e
		}
		if v.TargetVersion != version || !v.CleanupVerified || digest(b) != v.InstalledHash {
			return nil, errors.New("update receipt does not match the live agent; verification required")
		}
	}
	return v, nil
}
