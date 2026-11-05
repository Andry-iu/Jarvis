package main

// This ledger observes only Codex turns dispatched by this bridge. It never
// reads, edits or deletes conversations, and never derives tokens from quotas.
import (
	"encoding/json"
	"errors"
	"io"
	"math"
	"os"
	"path/filepath"
	"sort"
)

type measuredTokens struct {
	Input     int64  `json:"input_tokens"`
	Cached    *int64 `json:"cached_input_tokens"`
	Output    int64  `json:"output_tokens"`
	Reasoning *int64 `json:"reasoning_output_tokens"`
	Total     int64  `json:"total_tokens"`
}
type tokenRun struct {
	RequestID      string          `json:"request_id"`
	TurnID         string          `json:"turn_id"`
	Category       string          `json:"category"`
	RequestedModel string          `json:"requested_model"`
	StartedAt      string          `json:"started_at"`
	FinishedAt     string          `json:"finished_at,omitempty"`
	Status         string          `json:"status"`
	Coverage       string          `json:"coverage"`
	Baseline       *measuredTokens `json:"baseline,omitempty"`
	Last           *measuredTokens `json:"last_snapshot,omitempty"`
	Tokens         *measuredTokens `json:"tokens"`
}
type tokenLedger struct {
	Version   int             `json:"version"`
	MachineID string          `json:"machine_id"`
	ThreadID  string          `json:"thread_id"`
	Project   string          `json:"project"`
	Current   *measuredTokens `json:"current_snapshot,omitempty"`
	Pending   *tokenRun       `json:"pending,omitempty"`
	Runs      []*tokenRun     `json:"runs"`
}

func validUsageCategory(s string) bool {
	return s == "" || s == "development" || s == "dialogue" || s == "other" || s == "unclassified"
}
func tokenLedgerPath(dir, thread string) (string, error) {
	if !validThreadID(thread) {
		return "", errors.New("invalid token ledger thread")
	}
	return filepath.Join(dir, "token-usage", thread+".json"), nil
}
func loadTokenLedger(dir, machine, thread string) (*tokenLedger, error) {
	path, err := tokenLedgerPath(dir, thread)
	if err != nil {
		return nil, err
	}
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	b, err := io.ReadAll(io.LimitReader(f, 16*1024*1024+1))
	if err != nil || len(b) > 16*1024*1024 {
		return nil, errors.New("token ledger unreadable or too large; preserved")
	}
	var l tokenLedger
	if json.Unmarshal(b, &l) != nil || l.Version != 1 || l.MachineID != machine || l.ThreadID != thread {
		return nil, errors.New("invalid token ledger; preserved without reset")
	}
	if !validTokenLedger(&l) {
		return nil, errors.New("invalid token ledger contents; preserved without reset")
	}
	return &l, nil
}
func validMeasuredTokens(n *measuredTokens) bool {
	if n == nil {
		return true
	}
	return n.Input >= 0 && n.Output >= 0 && n.Total >= n.Input && n.Output == n.Total-n.Input &&
		(n.Cached == nil || (*n.Cached >= 0 && *n.Cached <= n.Input)) &&
		(n.Reasoning == nil || (*n.Reasoning >= 0 && *n.Reasoning <= n.Output))
}
func validTokenLedger(l *tokenLedger) bool {
	if l.Project == "" || !validMeasuredTokens(l.Current) {
		return false
	}
	requests, turns := map[string]bool{}, map[string]bool{}
	runs := append([]*tokenRun{}, l.Runs...)
	if l.Pending != nil {
		runs = append(runs, l.Pending)
	}
	for _, run := range runs {
		if run == nil || !requestIDPattern.MatchString(run.RequestID) || requests[run.RequestID] || run.Category == "" || !validUsageCategory(run.Category) || run.StartedAt == "" || !validMeasuredTokens(run.Baseline) || !validMeasuredTokens(run.Last) || !validMeasuredTokens(run.Tokens) {
			return false
		}
		if run.TurnID != "" {
			if turns[run.TurnID] {
				return false
			}
			turns[run.TurnID] = true
		}
		if (run.Coverage == "measured") != (run.Tokens != nil) {
			return false
		}
		requests[run.RequestID] = true
	}
	return true
}
func (c *codexClient) saveTokenLedger(l *tokenLedger) error {
	p, err := tokenLedgerPath(c.dir, l.ThreadID)
	if err != nil {
		return err
	}
	if err = os.MkdirAll(filepath.Dir(p), 0700); err != nil {
		return err
	}
	return atomicJSON(p, l)
}

