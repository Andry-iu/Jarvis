package main

import (
    "crypto/rand"
    "crypto/sha256"
    "encoding/base64"
    "encoding/hex"
    "encoding/json"
    "strings"
    "sync"
    "time"
)

// Session tokens are temporary conversational hints, NOT authentication or
// authorization. Transport identity, grants, machine identity, request receipts,
// idempotency, OS privileges and all per-command gates stay mandatory.
const sessionIdleTimeout = 5 * time.Minute
const maxSessionLeases = 256

type sessionLease struct {
    LastUse time.Time
    Principal string
}

type sessionState struct {
    mu sync.Mutex
    leases map[[32]byte]sessionLease
}

func suppliedSessionToken(raw json.RawMessage) (string, bool) {
    var fields map[string]json.RawMessage
    if json.Unmarshal(raw, &fields) != nil {
        return "", false
    }
    value, exists := fields["session_token"]
    if !exists {
        return "", false // legacy clients still work without a token
    }
    if string(value) == "0" {
        return "0", true // accept the literal numeric zero too
    }
    var token string
    if json.Unmarshal(value, &token) != nil {
        return "", false // runtime decoder separately rejects malformed types
    }
    if token == "" || token == "0" || len(token) > 256 {
        return "0", true
    }
    return token, true
}

// Resolve an existing lease at request arrival so lengthy operations do not
// invalidate their own session while executing.
func (s *sessionState) begin(raw json.RawMessage, principal string, at time.Time) (string, bool, bool) {
    given, supplied := suppliedSessionToken(raw)
    if !supplied {
        return "", false, false
    }
    if given == "0" {
        return "", false, true
    }
    key := sha256.Sum256([]byte(given))
    s.mu.Lock()
    defer s.mu.Unlock()
    lease, exists := s.leases[key]
    if !exists || lease.Principal != principal || at.Before(lease.LastUse) || at.Sub(lease.LastUse) >= sessionIdleTimeout {
        if exists && at.Sub(lease.LastUse) >= sessionIdleTimeout {
            delete(s.leases, key)
        }
        return "", false, true
    }
    lease.LastUse = at
    s.leases[key] = lease
    return given, true, true
}

func (s *sessionState) create(principal string, at time.Time) (string, error) {
    bytes := make([]byte, 32)
    if _, err := rand.Read(bytes); err != nil {
        return "", err
    }
    token := base64.RawURLEncoding.EncodeToString(bytes)
    key := sha256.Sum256([]byte(token))
    s.mu.Lock()
    defer s.mu.Unlock()
    if s.leases == nil {
        s.leases = make(map[[32]byte]sessionLease)
    }
    for k, lease := range s.leases {
        if at.Sub(lease.LastUse) >= sessionIdleTimeout {
            delete(s.leases, k)
        }
    }
    if len(s.leases) >= maxSessionLeases {
        // Never expand an unbounded security state. The displaced lease is
        // invalidated; its next caller transparently starts again.
        var oldest [32]byte
        var oldestAt time.Time
        for k, lease := range s.leases {
            if oldestAt.IsZero() || lease.LastUse.Before(oldestAt) {
                oldest, oldestAt = k, lease.LastUse
            }
        }
        delete(s.leases, oldest)
    }
    s.leases[key] = sessionLease{LastUse: at, Principal: principal}
    return token, nil
}

// Only successful initial commands create leases. An already valid lease is
// returned even on an operation error; the caller's authenticated attempt was
// received and does not need to begin a second session.
func (a *Agent) finishSession(result any, principal, known string, reused, supplied bool) any {
    if !supplied {
        return result
    }
    token := known
    if !reused {
        if toolFailed(result) {
            return result
        }
        var err error
        token, err = a.sessions.create(principal, time.Now())
        if err != nil {
            return result
        }
    }
    return addSessionMetadata(result, token, reused)
}

func addSessionMetadata(result any, token string, reused bool) any {
    outer, ok := result.(map[string]any)
    if !ok {
        return result
    }
    content, ok := outer["content"].([]any)
    if !ok || len(content) == 0 {
        return result
    }
    item, ok := content[0].(map[string]any)
    if !ok {
        return result
    }
    original, ok := item["text"].(string)
    if !ok {
        return result
    }
    var payload map[string]any
    if json.Unmarshal([]byte(original), &payload) != nil || payload == nil {
        return result
    }
    payload["session_token"] = token
    payload["session_reused"] = reused
    payload["session_idle_timeout_seconds"] = int(sessionIdleTimeout / time.Second)
    updated, err := json.Marshal(payload)
    if err == nil {
        item["text"] = string(updated)
    }
    return result
}

// Extract a stable, opaque principal only AFTER the JWT has passed the
// existing Cloudflare Access verifier. Never trust unverified claims.
func verifiedAccessPrincipal(jwt string) string {
    pieces := strings.Split(jwt, ".")
    if len(pieces) != 3 {
        return "cf:verified"
    }
    payload, err := base64.RawURLEncoding.DecodeString(pieces[1])
    if err != nil {
        return "cf:verified"
    }
    var claims struct {
        Sub string `json:"sub"`
        Email string `json:"email"`
    }
    if json.Unmarshal(payload, &claims) != nil {
        return "cf:verified"
    }
    principal := claims.Sub
    if principal == "" {
        principal = claims.Email
    }
    if principal == "" {
        return "cf:verified"
    }
    hash := sha256.Sum256([]byte(principal))
    return "cf:" + hex.EncodeToString(hash[:])
}
