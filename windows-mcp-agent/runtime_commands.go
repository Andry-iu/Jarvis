package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"
)

const maxJobOutput = 64 * 1024

var commandProfiles = []string{"go_test", "go_build", "go_vet", "git_status", "git_diff", "git_fetch", "git_pull_ff", "dotnet_test", "dotnet_build", "cmake_configure", "cmake_build", "msbuild"}

func childEnvironment() []string {
	allowed := map[string]bool{}
	for _, n := range []string{"SYSTEMROOT", "WINDIR", "PATH", "PATHEXT", "COMSPEC", "USERPROFILE", "HOMEDRIVE", "HOMEPATH", "APPDATA", "LOCALAPPDATA", "PROGRAMDATA", "PROGRAMFILES", "PROGRAMFILES(X86)", "TEMP", "TMP", "GOCACHE", "GOPATH", "GOROOT", "DOTNET_ROOT", "NUMBER_OF_PROCESSORS", "PROCESSOR_ARCHITECTURE"} {
		allowed[n] = true
	}
	out := []string{}
	for _, v := range os.Environ() {
		k, _, _ := strings.Cut(v, "=")
		if allowed[strings.ToUpper(k)] {
			out = append(out, v)
		}
	}
	return append(out, "GOWORK=off", "GOTOOLCHAIN=local", "GIT_TERMINAL_PROMPT=0", "DOTNET_CLI_TELEMETRY_OPTOUT=1", "DOTNET_NOLOGO=1")
}
func configureTreeCancellation(c *exec.Cmd) {
	c.WaitDelay = 2 * time.Second
	if runtime.GOOS == "windows" {
		c.Cancel = func() error {
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()
			k := exec.CommandContext(ctx, filepath.Join(os.Getenv("SystemRoot"), "System32", "taskkill.exe"), "/PID", strconv.Itoa(c.Process.Pid), "/T", "/F")
			configureDesktopCommand(k)
			k.Env = childEnvironment()
			return k.Run()
		}
	}
}
func commandSpec(profile, dir, id string) (string, []string, error) {
	switch profile {
	case "go_test", "go_build", "go_vet":
		if st, e := os.Stat(filepath.Join(dir, "go.mod")); e != nil || !st.Mode().IsRegular() {
			return "", nil, errors.New("project must be a Go module root")
		}
		if profile == "go_test" {
			return "go", []string{"test", "-count=1", "./..."}, nil
		}
		if profile == "go_vet" {
			return "go", []string{"vet", "./..."}, nil
		}
		staging := filepath.Join(dir, "Staging")
		st, e := os.Lstat(staging)
		if os.IsNotExist(e) {
			e = os.Mkdir(staging, 0700)
		} else if e == nil && (!st.IsDir() || st.Mode()&os.ModeSymlink != 0 || isReparse(st)) {
			e = errors.New("unsafe staging directory")
		}
		if e != nil {
			return "", nil, e
		}
		return "go", []string{"build", "-o", filepath.Join(staging, "build-"+id+".exe"), "."}, nil
	case "git_status":
		return "git", []string{"status", "--porcelain=v1", "-b"}, nil
	case "git_diff":
		return "git", []string{"--no-pager", "diff", "--no-ext-diff", "--no-textconv"}, nil
	case "git_fetch":
		return "git", []string{"-c", "core.hooksPath=NUL", "fetch", "--no-recurse-submodules", "origin"}, nil
	case "git_pull_ff":
		return "git", []string{"-c", "core.hooksPath=NUL", "pull", "--ff-only", "--no-recurse-submodules"}, nil
	case "dotnet_test":
		return "dotnet", []string{"test", "--nologo"}, nil
	case "dotnet_build":
		return "dotnet", []string{"build", "--nologo"}, nil
	case "cmake_configure":
		return "cmake", []string{"-S", ".", "-B", "Staging/cmake"}, nil
	case "cmake_build":
		return "cmake", []string{"--build", "Staging/cmake", "--config", "Debug"}, nil
	case "msbuild":
		return "MSBuild", []string{"/nologo", "/t:Build", "/p:Configuration=Debug"}, nil
	}
	return "", nil, errors.New("unsupported command profile; shell strings and arbitrary arguments are not accepted")
}
func (r *Runtime) startCommand(p runtimeRequest) (any, error) {
	if p.Timeout == 0 {
		p.Timeout = 120
	}
	if p.Timeout < 1 || p.Timeout > 600 {
		return nil, errors.New("timeout_seconds must be 1-600")
	}
	root, e := r.projectRoot(p.Project)
	if e != nil {
		return nil, e
	}
	dir := root.Name()
	root.Close()
	for _, j := range r.Jobs {
		if j.Status == "running" {
			return nil, errors.New("a persistent command is already running on this computer")
		}
	}
	id := newID()
	exe, args, e := commandSpec(p.Profile, dir, id)
	if e != nil {
		return nil, e
	}
	exe, e = exec.LookPath(exe)
	if e != nil {
		return nil, errors.New("requested toolchain is not installed or not in agent PATH")
	}
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(p.Timeout)*time.Second)
	j := &Job{ID: id, MachineID: r.MachineID, Project: p.Project, Kind: p.Profile, Status: "running", CreatedAt: utcNow(), cancel: cancel}
	if e = r.saveJob(j); e != nil {
		cancel()
		return nil, e
	}
	r.Jobs[id] = j
	go r.runCommand(ctx, cancel, j, dir, exe, args)
	return map[string]any{"job_id": id, "machine_id": r.MachineID, "project": p.Project, "status": "running"}, nil
}