// Called with c.mu held. Resuming never imports unobserved historical usage.
func (c *codexClient) initTokenMeter(thread, project string, fresh bool) error {
	l, err := loadTokenLedger(c.dir, c.machineID, thread)
	if fresh && err == nil {
		return errors.New("token ledger already exists; explicit resume required")
	}
	if os.IsNotExist(err) {
		l = &tokenLedger{Version: 1, MachineID: c.machineID, ThreadID: thread, Project: project, Runs: []*tokenRun{}}
	} else if err != nil {
		return err
	}
	if l.Project != project {
		return errors.New("token ledger project mismatch")
	}
	l.Current = nil
	if fresh {
		l.Current = &measuredTokens{Cached: int64Pointer(0), Reasoning: int64Pointer(0)}
	}
	if l.Pending != nil {
		l.Pending.Status = "outcome_unknown"
		l.Runs = append(l.Runs, l.Pending)
		l.Pending = nil
	}
	for _, r := range l.Runs {
		if r.Status == "inProgress" {
			r.Status = "outcome_unknown"
		}
	}
	return c.saveTokenLedger(l)
}
func int64Pointer(n int64) *int64 { return &n }

// The full preflight thread identifies unobserved turns from another client.
// A changed/unknown boundary discards attribution, never historical counters.
func tokenBoundaryMatches(l *tokenLedger, thread map[string]any) bool {
	turns, ok := thread["turns"].([]any)
	if !ok {
		return false
	}
	if len(turns) == 0 {
		return len(l.Runs) == 0
	}
	last, ok := turns[len(turns)-1].(map[string]any)
	if !ok {
		return false
	}
	id, ok := last["id"].(string)
	if !ok || id == "" {
		return false
	}
	for i := len(l.Runs) - 1; i >= 0; i-- {
		if l.Runs[i].TurnID != "" {
			return id == l.Runs[i].TurnID
		}
	}
	return false
}
func (c *codexClient) beginTokenRun(p runtimeRequest, model string, preflight ...map[string]any) error {
	l, err := loadTokenLedger(c.dir, c.machineID, p.ThreadID)
	if err != nil {
		return err
	}
	if l.Pending != nil {
		return errors.New("token ledger has unresolved dispatch; inspect before retry")
	}
	if len(preflight) > 0 && !tokenBoundaryMatches(l, preflight[0]) {
		l.Current = nil
	}
	cat := p.UsageCategory
	if cat == "" {
		cat = "unclassified"
	}
	if !validUsageCategory(cat) {
		return errors.New("invalid usage_category")
	}
	for _, run := range l.Runs {
		if run.RequestID == p.RequestID {
			return errors.New("token request already recorded; no replay")
		}
	}
	l.Pending = &tokenRun{Baseline: l.Current, RequestID: p.RequestID, Category: cat, RequestedModel: model, StartedAt: utcNow(), Status: "dispatching", Coverage: "no_token_event"}
	return c.saveTokenLedger(l)
}
func (c *codexClient) bindTokenRun(l *tokenLedger, turn string) {
	if turn == "" || l.Pending == nil {
		return
	}
	for _, r := range l.Runs {
		if r.TurnID == turn {
			return
		}
	}
	r := l.Pending
	l.Pending = nil
	r.TurnID = turn
	r.Status = "inProgress"
	if r.Baseline == nil {
		r.Coverage = "baseline_unavailable"
	}
	l.Runs = append(l.Runs, r)
}
func parseTokenSnapshot(raw json.RawMessage) (*measuredTokens, error) {
	var w struct {
		Input     *int64 `json:"inputTokens"`
		Cached    *int64 `json:"cachedInputTokens"`
		Output    *int64 `json:"outputTokens"`
		Reasoning *int64 `json:"reasoningOutputTokens"`
		Total     *int64 `json:"totalTokens"`
	}
	if json.Unmarshal(raw, &w) != nil || w.Input == nil || w.Output == nil || w.Total == nil {
		return nil, errors.New("missing token counters")
	}
	if *w.Input < 0 || *w.Output < 0 || *w.Total < 0 || *w.Input > *w.Total || *w.Output != *w.Total-*w.Input {
		return nil, errors.New("invalid token counters")
	}
	if w.Cached != nil && (*w.Cached < 0 || *w.Cached > *w.Input) {
		return nil, errors.New("invalid cached tokens")
	}
	if w.Reasoning != nil && (*w.Reasoning < 0 || *w.Reasoning > *w.Output) {
		return nil, errors.New("invalid reasoning tokens")
	}
	return &measuredTokens{*w.Input, w.Cached, *w.Output, w.Reasoning, *w.Total}, nil
}
func tokenDifference(end, start *measuredTokens) (*measuredTokens, bool) {
	if end == nil || start == nil {
		return nil, false
	}
	if end.Input < start.Input || end.Output < start.Output || end.Total < start.Total {
		return nil, false
	}
	n := &measuredTokens{Input: end.Input - start.Input, Output: end.Output - start.Output, Total: end.Total - start.Total}
	if end.Cached != nil && start.Cached != nil {
		if *end.Cached < *start.Cached || *end.Cached-*start.Cached > n.Input {
			return nil, false
		}
		n.Cached = int64Pointer(*end.Cached - *start.Cached)
	}
	if end.Reasoning != nil && start.Reasoning != nil {
		if *end.Reasoning < *start.Reasoning || *end.Reasoning-*start.Reasoning > n.Output {
			return nil, false
		}
		n.Reasoning = int64Pointer(*end.Reasoning - *start.Reasoning)
	}
	return n, true
}

