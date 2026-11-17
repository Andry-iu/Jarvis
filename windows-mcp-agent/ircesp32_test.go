package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"image"
	"image/jpeg"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
)

func ircFixtureDevice(t *testing.T) ircDevice {
	t.Helper()
	d := ircDevice{ID: "ircesp32-0123456789ab", Label: "fixture", Endpoint: "http://192.168.4.1", TokenEnv: "JARVIS_IRCESP32_TOKEN_TEST"}
	t.Setenv(d.TokenEnv, strings.Repeat("a", 64))
	return d
}
func ircFixtureClient(t *testing.T, h http.HandlerFunc) *http.Client {
	t.Helper()
	server := httptest.NewServer(h)
	t.Cleanup(server.Close)
	client := ircHTTPClient()
	tr := client.Transport.(*http.Transport)
	tr.DialContext = func(ctx context.Context, network, _ string) (net.Conn, error) {
		return (&net.Dialer{}).DialContext(ctx, network, server.Listener.Addr().String())
	}
	t.Cleanup(client.CloseIdleConnections)
	return client
}
func ircFixtureConfig(t *testing.T, r *Runtime, d ircDevice) {
	t.Helper()
	os.MkdirAll(filepath.Join(r.home, "Config"), 0700)
	b, _ := json.Marshal(map[string]any{"devices": []ircDevice{d}})
	if e := os.WriteFile(filepath.Join(r.home, "Config", "ircesp32.json"), b, 0600); e != nil {
		t.Fatal(e)
	}
}
func ircFixturePhoto(w http.ResponseWriter, d ircDevice, mutate func(http.Header)) {
	var data bytes.Buffer
	jpeg.Encode(&data, image.NewRGBA(image.Rect(0, 0, 2, 3)), nil)
	h := w.Header()
	h.Set("Content-Type", "image/jpeg")
	h.Set("X-IR-Device", d.ID)
	h.Set("X-IR-Boot", "0123abcd")
	h.Set("X-IR-Sequence", "1")
	h.Set("X-IR-SHA256", digest(data.Bytes()))
	h.Set("X-IR-Width", "2")
	h.Set("X-IR-Height", "3")
	h.Set("X-IR-Requested-Us", "1000")
	h.Set("X-IR-Captured-Us", "1001")
	if mutate != nil {
		mutate(h)
	}
	w.Write(data.Bytes())
}
func TestIRCESP32ConfigurationRefusesOtherOrigins(t *testing.T) {
	d := ircFixtureDevice(t)
	if e := d.validate(); e != nil {
		t.Fatal(e)
	}
	for _, url := range []string{"https://192.168.4.1", "http://example.com", "http://127.0.0.1", "http://169.254.169.254", "http://8.8.8.8", "http://192.168.4.1/", "http://192.168.4.1?q=a", "http://a:b@192.168.4.1", "http://192.168.4.1:81", "http://[::1]"} {
		v := d
		v.Endpoint = url
		if v.validate() == nil {
			t.Fatalf("accepted %s", url)
		}
	}
	r, _ := fixtureRuntime(t)
	ircFixtureConfig(t, r, d)
	v, e := r.ircCall(runtimeRequest{Operation: "ircesp32_list"})
	if e != nil {
		t.Fatal(e)
	}
	b, _ := json.Marshal(v)
	if bytes.Contains(b, []byte(strings.Repeat("a", 64))) || bytes.Contains(b, []byte("token_env")) {
		t.Fatal("credentials leaked")
	}
}
func TestIRCESP32InputValidation(t *testing.T) {
	for _, s := range []string{"STOP", "KEY 4 0", "KEY 0 255", "KEY 115 1", "MOUSE -127 127 0 7"} {
		if e := ircValidateInput(s); e != nil {
			t.Fatal(s, e)
		}
	}
	for _, s := range []string{"ARM", "HELLO", "PROVISION x y z", "KEY 1 0", "KEY 116 0", "KEY 4 256", "KEY +4 0", "KEY 04 0", "KEY 4 0\nSTOP", "MOUSE 128 0 0 0", "MOUSE 0 0 0 8", "MOUSE 0 0 0"} {
		if ircValidateInput(s) == nil {
			t.Fatal("accepted", s)
		}
	}
}
func TestIRCESP32PhotoValidation(t *testing.T) {
	d := ircFixtureDevice(t)
	cases := []struct {
		name   string
		mutate func(http.Header)
		ok     bool
	}{
		{"valid", nil, true},
		{"old_boot", func(h http.Header) { h.Set("X-IR-Boot", "ffffffff") }, false},
		{"other_device", func(h http.Header) { h.Set("X-IR-Device", "ircesp32-ffffffffffff") }, false},
		{"old_sequence", func(h http.Header) { h.Set("X-IR-Sequence", "2") }, false},
		{"corrupt_hash", func(h http.Header) { h.Set("X-IR-SHA256", strings.Repeat("0", 64)) }, false},
		{"stale_frame", func(h http.Header) { h.Set("X-IR-Captured-Us", "999") }, false},
		{"wrong_dimensions", func(h http.Header) { h.Set("X-IR-Width", "3") }, false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			c := ircFixtureClient(t, func(w http.ResponseWriter, r *http.Request) {
				if r.Method != "POST" || r.URL.Path != "/v1/capture" || r.Header.Get("Authorization") != "Bearer "+strings.Repeat("a", 64) {
					t.Error("request mismatch")
				}
				b, _ := io.ReadAll(r.Body)
				if string(b) != "0123abcd 1" {
					t.Error("envelope mismatch")
				}
				ircFixturePhoto(w, d, tc.mutate)
			})
			encoded, meta, e := ircReadPhoto(c, d, ircStatus{BootID: "0123abcd"}, 1)
			if (e == nil) != tc.ok {
				t.Fatal("validation result", e)
			}
			if tc.ok && (encoded == "" || meta["captured_at_utc"] != nil || meta["received_at_utc"] == nil) {
				t.Fatal("missing or invented provenance")
			}
		})
	}
}
func TestIRCESP32DurableInputNoReplay(t *testing.T) {
	r, _ := fixtureRuntime(t)
	d := ircFixtureDevice(t)
	ircFixtureConfig(t, r, d)
	var writes atomic.Int32
	var statuses atomic.Int32
	c := ircFixtureClient(t, func(w http.ResponseWriter, req *http.Request) {
		if req.URL.Path == "/v1/status" {
			statuses.Add(1)
			fmt.Fprintf(w, `{"protocol":"0.2","device_id":%q,"boot_id":"0123abcd","high_water":0}`, d.ID)
			return
		}
		n := writes.Add(1)
		b, _ := io.ReadAll(req.Body)
		expected := fmt.Sprintf("0123abcd %d ARM", n)
		if n == 2 {
			expected = "0123abcd 2 KEY 4 0"
		}
		if string(b) != expected {
			t.Error("wrong dispatch order")
		}
		fmt.Fprintf(w, "OK %d dispatched\n", n)
	})
	p := runtimeRequest{Operation: "ircesp32_input", DeviceID: d.ID, Text: "KEY 4 0", RequestID: newID(), MachineID: r.MachineID}
	if _, e := r.ircCallWithClient(p, c); e != nil {
		t.Fatal(e)
	}
	if _, e := r.ircCallWithClient(p, c); e != nil {
		t.Fatal(e)
	}
	if writes.Load() != 2 || statuses.Load() != 1 {
		t.Fatal("completed request replayed")
	}
	p.Text = "KEY 5 0"
	if _, e := r.ircCallWithClient(p, c); e == nil {
		t.Fatal("ID reuse accepted")
	}
}
func TestIRCESP32CaptureReceiptHasNoImageAndNeverRecaptures(t *testing.T) {
	r, _ := fixtureRuntime(t)
	d := ircFixtureDevice(t)
	ircFixtureConfig(t, r, d)
	var photos atomic.Int32
	c := ircFixtureClient(t, func(w http.ResponseWriter, req *http.Request) {
		if req.URL.Path == "/v1/status" {
			fmt.Fprintf(w, `{"protocol":"0.2","device_id":%q,"boot_id":"0123abcd","high_water":0,"camera_pins_verified":true}`, d.ID)
			return
		}
		photos.Add(1)
		ircFixturePhoto(w, d, nil)
	})
	p := runtimeRequest{Operation: "ircesp32_capture", DeviceID: d.ID, RequestID: newID(), MachineID: r.MachineID}
	v, e := r.ircCallWithClient(p, c)
	if e != nil {
		t.Fatal(e)
	}
	blocks := v.(map[string]any)["content"].([]any)
	if len(blocks) != 2 || blocks[1].(map[string]any)["type"] != "image" {
		t.Fatal("not MCP image")
	}
	b, e := os.ReadFile(filepath.Join(r.dir, "requests", p.RequestID+".json"))
	if e != nil {
		t.Fatal(e)
	}
	if bytes.Contains(b, []byte(`"data"`)) || bytes.Contains(b, []byte("/9j/")) {
		t.Fatal("image persisted in receipt")
	}
	if _, e := r.ircCallWithClient(p, c); e == nil {
		t.Fatal("duplicate photo presented as new")
	}
	if photos.Load() != 1 {
		t.Fatal("capture replay")
	}
}
func TestIRCESP32UncertainRequestNotRetried(t *testing.T) {
	r, _ := fixtureRuntime(t)
	d := ircFixtureDevice(t)
	ircFixtureConfig(t, r, d)
	var writes atomic.Int32
	c := ircFixtureClient(t, func(w http.ResponseWriter, req *http.Request) {
		if req.URL.Path == "/v1/status" {
			fmt.Fprintf(w, `{"protocol":"0.2","device_id":%q,"boot_id":"0123abcd","high_water":0}`, d.ID)
			return
		}
		writes.Add(1)
		conn, _, _ := w.(http.Hijacker).Hijack()
		conn.Close()
	})
	p := runtimeRequest{Operation: "ircesp32_input", DeviceID: d.ID, Text: "STOP", RequestID: newID(), MachineID: r.MachineID}
	if _, e := r.ircCallWithClient(p, c); e == nil {
		t.Fatal("uncertainty accepted")
	}
	if _, e := r.ircCallWithClient(p, c); e == nil {
		t.Fatal("uncertainty replayed")
	}
	if writes.Load() != 1 {
		t.Fatal("transport retry")
	}
}

