package main

import (
    "encoding/json"
    "testing"
    "time"
)

// Cached verified Cloudflare Access JWTs remain an internal optimization
// independent of the removed Jarvis conversational session-token feature.
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

// A previously cached client may keep offering the old token schema; this
// server must NOT accept the token as a shortcut to authentication.
func TestMCPServerDoesNotAdvertiseOrAcceptSessionToken(t *testing.T) {
    catalog := toolsList().(map[string]any)
    for _, raw := range catalog["tools"].([]any) {
        spec := raw.(map[string]any)
        name := spec["name"].(string)
        schema := spec["inputSchema"].(map[string]any)
        props := schema["properties"].(map[string]any)
        if _, exists := props["session_token"]; exists {
            t.Errorf("tool %q still advertises removed session_token", name)
        }
    }
    for _, operation := range []string{"machine_info", "ui_session_open"} {
        raw := json.RawMessage(`{"operation":"`+operation+`","session_token":"0"}`)
        if _, err := decodeRuntime(operation, raw); err == nil {
            t.Errorf("runtime %s unexpectedly accepted removed session_token", operation)
        }
    }
    if _, err := decodeRuntime("machine_info", json.RawMessage(`{"operation":"machine_info"}`)); err != nil {
        t.Fatalf("ordinary machine_info without token must remain supported: %v", err)
    }
}
