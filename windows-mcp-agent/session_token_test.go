package main

import (
    "encoding/json"
    "strings"
    "testing"
    "time"
)

func sessionResult() any { return toolResult(map[string]any{"ok": true}, false) }
func sessionText(t *testing.T, result any) map[string]any {
    t.Helper()
    m := result.(map[string]any)
    first := m["content"].([]any)[0].(map[string]any)["text"].(string)
    var v map[string]any
    if err := json.Unmarshal([]byte(first), &v); err != nil {t.Fatal(err)}
    return v
}

func TestSessionZeroReuseAndUnknownStart(t *testing.T) {
    a := &Agent{}
    now := time.Now()
    known, reused, supplied := a.sessions.begin(json.RawMessage(`{"session_token":"0"}`), "cf:user-1", now)
    if known != "" || reused || !supplied {t.Fatal("zero must request a new lease")}
    first := sessionText(t, a.finishSession(sessionResult(), "cf:user-1", known, reused, supplied))
    token := first["session_token"].(string)
    if len(token) < 40 || first["session_reused"] != false {t.Fatal("new strong token expected")}
    input, _ := json.Marshal(map[string]any{"session_token": token})
    known, reused, supplied = a.sessions.begin(input,"cf:user-1",now.Add(time.Second))
    if known != token || !reused || !supplied {t.Fatal("known token must reuse session")}
    again := sessionText(t, a.finishSession(sessionResult(),"cf:user-1",known,reused,supplied))
    if again["session_token"] != token || again["session_reused"] != true {t.Fatal("reuse metadata")}
    known, reused, supplied = a.sessions.begin(json.RawMessage(`{"session_token":"made-up-token"}`),"cf:user-1",now)
    if known != "" || reused || !supplied {t.Fatal("unknown token must behave like zero")}
    replacement := sessionText(t,a.finishSession(sessionResult(),"cf:user-1",known,reused,supplied))
    if replacement["session_token"] == token {t.Fatal("new lease must have new token")}
}

func TestSessionIdleExpiryAndPrincipalIsolation(t *testing.T) {
    var sessions sessionState
    now := time.Now()
    token, err := sessions.create("cf:one",now)
    if err != nil {t.Fatal(err)}
    raw, _ := json.Marshal(map[string]any{"session_token":token})
    if known,reused,_ := sessions.begin(raw,"cf:two",now.Add(time.Second)); known != "" || reused {
        t.Fatal("token from another principal accepted")
    }
    if known,reused,_ := sessions.begin(raw,"cf:one",now.Add(1799*time.Second)); known != token || !reused {
        t.Fatal("lease must renew before 1800 seconds")
    }
    if known,reused,_ := sessions.begin(raw,"cf:one",now.Add(3598*time.Second)); known != token || !reused {
        t.Fatal("every valid command renews idle deadline")
    }
    if known,reused,_ := sessions.begin(raw,"cf:one",now.Add(5398*time.Second)); known != "" || reused {
        t.Fatal("lease must expire after 1800 seconds idle")
    }
}

func TestSessionDoesNotIssueAfterFailedInitialCall(t *testing.T) {
    a := &Agent{}
    got := a.finishSession(toolResult(map[string]any{"error":"denied"},true),"local","",false,true)
    if _, exists := sessionText(t,got)["session_token"]; exists {t.Fatal("failed initial command must not issue a token")}
}

func TestSessionTokenRuntimeSchemaAndDecoder(t *testing.T) {
    p, err := decodeRuntime("machine_info",json.RawMessage(`{"session_token":"0"}`))
    if err != nil || string(p.SessionToken) != `"0"` {t.Fatalf("runtime token: %+v, %v",p,err)}
    if _, err := decodeRuntime("machine_info",json.RawMessage(`{"session_token":null}`)); err == nil {
        t.Fatal("null token must not be accepted")
    }
    var exists bool
    for _, raw := range toolsList().(map[string]any)["tools"].([]any) {
        spec := raw.(map[string]any)
        if spec["name"] == "machine_info" {
            exists = true
            props := spec["inputSchema"].(map[string]any)["properties"].(map[string]any)
            if props["session_token"] == nil {t.Fatal("schema must advertise session_token")}
        }
    }
    if !exists {t.Fatal("runtime catalog missing machine_info")}
    raw := json.RawMessage(`{"session_token":0}`)
    if value,supplied:=suppliedSessionToken(raw);value != "0" || !supplied {t.Fatal("numeric zero not accepted")}
    if strings.Contains(verifiedAccessPrincipal("a.b.c"),"user") {t.Fatal("raw claims surfaced")}
}

func TestVerifiedJWTCachedActivityAndAbsoluteJWTExpiry(t *testing.T) {
    var v AccessVerifier
    start := time.Now()
    v.cacheVerifiedJWT("verified-opaque-jwt", start.Add(90*time.Minute), start)
    if !v.cachedJWT("verified-opaque-jwt",start.Add(29*time.Minute)) {
        t.Fatal("recently verified access token should remain cached")
    }
    if !v.cachedJWT("verified-opaque-jwt",start.Add(58*time.Minute)) {
        t.Fatal("continued activity must extend 30-minute idle limit")
    }
    if v.cachedJWT("verified-opaque-jwt",start.Add(90*time.Minute)) {
        t.Fatal("issuer expiry MUST override continued activity")
    }
    if v.cachedJWT("unverified-token",start.Add(2*time.Minute)) {
        t.Fatal("cache must not trust arbitrary unverified tokens")
    }
    v.cacheVerifiedJWT("another-token",start.Add(3*time.Hour),start)
    if v.cachedJWT("another-token",start.Add(31*time.Minute)) {
        t.Fatal("cache must stop reusing an inactive token at 30 minutes")
    }
}
