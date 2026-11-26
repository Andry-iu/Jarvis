package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http/httptest"
	"slices"
	"strings"
	"testing"
	"time"
)

type ircSerialFixture struct {
	reply bytes.Buffer
	run   func(string) (string, []byte)
}

func (f *ircSerialFixture) setDeadline(time.Time)      {}
func (f *ircSerialFixture) Close() error               { return nil }
func (f *ircSerialFixture) Read(p []byte) (int, error) { return f.reply.Read(p) }

func (f *ircSerialFixture) Write(p []byte) (int, error) {
	fields := strings.SplitN(strings.TrimSpace(string(p)), " ", 3)
	if len(fields) != 3 || fields[0] != "IRC4" || len(fields[1]) != 16 {
		return 0, fmt.Errorf("bad command frame: %q", p)
	}
	response, payload := f.run(fields[2])
	fmt.Fprintf(&f.reply, "boot noise\r\nIRC4 0000000000000000 STATUS {}\n%sIRC4 %s %s\n", p, fields[1], response)
	f.reply.Write(payload)
	return len(p), nil
}

func TestIRCESP32USBDiscoveryControlAndDisconnect(t *testing.T) {
	r, _ := fixtureRuntime(t)
	adapter := ircSerialPort{"COM7", `USB\VID_FIXTURE`}
	status := ircStatus{Protocol: "0.4", Kind: "autech-ircesp32", DeviceID: "ircesp32-0123456789ab",
		BootID: "0123abcd", PinsVerified: true, HIDReady: true, CameraState: "ready"}
	var commands []string
	var payload = httptest.NewRecorder()
	ircFixturePhoto(payload, ircDevice{ID: status.DeviceID}, nil)
	jpeg := payload.Body.Bytes()
	open := func(p ircSerialPort) (ircSerialIO, error) {
		if p != adapter {
			t.Fatalf("opened wrong USB adapter: %+v", p)
		}
		return &ircSerialFixture{run: func(request string) (string, []byte) {
			commands = append(commands, request)
			switch request {
			case "STATUS":
				data, _ := json.Marshal(status)
				return "STATUS " + string(data), nil
			case "CAPTURE 0123abcd 1":
				return fmt.Sprintf("JPEG %s %s 1 %d 2 3 1000 1001 %s", status.DeviceID, status.BootID, len(jpeg), digest(jpeg)), jpeg
			case "COMMAND 0123abcd 2 ARM":
				return "REPLY OK 2 armed_3000ms", nil
			case "COMMAND 0123abcd 3 MOUSE 1 -1 0 0":
				return "REPLY OK 3 dispatched", nil
			default:
				t.Fatalf("unexpected USB request: %s", request)
				return "", nil
			}
		}}, nil
	}
	ports := func() ([]ircSerialPort, error) { return []ircSerialPort{adapter}, nil }
	if _, err := r.ircDiscoverSerial("com7", ports, open); err != nil || !slices.Equal(commands, []string{"STATUS"}) {
		t.Fatalf("USB discovery should only read status: %v %v", err, commands)
	}
	if r.ircDetected == nil || r.ircDetected.location()["serial_port"] != "COM7" {
		t.Fatal("USB selection was not retained")
	}
	p := runtimeRequest{Operation: "ircesp32_capture", DeviceID: status.DeviceID, RequestID: "bench-usb-photo-001"}
	if out, err := r.ircCallWithTransports(p, nil, open); err != nil || out == nil {
		t.Fatalf("USB capture failed: %v", err)
	}
	status.HighWater = 1
	p = runtimeRequest{Operation: "ircesp32_input", DeviceID: status.DeviceID, RequestID: "bench-usb-input-001", Text: "MOUSE 1 -1 0 0"}
	if _, err := r.ircCallWithTransports(p, nil, open); err != nil {
		t.Fatal(err)
	}
	expected := []string{"STATUS", "STATUS", "CAPTURE 0123abcd 1", "STATUS",
		"COMMAND 0123abcd 2 ARM", "COMMAND 0123abcd 3 MOUSE 1 -1 0 0"}
	if !slices.Equal(commands, expected) {
		t.Fatalf("unexpected USB traffic: %v", commands)
	}
	status.BootID = "deadbeef"
	p.RequestID = "bench-usb-input-002"
	if _, err := r.ircCallWithTransports(p, nil, open); err == nil || !slices.Equal(commands, append(expected, "STATUS")) {
		t.Fatalf("changed USB boot allowed a mutation: %v %v", err, commands)
	}
}

func TestIRCESP32USBRefusesAmbiguousDiscoveryAndCorruptJPEG(t *testing.T) {
	r, _ := fixtureRuntime(t)
	ports := func() ([]ircSerialPort, error) {
		return []ircSerialPort{{name: "COM7"}, {name: "COM8"}}, nil
	}
	status := ircStatus{Protocol: "0.4", Kind: "autech-ircesp32", DeviceID: "ircesp32-0123456789ab",
		BootID: "0123abcd", PinsVerified: true, CameraState: "ready"}
	data, _ := json.Marshal(status)
	w := httptest.NewRecorder()
	ircFixturePhoto(w, ircDevice{ID: status.DeviceID}, nil)
	jpeg := w.Body.Bytes()
	for _, corrupt := range []string{"length", "hash", "stale", "truncated"} {
		open := func(ircSerialPort) (ircSerialIO, error) {
			return &ircSerialFixture{run: func(p string) (string, []byte) {
				if p == "STATUS" {
					return "STATUS " + string(data), nil
				}
				size, hash, captured, body := len(jpeg), digest(jpeg), 1001, jpeg
				switch corrupt {
				case "length":
					size = ircMaxJPEG + 1
				case "hash":
					hash = strings.Repeat("0", 64)
				case "stale":
					captured = 999
				case "truncated":
					body = jpeg[:len(jpeg)-1]
				}
				return fmt.Sprintf("JPEG %s %s 1 %d 2 3 1000 %d %s", status.DeviceID, status.BootID, size, captured, hash), body
			}}, nil
		}
		if _, err := r.ircDiscoverSerial("", ports, open); err == nil || r.ircDetected != nil {
			t.Fatal("ambiguous USB discovery selected a device")
		}
		if _, err := r.ircDiscoverSerial("COM8", ports, open); err != nil {
			t.Fatal(err)
		}
		p := runtimeRequest{Operation: "ircesp32_capture", DeviceID: status.DeviceID, RequestID: "bench-usb-corrupt-" + corrupt}
		if result, err := r.ircCallWithTransports(p, nil, open); err == nil || result != nil {
			t.Fatalf("accepted %s USB photo", corrupt)
		}
	}
}