type jobWriter struct {
	r      *Runtime
	j      *Job
	stream string
}

func (w jobWriter) Write(b []byte) (int, error) {
	n := len(b)
	w.r.mu.Lock()
	defer w.r.mu.Unlock()
	s := w.j.Output + string(b)
	if len(s) > maxJobOutput {
		drop := len(s) - maxJobOutput
		for drop < len(s) && !utf8.RuneStart(s[drop]) {
			drop++
		}
		w.j.DroppedBytes += int64(drop)
		s = s[drop:]
	}
	w.j.Output = strings.ToValidUTF8(s, "�")
	target := &w.j.Stdout
	if w.stream == "stderr" {
		target = &w.j.Stderr
	}
	s = *target + string(b)
	if len(s) > maxJobOutput/2 {
		s = s[len(s)-maxJobOutput/2:]
	}
	*target = strings.ToValidUTF8(s, "�")
	return n, nil
}
func (r *Runtime) runCommand(ctx context.Context, cancel context.CancelFunc, j *Job, dir, exe string, args []string) {
	defer cancel()
	c := exec.CommandContext(ctx, exe, args...)
	c.Dir = dir
	c.Env = childEnvironment()
	c.Stdout = jobWriter{r, j, "stdout"}
	c.Stderr = jobWriter{r, j, "stderr"}
	configureTreeCancellation(c)
	configureDesktopCommand(c)
	done := make(chan struct{})
	go func() {
		ticker := time.NewTicker(time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-done:
				return
			case <-ticker.C:
				r.mu.Lock()
				e := r.saveJob(j)
				r.mu.Unlock()
				if e != nil {
					cancel()
					return
				}
			}
		}
	}()
	err := runManagedCommand(c)
	close(done)
	r.mu.Lock()
	defer r.mu.Unlock()
	j.FinishedAt = utcNow()
	j.cancel = nil
	exit := -1
	if c.ProcessState != nil {
		exit = c.ProcessState.ExitCode()
	}
	j.ExitCode = &exit
	switch {
	case errors.Is(ctx.Err(), context.DeadlineExceeded):
		j.Status = "timed_out"
	case ctx.Err() != nil:
		j.Status = "cancelled"
	case err != nil:
		j.Status = "failed"
	default:
		j.Status = "completed"
	}
	if err != nil {
		j.Output += fmt.Sprintf("\nprocess: %v", err)
		if len(j.Output) > maxJobOutput {
			j.Output = j.Output[len(j.Output)-maxJobOutput:]
		}
	}
	if e := r.saveJob(j); e != nil {
		j.Status = "persistence_failed"
		j.Output = "Final result could not be persisted; inspect execution before any retry."
	}
}
func (r *Runtime) jobCall(p runtimeRequest) (any, error) {
	if p.Operation == "job_cancel" {
		return r.mutation(p, func(x *Receipt) (any, error) {
			j := r.Jobs[p.JobID]
			if j == nil {
				return nil, errors.New("job not found")
			}
			if j.Status != "running" || j.cancel == nil {
				return nil, errors.New("job is not running")
			}
			j.cancel()
			return map[string]any{"job_id": j.ID, "cancellation_requested": true}, nil
		})
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	if p.Operation == "job_status" {
		j := r.Jobs[p.JobID]
		if j == nil {
			return nil, errors.New("job not found")
		}
		v := *j
		v.cancel = nil
		return v, nil
	}
	out := []Job{}
	for _, j := range r.Jobs {
		v := *j
		v.cancel = nil
		v.Output = ""
		v.Stdout = ""
		v.Stderr = ""
		out = append(out, v)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].CreatedAt > out[j].CreatedAt })
	limit := p.Limit
	if limit == 0 {
		limit = 50
	}
	start := p.Offset
	if start > len(out) {
		start = len(out)
	}
	end := start + limit
	if end > len(out) {
		end = len(out)
	}
	return map[string]any{"jobs": out[start:end], "next_offset": end, "total": len(out)}, nil
}