// Caller holds c.mu. Cumulative snapshots replace each run's totals; duplicates
// never get added. Missing baselines/counter resets remain explicitly unknown.
func (c *codexClient) captureTokenEvent(thread, method string, raw json.RawMessage) error {
	if method != "turn/started" && method != "turn/completed" && method != "thread/tokenUsage/updated" {
		return nil
	}
	l, err := loadTokenLedger(c.dir, c.machineID, thread)
	if os.IsNotExist(err) {
		return nil
	}
	if err != nil {
		return err
	}
	var p struct {
		TurnID string `json:"turnId"`
		Turn   struct {
			ID     string `json:"id"`
			Status string `json:"status"`
		} `json:"turn"`
		Usage struct {
			Total json.RawMessage `json:"total"`
		} `json:"tokenUsage"`
	}
	if json.Unmarshal(raw, &p) != nil {
		return errors.New("invalid token event")
	}
	if method == "turn/started" {
		known := false
		for _, run := range l.Runs {
			if run.TurnID == p.Turn.ID {
				known = true
				break
			}
		}
		if l.Pending == nil && !known {
			l.Current = nil
		}
		c.bindTokenRun(l, p.Turn.ID)
		return c.saveTokenLedger(l)
	}
	if method == "turn/completed" {
		for _, run := range l.Runs {
			if run.TurnID == p.Turn.ID {
				if run.FinishedAt == "" {
					run.Status = p.Turn.Status
					run.FinishedAt = utcNow()
				}
				if run.Last == nil || run.Coverage == "counter_reset_or_reordered" || run.Coverage == "invalid_snapshot" {
					l.Current = nil
				}
				break
			}
		}
		return c.saveTokenLedger(l)
	}
	var target *tokenRun
	for _, run := range l.Runs {
		if run.TurnID == p.TurnID && p.TurnID != "" {
			target = run
			break
		}
	}
	snapshot, err := parseTokenSnapshot(p.Usage.Total)
	if err != nil {
		if target != nil {
			target.Tokens = nil
			target.Coverage = "invalid_snapshot"
		}
		l.Current = nil
		if saveErr := c.saveTokenLedger(l); saveErr != nil {
			return saveErr
		}
		return err
	}
	if target != nil {
		if target.Last != nil {
			if _, ok := tokenDifference(snapshot, target.Last); !ok {
				target.Tokens = nil
				target.Coverage = "counter_reset_or_reordered"
				l.Current = nil
				return c.saveTokenLedger(l)
			}
		}
		target.Last = snapshot
		if target.Coverage != "counter_reset_or_reordered" && target.Coverage != "late_previous_turn" {
			if n, ok := tokenDifference(snapshot, target.Baseline); ok {
				target.Tokens = n
				target.Coverage = "measured"
			} else {
				target.Tokens = nil
				target.Coverage = "baseline_unavailable"
			}
		}
		// Late updates make later boundaries ambiguous; do not invent attribution.
		for i, run := range l.Runs {
			if run == target {
				for _, later := range l.Runs[i+1:] {
					if later.Baseline != nil && snapshot.Total > later.Baseline.Total {
						later.Tokens = nil
						later.Coverage = "late_previous_turn"
					}
				}
				if l.Pending != nil && l.Pending.Baseline != nil && snapshot.Total > l.Pending.Baseline.Total {
					l.Pending.Baseline = nil
				}
				break
			}
		}
	}
	// A watermark from an unidentified turn cannot become a trusted boundary.
	if p.TurnID == "" {
		for _, run := range l.Runs {
			if run.Status == "inProgress" {
				run.Baseline = nil
				run.Tokens = nil
				run.Coverage = "baseline_unavailable"
			}
		}
		l.Current = nil
	} else if l.Current == nil || snapshot.Total >= l.Current.Total {
		l.Current = snapshot
	}
	return c.saveTokenLedger(l)
}

