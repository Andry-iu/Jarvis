package main

import (
	"encoding/json"
	"math"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func meterFixture(t *testing.T, fresh bool) (*Runtime, *codexClient, string) {
	t.Helper()
	r, _ := fixtureRuntime(t)
	c := &codexClient{dir: r.dir, machineID: r.MachineID}
	thread := newID()
	if err := c.initTokenMeter(thread, "sample", fresh); err != nil {
		t.Fatal(err)
	}
	return r, c, thread
}
func meterEvent(t *testing.T, c *codexClient, thread, method string, p any) {
	t.Helper()
	b, err := json.Marshal(p)
	if err != nil {
		t.Fatal(err)
	}
	if err = c.captureTokenEvent(thread, method, b); err != nil {
		t.Fatal(err)
	}
}
func meterStart(t *testing.T, c *codexClient, thread, category string) string {
	t.Helper()
	turn := newID()
	if err := c.beginTokenRun(runtimeRequest{ThreadID: thread, RequestID: newID(), UsageCategory: category, Text: "PRIVATE DIALOGUE MUST NOT BE STORED"}, "fixture-model"); err != nil {
		t.Fatal(err)
	}
	meterEvent(t, c, thread, "turn/started", map[string]any{"turn": map[string]any{"id": turn}})
	return turn
}
func meterSnapshot(t *testing.T, c *codexClient, thread, turn string, input, output int64) {
	t.Helper()
	meterEvent(t, c, thread, "thread/tokenUsage/updated", map[string]any{"turnId": turn, "tokenUsage": map[string]any{"total": map[string]any{"inputTokens": input, "cachedInputTokens": input / 2, "outputTokens": output, "reasoningOutputTokens": output / 2, "totalTokens": input + output}}})
}
func meterComplete(t *testing.T, c *codexClient, thread, turn, status string) {
	t.Helper()
	meterEvent(t, c, thread, "turn/completed", map[string]any{"turn": map[string]any{"id": turn, "status": status}})
}
func meterLoad(t *testing.T, c *codexClient, thread string) *tokenLedger {
	t.Helper()
	l, e := loadTokenLedger(c.dir, c.machineID, thread)
	if e != nil {
		t.Fatal(e)
	}
	return l
}
func meterReport(t *testing.T, r *Runtime, cat string, limit, offset int) map[string]any {
	t.Helper()
	v, e := r.call(runtimeRequest{Operation: "codex_token_usage", MachineID: r.MachineID, Project: "sample", UsageCategory: cat, Limit: limit, Offset: offset})
	if e != nil {
		t.Fatal(e)
	}
	return v.(map[string]any)
}
func TestTokenMeterSeparatesDevelopmentAndPreservesDialogue(t *testing.T) {
	r, c, thread := meterFixture(t, true)
	// An independent conversation file must stay byte-identical.
	conversation := filepath.Join(r.dir, "conversation-fixture.json")
	original := []byte(`{"dialogue":"keep this"}`)
	if e := os.WriteFile(conversation, original, 0600); e != nil {
		t.Fatal(e)
	}
	dev := meterStart(t, c, thread, "development")
	meterSnapshot(t, c, thread, dev, 80, 20)
	meterSnapshot(t, c, thread, dev, 80, 20)
	meterSnapshot(t, c, thread, dev, 100, 40)
	meterComplete(t, c, thread, dev, "completed")
	meterComplete(t, c, thread, dev, "completed")
	dialogue := meterStart(t, c, thread, "dialogue")
	meterSnapshot(t, c, thread, dialogue, 160, 60)
	meterComplete(t, c, thread, dialogue, "completed")
	unknown := meterStart(t, c, thread, "")
	meterSnapshot(t, c, thread, unknown, 180, 80)
	meterComplete(t, c, thread, unknown, "failed")
	report := meterReport(t, r, "", 1, 0)
	sum := report["summary"].(*tokenAggregate)
	if sum.Measured != 1 || sum.Tokens.Total != 140 || sum.Tokens.Input != 100 || *sum.Tokens.Cached != 50 || *sum.Tokens.Reasoning != 20 || report["unclassified_excluded"] != 1 {
		t.Fatal(report)
	}
	all := meterReport(t, r, "all", 1, 0)
	if all["total_matching"] != 3 || all["has_more"] != true || all["summary"].(*tokenAggregate).Tokens.Total != 260 {
		t.Fatal(all)
	}
	if meterReport(t, r, "dialogue", 50, 0)["summary"].(*tokenAggregate).Tokens.Total != 80 {
		t.Fatal("dialogue incorrectly attributed")
	}
	if len(meterReport(t, r, "all", 1, math.MaxInt)["records"].([]map[string]any)) != 0 {
		t.Fatal("invalid pagination")
	}
	data, _ := os.ReadFile(conversation)
	if string(data) != string(original) {
		t.Fatal("conversation changed")
	}
	p, _ := tokenLedgerPath(c.dir, thread)
	data, _ = os.ReadFile(p)
	if strings.Contains(string(data), "PRIVATE DIALOGUE") {
		t.Fatal("prompt stored in token ledger")
	}
	// Reopening a ledger must preserve totals without relying on the event ring.
	c2 := &codexClient{dir: c.dir, machineID: c.machineID}
	if got := meterLoad(t, c2, thread).Runs[0].Tokens.Total; got != 140 {
		t.Fatal(got)
	}
}
func TestTokenMeterUnknownBoundariesAndLateEvents(t *testing.T) {
	t.Run("resume", func(t *testing.T) {
		r, c, thread := meterFixture(t, false)
		a := meterStart(t, c, thread, "development")
		meterSnapshot(t, c, thread, a, 1000, 100)
		meterComplete(t, c, thread, a, "completed")
		if meterLoad(t, c, thread).Runs[0].Tokens != nil {
			t.Fatal("historical usage imported")
		}
		b := meterStart(t, c, thread, "development")
		meterSnapshot(t, c, thread, b, 1100, 140)
		meterComplete(t, c, thread, b, "interrupted")
		s := meterReport(t, r, "development", 50, 0)["summary"].(*tokenAggregate)
		if s.Measured != 1 || s.Unmeasured != 1 || s.Tokens.Total != 140 {
			t.Fatal(s)
		}
	})
	t.Run("missing previous snapshot", func(t *testing.T) {
		_, c, thread := meterFixture(t, true)
		a := meterStart(t, c, thread, "dialogue")
		meterComplete(t, c, thread, a, "completed")
		b := meterStart(t, c, thread, "development")
		meterSnapshot(t, c, thread, b, 300, 100)
		if meterLoad(t, c, thread).Runs[1].Tokens != nil {
			t.Fatal("unobserved dialogue counted as development")
		}
	})
	t.Run("late previous snapshot", func(t *testing.T) {
		_, c, thread := meterFixture(t, true)
		a := meterStart(t, c, thread, "dialogue")
		meterSnapshot(t, c, thread, a, 100, 20)
		meterComplete(t, c, thread, a, "completed")
		b := meterStart(t, c, thread, "development")
		meterSnapshot(t, c, thread, b, 200, 60)
		meterSnapshot(t, c, thread, a, 120, 40)
		l := meterLoad(t, c, thread)
		if l.Runs[1].Tokens != nil || l.Runs[1].Coverage != "late_previous_turn" {
			t.Fatal(l.Runs[1])
		}
		meterSnapshot(t, c, thread, b, 240, 80)
		if meterLoad(t, c, thread).Runs[1].Tokens != nil {
			t.Fatal("ambiguous boundary recovered falsely")
		}
	})
	t.Run("reset", func(t *testing.T) {
		_, c, thread := meterFixture(t, true)
		a := meterStart(t, c, thread, "development")
		meterSnapshot(t, c, thread, a, 100, 40)
		meterSnapshot(t, c, thread, a, 20, 10)
		meterSnapshot(t, c, thread, a, 120, 60)
		if meterLoad(t, c, thread).Runs[0].Tokens != nil {
			t.Fatal("reset counted")
		}
	})
	t.Run("missing id", func(t *testing.T) {
		_, c, thread := meterFixture(t, true)
		a := meterStart(t, c, thread, "development")
		meterSnapshot(t, c, thread, "", 100, 40)
		if meterLoad(t, c, thread).Runs[0].Tokens != nil {
			t.Fatal("unknown turn counted")
		}
		meterComplete(t, c, thread, a, "failed")
	})
}
func TestTokenMeterRejectsMalformedCountersAndCorruption(t *testing.T) {
	for _, raw := range []string{`null`, `{}`, `{"inputTokens":1,"outputTokens":2,"totalTokens":4}`, `{"inputTokens":-1,"outputTokens":2,"totalTokens":1}`, `{"inputTokens":1,"outputTokens":2,"totalTokens":3,"cachedInputTokens":2}`, `{"inputTokens":1,"outputTokens":2,"totalTokens":3,"reasoningOutputTokens":3}`, `{"inputTokens":9223372036854775807,"outputTokens":1,"totalTokens":9223372036854775807}`} {
		if _, e := parseTokenSnapshot(json.RawMessage(raw)); e == nil {
			t.Fatal("accepted", raw)
		}
	}
	_, c, thread := meterFixture(t, true)
	a := meterStart(t, c, thread, "development")
	meterSnapshot(t, c, thread, a, 100, 40)
	bad := json.RawMessage(`{"turnId":"` + a + `","tokenUsage":{"total":{}}}`)
	if e := c.captureTokenEvent(thread, "thread/tokenUsage/updated", bad); e == nil {
		t.Fatal("invalid snapshot accepted")
	}
	if meterLoad(t, c, thread).Runs[0].Tokens != nil {
		t.Fatal("stale valid usage retained")
	}
	p, _ := tokenLedgerPath(c.dir, thread)
	b := []byte(`{"version":1,"runs":"corrupt"}`)
	os.WriteFile(p, b, 0600)
	if e := c.initTokenMeter(thread, "sample", false); e == nil {
		t.Fatal("corrupt ledger reset")
	}
	got, _ := os.ReadFile(p)
	if string(got) != string(b) {
		t.Fatal("corruption overwritten")
	}
}
func TestTokenMeterNoReplayUnknownFieldsAndAggregateOverflow(t *testing.T) {
	r, c, thread := meterFixture(t, true)
	req := runtimeRequest{ThreadID: thread, RequestID: newID(), UsageCategory: "development"}
	if e := c.beginTokenRun(req, ""); e != nil {
		t.Fatal(e)
	}
	if e := c.beginTokenRun(req, ""); e == nil {
		t.Fatal("unresolved dispatch replay")
	}
	if e := c.initTokenMeter(thread, "sample", false); e != nil {
		t.Fatal(e)
	}
	if e := c.beginTokenRun(req, ""); e == nil {
		t.Fatal("recorded request replay")
	}
	report := meterReport(t, r, "development", 50, 0)
	s := report["summary"].(*tokenAggregate)
	if s.Measured != 0 || s.Unmeasured != 1 || s.Tokens != nil || s.Active != 0 {
		t.Fatal(s)
	}
	if _, e := r.tokenUsageReport(runtimeRequest{Project: "sample", UsageCategory: "guess"}); e == nil {
		t.Fatal("category accepted")
	}
	if _, e := r.tokenUsageReport(runtimeRequest{Project: "../sample"}); e == nil {
		t.Fatal("unauthorized project accepted")
	}
	n, e := parseTokenSnapshot(json.RawMessage(`{"inputTokens":10,"outputTokens":2,"totalTokens":12}`))
	if e != nil || n.Cached != nil || n.Reasoning != nil {
		t.Fatal(n, e)
	}
	a := tokenAggregate{}
	if e = a.add(&tokenRun{Tokens: &measuredTokens{Input: math.MaxInt64, Total: math.MaxInt64}}); e != nil {
		t.Fatal(e)
	}
	if e = a.add(&tokenRun{Tokens: &measuredTokens{Input: 1, Total: 1}}); e == nil {
		t.Fatal("aggregate overflow")
	}
}

func TestTokenMeterUnobservedExternalTurnInvalidatesBoundary(t *testing.T) {
	_, c, thread := meterFixture(t, true)
	a := meterStart(t, c, thread, "dialogue")
	meterSnapshot(t, c, thread, a, 100, 40)
	meterComplete(t, c, thread, a, "completed")
	if !tokenBoundaryMatches(meterLoad(t, c, thread), map[string]any{"turns": []any{map[string]any{"id": a}}}) {
		t.Fatal("known boundary rejected")
	}
	preflight := map[string]any{"turns": []any{map[string]any{"id": "external-turn-unknown"}}}
	if e := c.beginTokenRun(runtimeRequest{ThreadID: thread, RequestID: newID(), UsageCategory: "development"}, "test", preflight); e != nil {
		t.Fatal(e)
	}
	b := newID()
	meterEvent(t, c, thread, "turn/started", map[string]any{"turn": map[string]any{"id": b}})
	meterSnapshot(t, c, thread, b, 500, 200)
	if meterLoad(t, c, thread).Runs[1].Tokens != nil {
		t.Fatal("external dialogue counted as development")
	}
}
