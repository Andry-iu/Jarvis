package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestMCP(t *testing.T) {
	srv := httptest.NewServer(handler(&Agent{}, "secret-for-test-123456789012345", nil))
	defer srv.Close()
	call := func(token, method, params string) (int, map[string]any) {
		t.Helper()
		body := `{"jsonrpc":"2.0","id":1,"method":"` + method + `","params":` + params + `}`
		req, _ := http.NewRequest("POST", srv.URL+"/mcp", bytes.NewBufferString(body))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Authorization", "Bearer "+token)
		res, e := http.DefaultClient.Do(req)
		if e != nil {
			t.Fatal(e)
		}
		defer res.Body.Close()
		var out map[string]any
		json.NewDecoder(res.Body).Decode(&out)
		return res.StatusCode, out
	}
	if s, _ := call("bad", "tools/list", `{}`); s != 401 {
		t.Fatal(s)
	}
	for _, method := range []string{"initialize", "tools/list"} {
		s, r := call("secret-for-test-123456789012345", method, `{}`)
		if s != 200 || r["result"] == nil {
			t.Fatalf("%s: %d %v", method, s, r)
		}
	}
	s, r := call("secret-for-test-123456789012345", "tools/call", `{"name":"task_start","arguments":{"duration_seconds":30}}`)
	if s != 200 || r["result"] == nil {
		t.Fatal(r)
	}
	_, r = call("secret-for-test-123456789012345", "tools/call", `{"name":"task_stop","arguments":{}}`)
	if r["result"] == nil {
		t.Fatal(r)
	}
}
