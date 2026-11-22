package main

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

// Claims coordinate this agent's work; they do not grant access to a resource.
type resourceClaim struct {
	Name string `json:"name"`
	Mode string `json:"mode"`
}

var resourceNamePattern = regexp.MustCompile(`^(process|shared):[a-z0-9][a-z0-9_.-]{0,95}$`)

func normalizedClaims(in []resourceClaim) ([]resourceClaim, error) {
	if len(in) > 32 {
		return nil, errors.New("at most 32 shared resource claims")
	}
	m := map[string]string{}
	for _, c := range in {
		c.Name = strings.ToLower(c.Name)
		if c.Mode != "read" && c.Mode != "write" {
			return nil, errors.New("resource mode must be read or write")
		}
		if strings.HasPrefix(c.Name, "port:") {
			v := strings.Split(c.Name, ":")
			if len(v) != 3 || (v[1] != "tcp" && v[1] != "udp") {
				return nil, errors.New("port resource must be port:tcp:N or port:udp:N")
			}
			n, e := strconv.Atoi(v[2])
			if e != nil || n < 1 || n > 65535 {
				return nil, errors.New("invalid port resource")
			}
			c.Name = "port:" + v[1] + ":" + strconv.Itoa(n)
		} else if !resourceNamePattern.MatchString(c.Name) {
			return nil, errors.New("resource must be port, process or shared with a bounded identifier")
		}
		if m[c.Name] != "write" {
			m[c.Name] = c.Mode
		}
	}
	out := []resourceClaim{}
	for n, mode := range m {
		out = append(out, resourceClaim{n, mode})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Name < out[j].Name })
	return out, nil
}
func entryReadOnly(e *queueEntry) bool {
	return e.Request.Operation == "command_start" && (e.Request.Profile == "git_status" || e.Request.Profile == "git_diff")
}

// Resolve the actual shared Git metadata, including linked worktrees. Never run
// hooks, config, a shell or user project code to discover repository identity.
func repositoryIdentity(dir string) (string, error) {
	for current := filepath.Clean(dir); ; current = filepath.Dir(current) {
		dot := filepath.Join(current, ".git")
		st, err := os.Lstat(dot)
		if err == nil {
			meta := dot
			if !st.IsDir() {
				b, e := os.ReadFile(dot)
				if e != nil {
					return "", e
				}
				if len(b) > 16384 || !strings.HasPrefix(string(b), "gitdir: ") {
					return "", errors.New("unrecognized Git metadata")
				}
				meta = strings.TrimSpace(strings.TrimPrefix(string(b), "gitdir: "))
				if !filepath.IsAbs(meta) {
					meta = filepath.Join(current, meta)
				}
			}
			b, e := os.ReadFile(filepath.Join(meta, "commondir"))
			if e == nil {
				if len(b) > 16384 {
					return "", errors.New("Git common directory too large")
				}
				common := strings.TrimSpace(string(b))
				if !filepath.IsAbs(common) {
					common = filepath.Join(meta, common)
				}
				meta = common
			} else if !os.IsNotExist(e) {
				return "", e
			}
			meta, e = filepath.EvalSymlinks(meta)
			if e != nil {
				return "", e
			}
			return "repository:" + digest([]byte(strings.ToLower(filepath.Clean(meta)))), nil
		}
		if !os.IsNotExist(err) {
			return "", err
		}
		if filepath.Dir(current) == current {
			break
		}
	}
	return "", nil
}
func claimsForRequest(p runtimeRequest, dir string) ([]resourceClaim, error) {
	out, err := normalizedClaims(p.Resources)
	if err != nil {
		return nil, err
	}
	repo, err := repositoryIdentity(dir)
	if err != nil {
		return nil, err
	}
	if repo != "" {
		mode := "read"
		if p.Operation == "codex_turn" || p.Profile == "git_fetch" || p.Profile == "git_pull_ff" {
			mode = "write"
		}
		out = append(out, resourceClaim{repo, mode})
	}
	return out, nil
}
func sameClaims(a, b []resourceClaim) bool {
	x, _ := json.Marshal(a)
	y, _ := json.Marshal(b)
	return string(x) == string(y)
}
func queueEntriesConflict(a, b *queueEntry) bool {
	if queuePathsOverlap(a.Resource, b.Resource) {
		return true
	}
	for _, x := range a.Claims {
		for _, y := range b.Claims {
			if x.Name == y.Name && (x.Mode == "write" || y.Mode == "write") {
				return true
			}
		}
	}
	return false
}
func (r *Runtime) queueBlockersLocked(e *queueEntry) []string {
	out := []string{}
	for _, v := range r.queue.Entries {
		if v.ID != e.ID && (v.Status == "starting" || v.Status == "running" || v.Status == "uncertain" || v.Status == "persistence_failed") && queueEntriesConflict(v, e) {
			out = append(out, v.ID)
		}
	}
	return out
}
