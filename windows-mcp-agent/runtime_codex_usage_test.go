package main

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestCodexUsageSummaryKeepsUnknownCreditsAndExhaustedQuota(t *testing.T) {
	var q codexQuota
	err := json.Unmarshal([]byte(`{"primary":{"usedPercent":100,"resetsAt":2000000000},"secondary":{"usedPercent":12.5,"resetsAt":2000001000},"credits":{"hasCredits":false,"unlimited":false,"balance":null}}`), &q)
	if err != nil { t.Fatal(err) }
	s := codexUsageSummary(q)
	w := s["windows"].(map[string]any)
	if w["primary"].(map[string]any)["remaining_percent"] != float64(0) ||
		w["secondary"].(map[string]any)["remaining_percent"] != float64(87.5) {
		t.Fatal("wrong remaining quota", s)
	}
	if s["credits"].(map[string]any)["status"] != "unknown" {
		t.Fatal("null credit balance became zero", s)
	}
	q.Credits.Balance = new(string)
	*q.Credits.Balance = "0"
	b, _ := json.Marshal(codexUsageSummary(q))
	if !strings.Contains(string(b), `"balance":"0"`) ||
		!strings.Contains(string(b), `"status":"known"`) { t.Fatal(string(b)) }
	invalid := 101.0
	q.Primary.Used = &invalid
	if codexUsageSummary(q)["windows"].(map[string]any)["primary"].(map[string]any)["remaining_percent"] != nil {
		t.Fatal("invalid percentage was normalized")
	}
}

// A real child process exercises the reader/RPC interlock without any model,
// credentials or network. Duplicate completion must cause only one quota read.
func TestCodexUsageReaderCompletionAndFailure(t *testing.T) {
	for _, mode := range []string{"completed", "interrupted", "failed", "rpc-error"} {
		t.Run(mode, func(t *testing.T) {
			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()
			cmd := exec.CommandContext(ctx, os.Args[0], "-test.run=^TestCodexUsageHelperProcess$")
			cmd.Env = append(os.Environ(), "JARVIS_USAGE_HELPER="+mode)
			input, err := cmd.StdinPipe()
			if err != nil { t.Fatal(err) }
			output, err := cmd.StdoutPipe()
			if err != nil { t.Fatal(err) }
			dir := t.TempDir()
			if err = os.Mkdir(filepath.Join(dir, "events"), 0700); err != nil { t.Fatal(err) }
			c := &codexClient{cmd: cmd, input: input, cancel: cancel, dir: dir,
				waiters: map[string]chan codexMessage{}, pending: map[string]codexPending{},
				loaded: map[string]bool{"test-thread": true}, active: map[string]string{},
				finished: map[string]string{}}
			if err = cmd.Start(); err != nil { t.Fatal(err) }
			done := make(chan struct{})
			go func() { c.readLoop(output); close(done) }()
			defer func() { c.Close(); select { case <-done: case <-time.After(3*time.Second): t.Error("reader did not exit") } }()
			var result map[string]any
			deadline := time.Now().Add(8*time.Second)
			for time.Now().Before(deadline) {
				v, e := c.events(runtimeRequest{ThreadID: "test-thread"})
				if e != nil { t.Fatal(e) }
				result = v.(map[string]any)
				if result["final_usage"] != nil {
					raw := result["final_usage"].(json.RawMessage)
					if len(raw) > 0 { break }
				}
				time.Sleep(10*time.Millisecond)
			}
			raw, ok := result["final_usage"].(json.RawMessage)
			if !ok || len(raw) == 0 { t.Fatal("final usage never arrived", result) }
			var report map[string]any
			if err = json.Unmarshal(raw, &report); err != nil { t.Fatal(err) }
			if report["turn_id"] != "test-turn" || report["phase"] != "after" { t.Fatal(report) }
			if mode == "rpc-error" {
				if report["available"] != false || report["error"] != "quota_read_failed" { t.Fatal(report) }
			} else {
				if report["available"] != true || report["turn_status"] != mode { t.Fatal(report) }
				status := report["status"].(map[string]any)
				if status["inference_allowed"] != false { t.Fatal("test exhausted quota unexpectedly eligible") }
				usage := status["usage"].(map[string]any)
				if usage["windows"].(map[string]any)["primary"].(map[string]any)["remaining_percent"] != float64(0) {
					t.Fatal("exhausted quota hidden from report", usage)
				}
				if usage["credits"].(map[string]any)["status"] != "unknown" { t.Fatal(usage) }
			}
			c.mu.Lock()
			var all []codexEvent
			b, err := os.ReadFile(filepath.Join(dir, "events", "test-thread.json"))
			if err == nil { err = json.Unmarshal(b, &all) }
			c.mu.Unlock()
			if err != nil { t.Fatal(err) }
			count := 0
			for _, e := range all { if e.Method == "jarvis/usage" { count++ } }
			if count != 1 || result["usage_pending"] != false { t.Fatal("duplicate or pending final reading", count, result) }
		})
	}
}

func TestCodexUsageHelperProcess(t *testing.T) {
	mode := os.Getenv("JARVIS_USAGE_HELPER")
	if mode == "" { return }
	enc := json.NewEncoder(os.Stdout)
	status := mode
	if mode == "rpc-error" { status = "failed" }
	notification := map[string]any{"method":"turn/completed", "params":map[string]any{"threadId":"test-thread", "turn":map[string]any{"id":"test-turn","status":status}}}
	enc.Encode(notification)
	enc.Encode(notification)
	scanner := bufio.NewScanner(os.Stdin)
	accountReads, quotaReads := 0, 0
	for scanner.Scan() {
		var m codexMessage
		if json.Unmarshal(scanner.Bytes(), &m) != nil { os.Exit(2) }
		switch m.Method {
		case "account/read":
			accountReads++
			if accountReads > 1 { fmt.Fprintln(os.Stderr, "duplicate account read"); os.Exit(3) }
			if mode == "rpc-error" {
				enc.Encode(map[string]any{"id":m.ID,"error":map[string]any{"code":-1,"message":"test service unavailable"}})
			} else {
				enc.Encode(map[string]any{"id":m.ID,"result":map[string]any{"account":map[string]any{"type":"chatgpt","planType":"self_serve_business_prolite","email":"fixture@example.invalid"}}})
			}
		case "account/rateLimits/read":
			quotaReads++
			if quotaReads > 1 { fmt.Fprintln(os.Stderr, "duplicate quota read"); os.Exit(4) }
			enc.Encode(map[string]any{"id":m.ID,"result":json.RawMessage(`{"rateLimits":{"limitId":"codex","primary":{"usedPercent":100,"resetsAt":2000000000},"credits":{"hasCredits":false,"unlimited":false,"balance":null}}}`)})
		default:
			os.Exit(5)
		}
	}
	os.Exit(0)
}