func TestIRCESP32UnverifiedCameraNeverRequestsImage(t *testing.T) {
	r, _ := fixtureRuntime(t)
	d := ircFixtureDevice(t)
	ircFixtureConfig(t, r, d)
	var photos atomic.Int32
	c := ircFixtureClient(t, func(w http.ResponseWriter, req *http.Request) {
		if req.URL.Path == "/v1/status" {
			fmt.Fprintf(w, `{"protocol":"0.2","device_id":%q,"boot_id":"0123abcd","camera_pins_verified":false}`, d.ID)
			return
		}
		photos.Add(1)
	})
	p := runtimeRequest{Operation: "ircesp32_capture", DeviceID: d.ID, RequestID: newID(), MachineID: r.MachineID}
	if _, e := r.ircCallWithClient(p, c); e == nil {
		t.Fatal("unverified camera accepted")
	}
	if photos.Load() != 0 {
		t.Fatal("unverified camera activated")
	}
}
func TestIRCESP32RedirectNeverForwardsToken(t *testing.T) {
	d := ircFixtureDevice(t)
	var requests atomic.Int32
	c := ircFixtureClient(t, func(w http.ResponseWriter, r *http.Request) {
		requests.Add(1)
		w.Header().Set("Location", "http://192.168.4.1/other")
		w.WriteHeader(302)
	})
	if _, e := ircReadStatus(c, d); e == nil {
		t.Fatal("redirect accepted")
	}
	if requests.Load() != 1 {
		t.Fatal("redirect followed")
	}
}
