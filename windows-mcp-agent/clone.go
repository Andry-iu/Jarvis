package main

import (
	"context"
	"errors"
	"fmt"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"time"
)

var repoNamePattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$`)

// Only HTTPS GitHub repositories in the explicitly allowed account can be cloned.
func validateCloneInput(raw, project string) (string, string, error) {
	u, err := url.Parse(raw)
	if err != nil || u.Scheme != "https" || !strings.EqualFold(u.Host, "github.com") || u.User != nil || u.RawQuery != "" || u.Fragment != "" {
		return "", "", errors.New("repository must be a GitHub HTTPS URL without credentials or query parameters")
	}
	parts := strings.Split(strings.Trim(u.Path, "/"), "/")
	if len(parts) != 2 || !strings.EqualFold(parts[0], "Andry-iu") {
		return "", "", errors.New("repository must belong to approved GitHub owner Andry-iu")
	}
	repo := strings.TrimSuffix(parts[1], ".git")
	if !repoNamePattern.MatchString(repo) || repo == "." || repo == ".." {
		return "", "", errors.New("invalid repository name")
	}
	if project == "" {
		project = repo
	}
	if !repoNamePattern.MatchString(project) || project == "." || project == ".." || strings.HasSuffix(project, ".") {
		return "", "", errors.New("invalid local project name")
	}
	return "https://github.com/" + parts[0] + "/" + repo + ".git", project, nil
}

func (a *Agent) cloneProject(raw, project string) (any, error) {
	remote, name, err := validateCloneInput(raw, project)
	if err != nil {
		return nil, err
	}
	root, err := projectsRoot()
	if err != nil {
		return nil, err
	}
	dest := filepath.Join(root, name)
	if _, err := os.Lstat(dest); err == nil {
		return nil, errors.New("target already exists; refusing to overwrite")
	} else if !os.IsNotExist(err) {
		return nil, err
	}
	a.mu.Lock()
	if a.task != nil && a.task.Status == "running" {
		a.mu.Unlock()
		return nil, errors.New("another task is running")
	}
	a.next++
	ctx, cancel := context.WithTimeout(context.Background(), 180*time.Second)
	t := &Task{ID: fmt.Sprintf("task-%d", a.next), Name: "Clone " + name, Status: "running", StartedAt: time.Now().Format(time.RFC3339), cancel: cancel}
	a.task = t
	a.mu.Unlock()
	go func() {
		defer cancel()
		// Clone to a staging directory, then atomically publish on successful completion.
		staging, err := os.MkdirTemp(root, ".jarvis-clone-*")
		if err == nil {
			err = os.Remove(staging)
		}
		var result string
		if err == nil {
			cmd := exec.CommandContext(ctx, "git", "-c", "credential.interactive=never", "clone", "--no-checkout", "--", remote, staging)
			cmd.Env = append(os.Environ(), "GIT_TERMINAL_PROMPT=0", "GCM_INTERACTIVE=Never", "GIT_CONFIG_NOSYSTEM=1")
			output, runErr := cmd.CombinedOutput()
			err = runErr
			if err != nil {
				result = "git clone failed (check Git credentials on notebook): " + trimGitError(string(output))
			}
		}
		if err == nil {
			cmd := exec.CommandContext(ctx, "git", "-C", staging, "checkout", "--force")
			cmd.Env = append(os.Environ(), "GIT_TERMINAL_PROMPT=0", "GCM_INTERACTIVE=Never", "GIT_CONFIG_NOSYSTEM=1")
			output, runErr := cmd.CombinedOutput()
			err = runErr
			if err != nil {
				result = "git checkout failed: " + trimGitError(string(output))
			}
		}
		if err == nil {
			err = os.Rename(staging, dest)
			if err != nil {
				result = "cannot publish clone: " + err.Error()
			}
		}
		if err != nil {
			_ = os.RemoveAll(staging)
		}
		a.mu.Lock()
		defer a.mu.Unlock()
		t.FinishedAt = time.Now().Format(time.RFC3339)
		if err == nil {
			t.Status = "completed"
			t.Progress = 100
			t.Result = "Cloned " + remote + " into " + dest
		} else if ctx.Err() != nil {
			t.Status = "cancelled"
			t.Result = "Clone cancelled or timed out; staging removed"
		} else {
			t.Status = "failed"
			t.Result = result
			if result == "" {
				t.Result = err.Error()
			}
		}
	}()
	return map[string]any{"task_id": t.ID, "state": "running", "project": name, "message": "Clone started; poll agent_status for completion"}, nil
}

func trimGitError(s string) string {
	s = strings.TrimSpace(s)
	// Keep messages short and avoid printing URL credentials (credentials are forbidden in inputs).
	if len(s) > 400 {
		s = s[:400]
	}
	return s
}
