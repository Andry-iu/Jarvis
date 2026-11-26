package main

import (
	"bytes"
	"fmt"
	"image"
	"image/color"
	"image/jpeg"
	"io"
	"math"
	"net/http"
	"sync/atomic"
	"testing"
	"time"
)

func TestIRCESP32PerspectiveFromTwoJPEGs(t *testing.T) {
	screenCorners := ircQuad{{0, 0}, {1, 0}, {1, 1}, {0, 1}}
	cameraMonitor := ircQuad{{.14, .14}, {.84, .08}, {.92, .82}, {.11, .92}}
	photoToScreen, err := ircFitPerspective(cameraMonitor, screenCorners)
	if err != nil {
		t.Fatal(err)
	}
	windowsJPEG := ircCalibrationFixture(t, ircHomography{1, 0, 0, 0, 1}, 1200, 675)
	cameraJPEG := ircCalibrationFixture(t, photoToScreen, 800, 600)
	reference, err := ircFindRectangle(windowsJPEG)
	if err != nil || !ircReferenceRectangle(reference) {
		t.Fatalf("Windows reference %v: %v", reference, err)
	}
	photo, err := ircFindRectangle(cameraJPEG)
	if err != nil {
		t.Fatal(err)
	}
	got, err := ircCalibrationMatrix(photo, reference)
	if err != nil {
		t.Fatal(err)
	}
	for _, p := range []ircPoint{{.18, .18}, {.80, .13}, {.48, .45}, {.89, .80}, {.14, .87}} {
		expected, _ := ircProject(photoToScreen, p)
		actual, ok := ircProject(got, p)
		if !ok || math.Hypot(expected.X-actual.X, expected.Y-actual.Y) > .006 {
			t.Errorf("photo point %v: got %v, want %v", p, actual, expected)
		}
	}
}

func ircCalibrationFixture(t *testing.T, transform ircHomography, width, height int) image.Image {
	t.Helper()
	img := image.NewGray(image.Rect(0, 0, width, height))
	for y := range height {
		for x := range width {
			p, _ := ircProject(transform, ircPoint{(float64(x) + .5) / float64(width), (float64(y) + .5) / float64(height)})
			var level uint8 = 25 // dark surrounding room
			if p.X >= 0 && p.X < 1 && p.Y >= 0 && p.Y < 1 {
				level = 218
				if p.X >= .125 && p.X < .875 && p.Y >= .13 && p.Y < .87 {
					level = 58
				}
			}
			img.SetGray(x, y, color.Gray{Y: level + uint8((x+3*y)%13)})
		}
	}
	var encoded bytes.Buffer
	if err := jpeg.Encode(&encoded, img, &jpeg.Options{Quality: 64}); err != nil {
		t.Fatal(err)
	}
	out, err := jpeg.Decode(bytes.NewReader(encoded.Bytes()))
	if err != nil {
		t.Fatal(err)
	}
	return out
}

func TestIRCESP32CalibrationRejectsWrongPatternAndCroppedScreen(t *testing.T) {
	white := image.NewGray(image.Rect(0, 0, 400, 250))
	for i := range white.Pix {
		white.Pix[i] = 240
	}
	if _, err := ircFindRectangle(white); err == nil {
		t.Fatal("a blank screen was accepted")
	}
	for _, r := range []image.Rectangle{image.Rect(25, 56, 175, 188), image.Rect(225, 56, 375, 188)} {
		for y := r.Min.Y; y < r.Max.Y; y++ {
			for x := r.Min.X; x < r.Max.X; x++ {
				white.SetGray(x, y, color.Gray{Y: 24})
			}
		}
	}
	if _, err := ircFindRectangle(white); err == nil {
		t.Fatal("multiple rectangles were accepted")
	}
	small := ircQuad{{.2, .2}, {.8, .2}, {.8, .8}, {.2, .8}}
	big := ircQuad{{.05, .05}, {.95, .05}, {.95, .95}, {.05, .95}}
	if _, err := ircCalibrationMatrix(big, small); err == nil {
		t.Fatal("cropped monitor was accepted")
	}
}

func TestIRCESP32CameraMoveUsesPersistedCalibrationOnce(t *testing.T) {
	r, _ := fixtureRuntime(t)
	d := ircFixtureDevice(t)
	ircFixtureConfig(t, r, d)
	calibration := ircCalibration{
		Version: 1, ID: newID(), DeviceID: d.ID, TargetMachineID: newID(),
		Screen: captureGeometry{OriginalWidth: 1000, OriginalHeight: 600},
		Width:  500, Height: 300, PhotoToScreen: ircHomography{1, 0, 0, 0, 1}, CreatedAt: utcNow(),
	}
	if err := atomicJSON(r.ircCalibrationPath(d.ID), calibration); err != nil {
		t.Fatal(err)
	}
	frame := ircCameraFrame{ID: "frame-1", DeviceID: d.ID, BootID: "0123abcd", Width: 500, Height: 300, Received: time.Now()}
	r.ircFrame = frame
	var writes atomic.Int32
	c := ircFixtureClient(t, func(w http.ResponseWriter, req *http.Request) {
		switch req.URL.Path {
		case "/v1/status":
			fmt.Fprintf(w, `{"protocol":"0.2","device_id":%q,"boot_id":"0123abcd","high_water":0}`, d.ID)
		case "/v1/command":
			n := writes.Add(1)
			body, _ := io.ReadAll(req.Body)
			if n == 1 && string(body) != "0123abcd 1 ARM" || n == 2 && string(body) != "0123abcd 2 MOUSE 32 16 0 0" || n > 2 {
				t.Errorf("unexpected HID request: %s", body)
			}
			fmt.Fprintf(w, "OK %d dispatched", n)
		default:
			t.Errorf("unexpected endpoint: %s", req.URL.Path)
		}
	})
	ex, ey := 300, 200
	p := runtimeRequest{Operation: "ircesp32_mouse_camera", DeviceID: d.ID, MachineID: r.MachineID,
		RequestID: newID(), CaptureID: frame.ID, X: 100, Y: 100, EndX: &ex, EndY: &ey}
	if _, err := r.ircCallWithClient(p, c); err != nil {
		t.Fatal(err)
	}
	if _, err := r.ircCallWithClient(p, c); err != nil || writes.Load() != 2 {
		t.Fatal("same request did not return its receipt without a second dispatch", err)
	}
	p.RequestID = newID()
	if _, err := r.ircCallWithClient(p, c); err == nil || writes.Load() != 2 {
		t.Fatal("used photo accepted for a new move")
	}
	r.ircFrame = frame
	r.ircFrame.Received = time.Now().Add(-46 * time.Second)
	p.RequestID = newID()
	if _, err := r.ircCallWithClient(p, c); err == nil || writes.Load() != 2 {
		t.Fatal("expired photo accepted for a new move")
	}
}

func TestIRCESP32CalibrationStartReturnsInstructionsWithoutCapture(t *testing.T) {
	p, err := decodeRuntime("ircesp32_calibrate", []byte(`{"bitmap_ready":false}`))
	if err != nil {
		t.Fatal(err)
	}
	v, err := (&Agent{}).ircCalibrate(nil, p)
	if err != nil || v.(map[string]any)["state"] != "awaiting_bitmap_confirmation" {
		t.Fatalf("expected instructions only: %v %v", v, err)
	}
}
