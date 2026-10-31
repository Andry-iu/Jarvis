package main

import (
	"encoding/json"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestArchiveCompactsAndPreservesDependencies(t *testing.T) {
	r, _ := fixtureRuntime(t)
	holdQueue(t, r, "sample")
	a := addQueueTest(t, r, "sample", "finished", nil)
	b := addQueueTest(t, r, "sample", "dependent", []string{a})
	withQueue(r, func() {
		r.queueFindLocked(a).Status = "completed"
		r.queueFindLocked(a).FinishedAt = utcNow()
		if err := r.queueSaveLocked(); err != nil {
			t.Fatal(err)
		}
		if len(r.queue.Entries) != 1 || r.queue.Entries[0].ID != b {
			t.Fatal("terminal entry retained in operative queue")
		}
		if r.queueFindLocked(a).Status != "completed" {
			t.Fatal("archived dependency missing")
		}
	})
	v, err := r.activityHistory(runtimeRequest{QueueID: a, Project: "sample"})
	if err != nil {
		t.Fatal(err)
	}
	if v.(map[string]any)["activity"].(map[string]any)["status"] != "completed" {
		t.Fatal(v)
	}
	withQueue(r, func() {
		if err := r.queueSaveLocked(); err != nil {
			t.Fatal("idempotent compaction", err)
		}
	})
	dir := r.dir
	r.Close()
	again, err := newRuntime(dir)
	if err != nil {
		t.Fatal(err)
	}
	defer again.Close()
	if len(again.queue.Entries) != 1 || again.queueFindLocked(a).Status != "completed" {
		t.Fatal("restart lost archive/dependency")
	}
}
func TestArchiveCrashWindowAndFailureNeverDropsEntry(t *testing.T) {
	r, _ := fixtureRuntime(t)
	holdQueue(t, r, "sample")
	a := addQueueTest(t, r, "sample", "done", nil)
	withQueue(r, func() {
		e := r.queueFindLocked(a)
		e.Status = "cancelled"
		e.FinishedAt = utcNow()
		// Simulate archive persisted before queue.json replacement: restart must deduplicate.
		if _, err := r.archiveTerminalLocked(); err != nil {
			t.Fatal(err)
		}
		if err := atomicJSON(filepath.Join(r.dir, "queue.json"), r.queue); err != nil {
			t.Fatal(err)
		}
	})
	dir := r.dir
	r.Close()
	again, err := newRuntime(dir)
	if err != nil {
		t.Fatal(err)
	}
	defer again.Close()
	if len(again.queue.Entries) != 0 {
		t.Fatal("crash window duplicated history")
	}
	holdQueue(t, again, "sample")
	b := addQueueTest(t, again, "sample", "blocked", nil)
	withQueue(again, func() {
		e := again.queueFindLocked(b)
		e.Status = "completed"
		path, _ := again.archivePath(b)
		os.MkdirAll(filepath.Dir(path), 0700)
		os.WriteFile(path, []byte("conflict"), 0600)
		if again.queueSaveLocked() == nil || !again.queueBroken || len(again.queue.Entries) != 1 {
			t.Fatal("archive conflict dropped or overwrote activity")
		}
	})
}
func TestArchiveHistoryPagesAndProjectFilter(t *testing.T) {
	r, _ := fixtureRuntime(t)
	withQueue(r, func() {
		for i := 0; i < 205; i++ {
			r.queue.Entries = append(r.queue.Entries, &queueEntry{ID: newID(), Project: "sample", Resource: filepath.Join(r.dir, "test"), Status: "completed", Request: runtimeRequest{Operation: "command_start", MachineID: r.MachineID, Project: "sample"}})
		}
		if err := r.queueSaveLocked(); err != nil {
			t.Fatal(err)
		}
		if len(r.queue.Entries) != 0 {
			t.Fatal("queue not compacted")
		}
	})
	for offset, want := range map[int]int{0: 200, 200: 5, 205: 0} {
		v, err := r.activityHistory(runtimeRequest{Project: "sample", Offset: offset, Limit: 200})
		if err != nil {
			t.Fatal(err)
		}
		if len(v.(map[string]any)["activities"].([]any)) != want {
			t.Fatal("wrong archive page", offset, v)
		}
	}
	v, err := r.activityHistory(runtimeRequest{Project: "other"})
	if err != nil || len(v.(map[string]any)["activities"].([]any)) != 0 {
		t.Fatal("project leakage", v, err)
	}
}
func TestResourcesConflictAndSharedWorktreeIdentity(t *testing.T) {
	dir := t.TempDir()
	common := filepath.Join(dir, "repo", ".git")
	os.MkdirAll(filepath.Join(common, "worktrees", "two"), 0700)
	one := filepath.Join(dir, "repo")
	two := filepath.Join(dir, "two")
	os.Mkdir(two, 0700)
	os.WriteFile(filepath.Join(two, ".git"), []byte("gitdir: "+filepath.Join(common, "worktrees", "two")), 0600)
	os.WriteFile(filepath.Join(common, "worktrees", "two", "commondir"), []byte("../.."), 0600)
	a, err := repositoryIdentity(one)
	if err != nil {
		t.Fatal(err)
	}
	b, err := repositoryIdentity(two)
	if err != nil || a == "" || a != b {
		t.Fatal("linked worktree common repo missed", a, b, err)
	}
	x := &queueEntry{ID: newID(), Resource: one, Claims: []resourceClaim{{"port:tcp:8080", "write"}}, Request: runtimeRequest{Operation: "command_start", Profile: "go_test"}}
	y := &queueEntry{ID: newID(), Resource: two, Claims: []resourceClaim{{"port:tcp:8080", "read"}}, Request: runtimeRequest{Operation: "command_start", Profile: "go_test"}}
	if !queueEntriesConflict(x, y) {
		t.Fatal("shared port conflict missed")
	}
	x.Claims[0].Mode = "read"
	if queueEntriesConflict(x, y) {
		t.Fatal("compatible reads serialized")
	}
	x.Claims = []resourceClaim{{a, "write"}}
	y.Claims = []resourceClaim{{b, "read"}}
	if !queueEntriesConflict(x, y) {
		t.Fatal("Git metadata conflict missed")
	}
	for _, name := range []string{"port:tcp:0", "port:udp:65536", "port:http:80", "repository:any", "shared:../escape"} {
		if _, err := normalizedClaims([]resourceClaim{{name, "write"}}); err == nil {
			t.Fatal("unsafe resource accepted", name)
		}
	}
	c, err := normalizedClaims([]resourceClaim{{"port:TCP:08080", "read"}, {"port:tcp:8080", "write"}})
	if err != nil || len(c) != 1 || c[0].Name != "port:tcp:8080" || c[0].Mode != "write" {
		t.Fatal(c, err)
	}
}
func TestSchedulerHonorsSharedResourcesWithoutBlockingIndependentWork(t *testing.T) {
	r, root := fixtureRuntime(t)
	res := holdQueue(t, r, "sample")
	for _, name := range []string{"other", "third"} {
		os.Mkdir(filepath.Join(filepath.Dir(root), name), 0700)
		holdQueue(t, r, name)
	}
	a := addQueueTest(t, r, "sample", "A", nil)
	b := addQueueTest(t, r, "other", "B", nil)
	c := addQueueTest(t, r, "third", "C", nil)
	withQueue(r, func() {
		ea := r.queueFindLocked(a)
		ea.Status = "running"
		ea.JobID = "fake-job"
		ea.Claims = []resourceClaim{{"shared:compiler", "write"}}
		r.Jobs[ea.JobID] = &Job{Status: "running"}
		eb := r.queueFindLocked(b)
		eb.Claims = []resourceClaim{{"shared:compiler", "write"}}
		eb.Request.Resources = eb.Claims
		delete(r.queue.Paused, res)
		for path := range r.queue.Paused {
			delete(r.queue.Paused, path)
		}
		r.queueDispatchLocked()
		if r.queueFindLocked(b).Status != "queued" || r.queueFindLocked(c).JobID == "" {
			t.Fatal("shared lock blocked independent work or failed to protect resource")
		}
	})
}

type rpcTestInput struct{ requests chan codexMessage }

func (w *rpcTestInput) Write(b []byte) (int, error) {
	var m codexMessage
	if err := json.Unmarshal(b, &m); err != nil {
		return 0, err
	}
	w.requests <- m
	return len(b), nil
}
func (w *rpcTestInput) Close() error { return nil }
func TestSlowCodexRPCDoesNotBlockQueueJobsOrInterruption(t *testing.T) {
	r, _ := fixtureRuntime(t)
	t.Setenv("JARVIS_CODEX_ENABLED", "1")
	holdQueue(t, r, "sample")
	w := &rpcTestInput{make(chan codexMessage, 16)}
	c := &codexClient{input: w, cancel: func() {}, dir: r.dir, machineID: r.MachineID, waiters: map[string]chan codexMessage{}, pending: map[string]codexPending{}, loaded: map[string]bool{}, active: map[string]string{}, finished: map[string]string{}}
	withQueue(r, func() { r.codex = c })
	done := make(chan error, 1)
	go func() { _, e := r.codexCall(runtimeRequest{Operation: "codex_status"}); done <- e }()
	var request codexMessage
	select {
	case request = <-w.requests:
	case <-time.After(time.Second):
		t.Fatal("RPC not started")
	}
	checks := make(chan error, 1)
	go func() {
		_, e := r.call(runtimeRequest{Operation: "queue_list"})
		if e == nil {
			_, e = r.call(runtimeRequest{Operation: "job_list"})
		}
		checks <- e
	}()
	select {
	case e := <-checks:
		if e != nil {
			t.Fatal(e)
		}
	case <-time.After(time.Second):
		t.Fatal("slow RPC owns global state mutex")
	}
	// Interruption must be dispatched before the slow quota RPC finishes.
	thread, turn := newID(), newID()
	root, err := r.projectResource("sample")
	if err != nil {
		t.Fatal(err)
	}
	if err = atomicJSON(filepath.Join(r.dir, "threads", thread+".json"), codexLink{thread, "sample", root, "test", r.MachineID, utcNow()}); err != nil {
		t.Fatal(err)
	}
	c.mu.Lock()
	c.loaded[thread] = true
	c.active[thread] = turn
	c.mu.Unlock()
	interrupted := make(chan error, 1)
	go func() {
		_, e := r.codexCall(runtimeRequest{Operation: "codex_interrupt", Project: "sample", ThreadID: thread, TurnID: turn, RequestID: newID()})
		interrupted <- e
	}()
	select {
	case m := <-w.requests:
		if m.Method != "turn/interrupt" {
			t.Fatal(m.Method)
		}
		c.mu.Lock()
		ch := c.waiters[c.nextIDString(m.ID)]
		c.mu.Unlock()
		ch <- codexMessage{Result: json.RawMessage(`{}`)}
	case <-time.After(time.Second):
		t.Fatal("interruption blocked by quota RPC")
	}
	select {
	case e := <-interrupted:
		if e != nil {
			t.Fatal(e)
		}
	case <-time.After(time.Second):
		t.Fatal("interrupt response blocked")
	}
	c.mu.Lock()
	ch := c.waiters[c.nextIDString(request.ID)]
	c.mu.Unlock()
	ch <- codexMessage{Error: json.RawMessage(`{"message":"simulated disconnect"}`)}
	if err := <-done; err == nil {
		t.Fatal("service error lost")
	}
}
func (c *codexClient) nextIDString(raw json.RawMessage) string { return strings.Trim(string(raw), `"`) }

var _ io.WriteCloser = (*rpcTestInput)(nil)