func (c *codexClient) tokenRunView(thread, turn string) any {
	l, err := loadTokenLedger(c.dir, c.machineID, thread)
	if err != nil {
		return map[string]any{"coverage": "unavailable", "tokens": nil}
	}
	for _, r := range l.Runs {
		if r.TurnID == turn {
			return publicTokenRun(l, r)
		}
	}
	return map[string]any{"coverage": "no_record", "tokens": nil}
}
func publicTokenRun(l *tokenLedger, r *tokenRun) map[string]any {
	return map[string]any{"machine_id": l.MachineID, "project": l.Project, "thread_id": l.ThreadID, "request_id": r.RequestID, "turn_id": r.TurnID, "category": r.Category, "requested_model": r.RequestedModel, "started_at": r.StartedAt, "finished_at": r.FinishedAt, "status": r.Status, "coverage": r.Coverage, "tokens": r.Tokens}
}

// Aggregates include observed deltas only. Missing counters remain null, and
// cache/reasoning are subsets of input/output, never added again to total.
type tokenAggregate struct {
	Measured   int             `json:"measured_runs"`
	Unmeasured int             `json:"unmeasured_runs"`
	Active     int             `json:"active_runs"`
	Tokens     *measuredTokens `json:"observed_tokens"`
}

func (a *tokenAggregate) add(run *tokenRun) error {
	if run.Status == "inProgress" || run.Status == "dispatching" {
		a.Active++
	}
	if run.Tokens == nil {
		a.Unmeasured++
		return nil
	}
	n := run.Tokens
	if a.Tokens == nil {
		a.Tokens = &measuredTokens{Cached: int64Pointer(0), Reasoning: int64Pointer(0)}
	}
	if n.Total > math.MaxInt64-a.Tokens.Total {
		return errors.New("token aggregate overflow; select thread_id")
	}
	a.Tokens.Input += n.Input
	a.Tokens.Output += n.Output
	a.Tokens.Total += n.Total
	if a.Tokens.Cached != nil {
		if n.Cached == nil {
			a.Tokens.Cached = nil
		} else {
			*a.Tokens.Cached += *n.Cached
		}
	}
	if a.Tokens.Reasoning != nil {
		if n.Reasoning == nil {
			a.Tokens.Reasoning = nil
		} else {
			*a.Tokens.Reasoning += *n.Reasoning
		}
	}
	a.Measured++
	return nil
}
func (r *Runtime) tokenUsageReport(p runtimeRequest) (any, error) {
	if p.Limit == 0 {
		p.Limit = 50
	}
	if p.Limit < 1 || p.Limit > 200 || p.Offset < 0 || (!validUsageCategory(p.UsageCategory) && p.UsageCategory != "all") {
		return nil, errors.New("invalid token usage filter")
	}
	root, err := r.projectRoot(p.Project)
	if err != nil {
		return nil, err
	}
	root.Close()
	cat := p.UsageCategory
	if cat == "" {
		cat = "development"
	}
	ids := []string{}
	if p.ThreadID != "" {
		if !validThreadID(p.ThreadID) {
			return nil, errors.New("invalid thread_id")
		}
		ids = append(ids, p.ThreadID)
	} else {
		files, e := os.ReadDir(filepath.Join(r.dir, "token-usage"))
		if e != nil && !os.IsNotExist(e) {
			return nil, errors.New("token ledger directory unreadable")
		}
		for _, f := range files {
			if !f.IsDir() && filepath.Ext(f.Name()) == ".json" {
				ids = append(ids, f.Name()[:len(f.Name())-5])
			}
		}
		if len(ids) > 2000 {
			return nil, errors.New("too many token ledgers; select thread_id")
		}
	}
	rows := []map[string]any{}
	skipped := 0
	summary := map[string]*tokenAggregate{}
	totals := &tokenAggregate{}
	for _, id := range ids {
		l, e := loadTokenLedger(r.dir, r.MachineID, id)
		if e != nil {
			return nil, e
		}
		if l.Project != p.Project {
			continue
		}
		runs := append([]*tokenRun{}, l.Runs...)
		if l.Pending != nil {
			runs = append(runs, l.Pending)
		}
		for _, run := range runs {
			if cat != "all" && run.Category != cat {
				if run.Category == "unclassified" {
					skipped++
				}
				continue
			}
			if summary[run.Category] == nil {
				summary[run.Category] = &tokenAggregate{}
			}
			if e := summary[run.Category].add(run); e != nil {
				return nil, e
			}
			if e := totals.add(run); e != nil {
				return nil, e
			}
			rows = append(rows, publicTokenRun(l, run))
			if len(rows) > 100000 {
				return nil, errors.New("too many token records; select thread_id")
			}
		}
	}
	sort.Slice(rows, func(i, j int) bool {
		a, b := rows[i]["started_at"].(string), rows[j]["started_at"].(string)
		if a == b {
			return rows[i]["request_id"].(string) < rows[j]["request_id"].(string)
		}
		return a < b
	})
	start := p.Offset
	if start > len(rows) {
		start = len(rows)
	}
	end := start + p.Limit
	if end > len(rows) {
		end = len(rows)
	}
	return map[string]any{"machine_id": r.MachineID, "project": p.Project, "category": cat, "records": rows[start:end], "total_matching": len(rows), "next_offset": end, "has_more": end < len(rows), "unclassified_excluded": skipped, "summary_by_category": summary, "summary": totals, "summary_scope": "all matching records, including observed partial usage of active runs; unknown runs excluded from totals", "scope": "Jarvis-dispatched Codex turns only; external ChatGPT dialog, audio and other clients are not observed", "historical_backfill": false, "account_quota_is_not_tokens": true}, nil
}
