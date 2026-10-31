package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

// Project operations are read-only and confined to an explicitly opted-in root.
func projectsRoot() (string, error) {
	root := os.Getenv("JARVIS_PROJECTS_ROOT")
	if strings.TrimSpace(root) == "" {
		return "", errors.New("project access disabled: set JARVIS_PROJECTS_ROOT to the authorized projects directory")
	}
	abs, err := filepath.Abs(root)
	if err != nil {
		return "", err
	}
	abs, err = filepath.EvalSymlinks(abs)
	if err != nil {
		return "", fmt.Errorf("projects root unavailable: %w", err)
	}
	info, err := os.Stat(abs)
	if err != nil || !info.IsDir() {
		return "", errors.New("projects root is not a directory")
	}
	return abs, nil
}
func resolveProject(rel string) (string, error) {
	root, err := projectsRoot()
	if err != nil {
		return "", err
	}
	if rel == "" || filepath.IsAbs(rel) || strings.Contains(rel, ":") {
		return "", errors.New("project must be a relative path within the projects root")
	}
	path := filepath.Join(root, rel)
	full, err := filepath.EvalSymlinks(path)
	if err != nil {
		return "", err
	}
	relative, err := filepath.Rel(root, full)
	if err != nil || relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator)) {
		return "", errors.New("project path escapes authorized root")
	}
	return full, nil
}
func projectList() (any, error) {
	root, err := projectsRoot()
	if err != nil {
		return nil, err
	}
	dirs, err := os.ReadDir(root)
	if err != nil {
		return nil, err
	}
	projects := []string{}
	for _, d := range dirs {
		if !d.IsDir() {
			continue
		}
		p, err := resolveProject(d.Name())
		if err != nil {
			continue
		}
		info, err := os.Stat(filepath.Join(p, ".git"))
		if err == nil && (info.IsDir() || info.Mode().IsRegular()) {
			projects = append(projects, d.Name())
		}
	}
	return map[string]any{"projects": projects, "root": root}, nil
}
func gitQuery(ctx context.Context, dir string, args ...string) (string, error) {
	c := exec.CommandContext(ctx, "git", append([]string{"-C", dir}, args...)...)
	c.Env = append(os.Environ(), "GIT_OPTIONAL_LOCKS=0", "GIT_TERMINAL_PROMPT=0", "GIT_CONFIG_NOSYSTEM=1")
	out, err := c.CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("git query failed: %s", strings.TrimSpace(string(out)))
	}
	return strings.TrimSpace(string(out)), nil
}
func projectStatus(rel string) (any, error) {
	dir, err := resolveProject(rel)
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	inside, err := gitQuery(ctx, dir, "rev-parse", "--show-toplevel")
	if err != nil {
		return nil, err
	}
	if !samePath(inside, dir) {
		return nil, errors.New("path is not a Git repository root")
	}
	branch, err := gitQuery(ctx, dir, "branch", "--show-current")
	if err != nil {
		return nil, err
	}
	commit, err := gitQuery(ctx, dir, "rev-parse", "--short", "HEAD")
	if err != nil {
		return nil, err
	}
	stat, err := gitQuery(ctx, dir, "status", "--porcelain=v1", "-uno")
	if err != nil {
		return nil, err
	}
	changed := 0
	if stat != "" {
		changed = len(strings.Split(stat, "\n"))
	}
	return map[string]any{"project": rel, "branch": branch, "head": commit, "tracked_changes": changed, "clean_tracked": changed == 0, "status_checked_at": time.Now().UTC().Format(time.RFC3339)}, nil
}
func samePath(a, b string) bool { return strings.EqualFold(filepath.Clean(a), filepath.Clean(b)) }
