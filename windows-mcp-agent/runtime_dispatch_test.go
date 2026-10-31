package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestSlowQueuedCodexDoesNotDelayIndependentProject(t *testing.T) {
	r, dir := fixtureRuntime(t)
	t.Setenv("JARVIS_CODEX_ENABLED", "1")
	resource := holdQueue(t, r, "sample")
	os.Mkdir(filepath.Join(filepath.Dir(dir), "other"), 0700)
	other := holdQueue(t, r, "other")
	thread := newID()
	if err := atomicJSON(filepath.Join(r.dir, "threads", thread+".json"), codexLink{thread, "sample", resource, "test", r.MachineID, utcNow()}); err != nil {
		t.Fatal(err)
	}
	w := &rpcTestInput{make(chan codexMessage, 16)}
	c := &codexClient{input: w, cancel: func() {}, dir: r.dir, machineID: r.MachineID, waiters: map[string]chan codexMessage{}, pending: map[string]codexPending{}, loaded: map[string]bool{thread: true}, active: map[string]string{}, finished: map[string]string{}}
	withQueue(r, func() { r.codex = c })
	v, err := r.call(runtimeRequest{Operation: "codex_turn", Project: "sample", ThreadID: thread, Text: "fixture", RequestID: newID(), MachineID: r.MachineID})
	if err != nil {
		t.Fatal(err)
	}
	id := v.(map[string]any)["queue_id"].(string)
	command := addQueueTest(t, r, "other", "independent", nil)
	done := make(chan struct{})
	go func() {
		withQueue(r, func() { delete(r.queue.Paused, resource); delete(r.queue.Paused, other); r.queueDispatchLocked() })
		close(done)
	}()
	var pending codexMessage
	select {
	case pending = <-w.requests:
	case <-time.After(time.Second):
		t.Fatal("Codex preflight did not start")
	}
	// Always release the fake RPC even if a following assertion fails.
	defer func() {
		c.mu.Lock()
		ch := c.waiters[c.nextIDString(pending.ID)]
		c.mu.Unlock()
		if ch != nil {
			select {
			case ch <- codexMessage{Error: json.RawMessage(`{"message":"fixture disconnect"}`)}:
			default:
			}
		}
	}()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("scheduler waits on Codex preflight")
	}
	checked := make(chan bool, 1)
	go func() {
		withQueue(r, func() {
			checked <- r.queueFindLocked(command).JobID != "" && r.queueFindLocked(id).Status == "starting"
		})
	}()
	select {
	case ok := <-checked:
		if !ok {
			t.Fatal("independent dispatch missing or reservation lost")
		}
	case <-time.After(time.Second):
		t.Fatal("runtime locked by starting Codex")
	}
}
