package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestStreamableHTTPTransport(t *testing.T) {
	token := "test-secret-123456789012345678901234"
	srv := httptest.NewServer(handler(&Agent{}, token, nil))
	defer srv.Close()
	call := func(method, contentType, accept, auth, body string) (int, http.Header, map[string]any) {
		t.Helper()
		req, _ := http.NewRequest(method, srv.URL+"/mcp", bytes.NewBufferString(body))
		if contentType != "" {
			req.Header.Set("Content-Type", contentType)
		}
		if accept != "" {
			req.Header.Set("Accept", accept)
		}
		if auth != "" {
			req.Header.Set("Authorization", auth)
		}
		r, e := http.DefaultClient.Do(req)
		if e != nil {
			t.Fatal(e)
		}
		defer r.Body.Close()
		var result map[string]any
		_ = json.NewDecoder(r.Body).Decode(&result)
		return r.StatusCode, r.Header, result
	}
	const init = `{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}`
	valid := "Bearer " + token
	s, _, _ := call("POST", "application/json", "application/json, text/event-stream", "", init)
	if s != 401 {
		t.Fatalf("missing auth %d", s)
	}
	s, h, r := call("POST", "application/json", "application/json, text/event-stream", valid, init)
	if s != 200 {
		t.Fatalf("init %d: %v", s, r)
	}
	if r["result"].(map[string]any)["protocolVersion"] != "2025-06-18" {
		t.Fatalf("negotiation %v", r)
	}
	if h.Get("Content-Type") != "application/json" {
		t.Fatal(h)
	}
	s, _, _ = call("POST", "application/json", "application/json", valid, init)
	if s != 406 {
		t.Fatalf("accept %d", s)
	}
	s, _, _ = call("POST", "application/json", "application/json, text/event-stream", valid, `{"jsonrpc":"2.0","method":"notifications/initialized"}`)
	if s != 202 {
		t.Fatalf("notification %d", s)
	}
	s, _, _ = call("GET", "", "", valid, "")
	if s != 405 {
		t.Fatalf("GET %d", s)
	}
	s, _, _ = call("DELETE", "", "", valid, "")
	if s != 405 {
		t.Fatalf("DELETE %d", s)
	}
	s, _, r = call("POST", "application/json", "application/json, text/event-stream", valid, `{"jsonrpc":"2.0","id":2,"method":"tools/list"}`)
	if s != 200 || r["result"] == nil {
		t.Fatalf("tools %d %v", s, r)
	}
	s, _, r = call("POST", "application/json", "application/json, text/event-stream", valid, `invalid`)
	if s != 400 || r["error"] == nil {
		t.Fatalf("malformed %d %v", s, r)
	}
}
