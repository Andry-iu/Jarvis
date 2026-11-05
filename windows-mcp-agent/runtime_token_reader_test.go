package main

import (
	"bufio"
	"context"
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
	"time"
)

func TestTokenMeterReaderIntegration(t *testing.T) {
	_, c, thread := meterFixture(t, true)
	if err := os.MkdirAll(filepath.Join(c.dir, "events"), 0700); err != nil {
		t.Fatal(err)
	}
	if err := c.beginTokenRun(runtimeRequest{ThreadID: thread, RequestID: newID(), UsageCategory: "development"}, "fixture-model"); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	cmd := exec.CommandContext(ctx, os.Args[0], "-test.run=^TestTokenMeterReaderHelper$")
	cmd.Env = append(os.Environ(), "JARVIS_TOKEN_HELPER_THREAD="+thread)
	input, err := cmd.StdinPipe()
	if err != nil {
		t.Fatal(err)
	}
	output, err := cmd.StdoutPipe()
	if err != nil {
		t.Fatal(err)
	}
	c.cmd = cmd
	c.input = input
	c.cancel = cancel
	c.loaded = map[string]bool{thread: true}
	c.active = map[string]string{}
	c.finished = map[string]string{}
	c.pending = map[string]codexPending{}
	c.waiters = map[string]chan codexMessage{}
	if err = cmd.Start(); err != nil {
		t.Fatal(err)
	}
	done := make(chan struct{})
	go func() { c.readLoop(output); close(done) }()
	defer func() {
		c.Close()
		select {
		case <-done:
		case <-time.After(3 * time.Second):
			t.Error("reader did not exit")
		}
	}()
	deadline := time.Now().Add(8 * time.Second)
	for time.Now().Before(deadline) {
		v, e := c.events(runtimeRequest{ThreadID: thread})
		if e != nil {
			t.Fatal(e)
		}
		raw, _ := v.(map[string]any)["final_usage"].(json.RawMessage)
		if len(raw) > 0 {
			var report struct {
				Available  bool `json:"available"`
				TokenUsage struct {
					Category string         `json:"category"`
					Tokens   measuredTokens `json:"tokens"`
				} `json:"token_usage"`
			}
			if e = json.Unmarshal(raw, &report); e != nil {
				t.Fatal(e)
			}
			if report.Available || report.TokenUsage.Category != "development" || report.TokenUsage.Tokens.Total != 140 {
				t.Fatal(string(raw))
			}
			c.mu.Lock()
			l := meterLoad(t, c, thread)
			c.mu.Unlock()
			if len(l.Runs) != 1 || l.Runs[0].Tokens.Total != 140 {
				t.Fatal("reader double counted")
			}
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatal("reader final usage unavailable")
}
func TestTokenMeterReaderHelper(t *testing.T) {
	thread := os.Getenv("JARVIS_TOKEN_HELPER_THREAD")
	if thread == "" {
		return
	}
	enc := json.NewEncoder(os.Stdout)
	turn := "fixture-turn-0000001"
	enc.Encode(map[string]any{"method": "turn/started", "params": map[string]any{"threadId": thread, "turn": map[string]any{"id": turn}}})
	for _, n := range []int{80, 100, 100} {
		enc.Encode(map[string]any{"method": "thread/tokenUsage/updated", "params": map[string]any{"threadId": thread, "turnId": turn, "tokenUsage": map[string]any{"total": map[string]int{"inputTokens": n, "outputTokens": 40, "totalTokens": n + 40}}}})
	}
	for i := 0; i < 2; i++ {
		enc.Encode(map[string]any{"method": "turn/completed", "params": map[string]any{"threadId": thread, "turn": map[string]any{"id": turn, "status": "completed"}}})
	}
	scanner := bufio.NewScanner(os.Stdin)
	for scanner.Scan() {
		var m codexMessage
		if json.Unmarshal(scanner.Bytes(), &m) != nil {
			os.Exit(2)
		}
		enc.Encode(map[string]any{"id": m.ID, "error": map[string]any{"code": -1, "message": "fixture: quota unavailable"}})
	}
	os.Exit(0)
}
