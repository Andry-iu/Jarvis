package main

import (
	"encoding/json"
	"math"
	"time"
)

// Summaries never substitute a billing proof for the actual service balance.
// Reading data and deciding whether a new inference is allowed are separate.
func codexUsageSummary(q codexQuota) map[string]any {
	windows := map[string]any{}
	for name, w := range map[string]*quotaWindow{"primary": q.Primary, "secondary": q.Secondary} {
		if w == nil {
			continue
		}
		item := map[string]any{"used_percent": w.Used, "remaining_percent": nil, "resets_at": w.Resets, "resets_at_utc": nil, "status": "unknown"}
		if w.Used != nil && !math.IsNaN(*w.Used) && !math.IsInf(*w.Used, 0) && *w.Used >= 0 && *w.Used <= 100 {
			item["remaining_percent"] = 100 - *w.Used
			item["status"] = "known"
		}
		if w.Resets != nil && *w.Resets > 0 {
			item["resets_at_utc"] = time.Unix(*w.Resets, 0).UTC().Format(time.RFC3339)
		}
		windows[name] = item
	}
	credits := map[string]any{"status": "unknown", "balance": nil, "has_credits": nil, "unlimited": nil}
	if q.Credits != nil {
		credits["balance"] = q.Credits.Balance
		credits["has_credits"] = q.Credits.Has
		credits["unlimited"] = q.Credits.Unlimited
		if q.Credits.Balance != nil {
			credits["status"] = "known"
		}
	}
	return map[string]any{"account_wide": true, "windows": windows, "credits": credits}
}

// Called once after a completed/interrupted/failed turn, on a separate goroutine.
// A denied next turn does not suppress valid usage data. No retry, comparison,
// inference, credit purchase or change to the pre-turn cost guard occurs here.
func (c *codexClient) recordTurnUsage(thread, turn, status string) error {
	report := map[string]any{"thread_id": thread, "turn_id": turn, "turn_status": status, "phase": "after", "account_wide": true, "available": false, "checked_at": utcNow()}
	value, err := codexAccountStatus(c)
	if err == nil {
		report["available"] = true
		report["status"] = value
	} else {
		report["error"] = "quota_read_failed"
		report["next_step"] = "request codex_status when the service is available"
	}
	b, err := json.Marshal(report)
	if err != nil {
		return err
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.appendEventLocked(thread, "jarvis/usage", b, "")
}
