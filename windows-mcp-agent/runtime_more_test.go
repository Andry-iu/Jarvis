package main

import (
	"bytes"
	"encoding/json"
	"encoding/base64"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestRuntimeOneMiBBinaryOverMCP(t *testing.T) {
	r, dir := fixtureRuntime(t)
	const token = "test-only-bearer-123456789012345"
	server := httptest.NewServer(handler(&Agent{runtime: r}, token, nil))
	defer server.Close()
	data := bytes.Repeat([]byte{0x80}, maxFileBytes)
	arguments := map[string]any{"machine_id": r.MachineID, "project": "sample", "path": "binary.bin", "content": base64.StdEncoding.EncodeToString(data), "encoding": "base64", "expected_sha256": "missing", "request_id": newID()}
	body, _ := json.Marshal(map[string]any{"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": map[string]any{"name": "file_write", "arguments": arguments}})
	req, _ := http.NewRequest("POST", server.URL+"/mcp", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	response, e := http.DefaultClient.Do(req)
	if e != nil {
		t.Fatal(e)
	}
	defer response.Body.Close()
	var rpc struct { Result struct { IsError bool `json:"isError"` } `json:"result"`; Error any `json:"error"` }
	if e = json.NewDecoder(response.Body).Decode(&rpc); e != nil || response.StatusCode != 200 || rpc.Error != nil || rpc.Result.IsError {
		t.Fatalf("1 MiB MCP upload failed: %d %v %+v", response.StatusCode, e, rpc)
	}
	got, e := os.ReadFile(filepath.Join(dir, "binary.bin"))
	if e != nil || !bytes.Equal(got, data) {
		t.Fatal("binary upload mismatch", e)
	}
}

func TestRestorePreservesNewUserChanges(t *testing.T) {
	r, dir := fixtureRuntime(t)
	v := mutateFile(t, r, runtimeRequest{Operation: "file_write", Path: "notes.txt", Expected: "missing", Content: "created"})
	id := v.(map[string]any)["request_id"].(string)
	os.WriteFile(filepath.Join(dir, "notes.txt"), []byte("new user edit"), 0600)
	if _, e := r.call(runtimeRequest{Operation: "file_restore", RestoreID: id, RequestID: newID()}); e == nil {
		t.Fatal("restore erased user edit")
	}
	b, _ := os.ReadFile(filepath.Join(dir, "notes.txt"))
	if string(b) != "new user edit" {
		t.Fatal("edit changed")
	}
}
func TestUncertainReceiptNeverReplays(t *testing.T) {
	r, dir := fixtureRuntime(t)
	p := runtimeRequest{Operation: "file_write", Project: "sample", Path: "never.txt", Expected: "missing", Content: "do not replay", RequestID: newID()}
	b, _ := json.Marshal(p)
	x := &Receipt{ID: p.RequestID, Digest: digest(b), Operation: p.Operation, Status: "dispatched", At: utcNow()}
	if e := r.saveReceipt(x); e != nil {
		t.Fatal(e)
	}
	if _, e := r.call(p); e == nil {
		t.Fatal("uncertain request replayed")
	}
	if _, e := os.Stat(filepath.Join(dir, "never.txt")); !os.IsNotExist(e) {
		t.Fatal("uncertain operation executed")
	}
}

type testWriteCloser struct{ bytes.Buffer }

func TestRuntimeRequiredAndOperationSpecificFields(t *testing.T) {
	for _, raw := range []string{
		`{"operation":"file_write","project":"sample","path":"x","expected_sha256":"missing","request_id":"1234567890123456"}`,
		`{"operation":"file_write","project":"sample","path":"x","content":null,"expected_sha256":"missing","request_id":"1234567890123456"}`,
		`{"operation":"machine_info","project":"ignored-project"}`,
	} {
		if _, e := decodeRuntime("", json.RawMessage(raw)); e == nil {
			t.Fatal("invalid or irrelevant arguments accepted", raw)
		}
	}
	if _, e := decodeRuntime("", json.RawMessage(`{"operation":"file_write","project":"sample","path":"x","content":"","expected_sha256":"missing","request_id":"1234567890123456"}`)); e != nil {
		t.Fatal("explicit empty content should be accepted", e)
	}
}

func (w *testWriteCloser) Close() error { return nil }
func TestCodexApprovalRejectsSessionGrantAndReplay(t *testing.T) {
	w := &testWriteCloser{}
	thread := newID()
	id := newID()
	c := &codexClient{input: w, pending: map[string]codexPending{id: {ID: json.RawMessage("42"), Method: "item/fileChange/requestApproval", ThreadID: thread}}}
	p := runtimeRequest{ThreadID: thread, ApprovalID: id, Decision: "acceptForSession"}
	if _, e := c.respond(p); e == nil {
		t.Fatal("session grant accepted")
	}
	if len(c.pending) != 1 {
		t.Fatal("invalid input consumed pending approval")
	}
	p.Decision = "decline"
	if _, e := c.respond(p); e != nil {
		t.Fatal(e)
	}
	before := w.String()
	if _, e := c.respond(p); e == nil {
		t.Fatal("approval replay accepted")
	}
	if w.String() != before {
		t.Fatal("approval response sent twice")
	}
}
func TestStateCannotBeExposedAsProject(t *testing.T) {
	_, dir := fixtureRuntime(t)
	if r, e := newRuntime(filepath.Join(dir, "public-state")); e == nil {
		r.Close()
		t.Fatal("state under project root accepted")
	}
}
