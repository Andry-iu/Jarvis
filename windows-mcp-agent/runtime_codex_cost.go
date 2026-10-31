package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// An API null is never treated as zero by itself. The local operator may record
// independently verified billing facts after the user supplied the usage screen.
// No MCP request can supply or change this proof. Fresh API flags and quotas
// still have to pass the original strict guard, including paid-credit refusal.
type codexCostProof struct {
	Version int `json:"version"`
	MachineID string `json:"machine_id"`
	AccountFingerprint string `json:"account_fingerprint"`
	Source string `json:"source"`
	ZeroCredits bool `json:"zero_credits"`
	AutomaticRechargeDisabled bool `json:"automatic_recharge_disabled"`
	ConfirmedAt time.Time `json:"confirmed_at"`
	ValidUntil time.Time `json:"valid_until"`
}

func codexAccountFingerprint(a codexAccount) string {
	if a.Account == nil || a.Account.Type != "chatgpt" || strings.TrimSpace(a.Account.Email) == "" { return "" }
	return digest([]byte(strings.ToLower(strings.TrimSpace(a.Account.Email))+"\x00"+a.Account.Plan))
}

func readCodexCostProof() (*codexCostProof, error) {
	home := os.Getenv("JARVIS_HOME")
	if !filepath.IsAbs(home) { return nil, errors.New("cost proof home unavailable") }
	b, err := os.ReadFile(filepath.Join(home,"Config","codex-cost-proof.json"))
	if err != nil { return nil, err }
	if len(b)>4096 { return nil, errors.New("cost proof too large") }
	var proof codexCostProof
	d := json.NewDecoder(bytes.NewReader(bytes.TrimPrefix(b,[]byte{0xef,0xbb,0xbf})))
	d.DisallowUnknownFields()
	if err=d.Decode(&proof);err!=nil{return nil,err}
	return &proof,nil
}

func codexCostGuardWithProof(a codexAccount,q codexQuota,p *codexCostProof,machine string,now time.Time) error {
	if q.Credits==nil || q.Credits.Balance!=nil { return codexCostGuard(a,q) }
	if p==nil || p.Version!=1 || p.Source!="user-confirmed-usage-screenshot" || !p.ZeroCredits || !p.AutomaticRechargeDisabled || p.MachineID!=machine || machine=="" || p.AccountFingerprint=="" || p.AccountFingerprint!=codexAccountFingerprint(a) || p.ConfirmedAt.IsZero() || p.ConfirmedAt.After(now.Add(5*time.Minute)) || !p.ValidUntil.After(now) || p.ValidUntil.After(p.ConfirmedAt.Add(7*24*time.Hour)) {
		return errors.New("API balance unavailable and no current account/computer-bound zero-credit proof; inference blocked")
	}
	// Preserve the raw API response; only this checked decision uses the separate
	// zero-credit evidence. Positive/unknown credit flags cannot be overridden.
	copyQuota:=q
	copyCredits:=*q.Credits
	zero:="0"
	copyCredits.Balance=&zero
	copyQuota.Credits=&copyCredits
	return codexCostGuard(a,copyQuota)
}
