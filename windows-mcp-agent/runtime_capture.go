package main

import (
	"bytes"
	"context"
	_ "embed"
	"encoding/base64"
	"encoding/json"
	"errors"
	"io"
	"math"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"time"
)

//go:embed capture_worker.ps1
var captureWorker string

type captureOutput struct {
	OK       bool            `json:"ok"`
	Error    string          `json:"error"`
	Image    string          `json:"image"`
	Metadata json.RawMessage `json:"metadata"`
}
// imageCaptureSnapshot binds one screenshot's image-pixel space to the real
// desktop.  It is private, short-lived and consumed by a single mouse action.
type captureGeometry struct {
 OriginX int `json:"origin_x"`
 OriginY int `json:"origin_y"`
 OriginalWidth int `json:"original_width"`
 OriginalHeight int `json:"original_height"`
 ImageWidth int `json:"image_width"`
 ImageHeight int `json:"image_height"`
}
type imageCaptureSnapshot struct {
 ID string
 Created time.Time
 Geometry captureGeometry
 ForegroundHWND int64
}
// Convert a pixel centre, not a pixel edge.  This avoids a rounding bias at
// non-integral scale factors and supports negative-origin secondary monitors.
func (g captureGeometry) physical(x, y int) (int, int, error) {
 if g.ImageWidth < 1 || g.ImageHeight < 1 || g.OriginalWidth < 1 || g.OriginalHeight < 1 ||
    g.OriginalWidth > 16384 || g.OriginalHeight > 16384 ||
    x < 0 || y < 0 || x >= g.ImageWidth || y >= g.ImageHeight {
  return 0, 0, errors.New("point_outside_captured_image")
 }
 xx := g.OriginX + int(math.Floor((float64(x)+0.5)*float64(g.OriginalWidth)/float64(g.ImageWidth)))
 yy := g.OriginY + int(math.Floor((float64(y)+0.5)*float64(g.OriginalHeight)/float64(g.ImageHeight)))
 if xx < g.OriginX || xx >= g.OriginX+g.OriginalWidth || yy < g.OriginY || yy >= g.OriginY+g.OriginalHeight {
  return 0, 0, errors.New("coordinate_transform_out_of_bounds")
 }
 return xx, yy, nil
}
type captureBuffer struct {
	bytes.Buffer
	overflow bool
}

func (b *captureBuffer) Write(p []byte) (int, error) {
	n := len(p)
	space := 3*1024*1024 - b.Len()
	if len(p) > space {
		p = p[:space]
		b.overflow = true
	}
	b.Buffer.Write(p)
	return n, nil
}
func (a *Agent) screenCapture(p runtimeRequest) (any, error) {
	if runtime.GOOS != "windows" || os.Getenv("JARVIS_DESKTOP_ENABLED") != "1" {
		return nil, errors.New("screen capture requires enabled Windows interactive desktop")
	}
	if p.MaxWidth == 0 {
		p.MaxWidth = 1600
	}
	if p.Quality == 0 {
		p.Quality = 75
	}
	if p.MaxWidth < 320 || p.MaxWidth > 2560 || p.Quality < 20 || p.Quality > 90 || p.Monitor < 0 || len(p.Region) != 0 && len(p.Region) != 4 {
		return nil, errors.New("invalid capture size, quality, monitor or region")
	}
	// Enforced at the agent, not in ChatGPT: no more than one screenshot per second.
	a.captureMu.Lock()
	defer a.captureMu.Unlock()
	now := time.Now()
	if !captureRateAllowed(a.lastScreenCapture, now) {
		return nil, errors.New("screen_capture rate limit: at most one image per second")
	}
	a.lastScreenCapture = now
	dir, e := os.MkdirTemp("", "jarvis-capture-")
	if e != nil {
		return nil, e
	}
	defer os.RemoveAll(dir)
	path := filepath.Join(dir, "capture.ps1")
	if e = os.WriteFile(path, []byte(captureWorker), 0600); e != nil {
		return nil, e
	}
	ctx, cancel := context.WithTimeout(context.Background(), 12*time.Second)
	defer cancel()
	ps := filepath.Join(os.Getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe")
	c := exec.CommandContext(ctx, ps, "-NoLogo", "-NoProfile", "-NonInteractive", "-STA", "-ExecutionPolicy", "Bypass", "-File", path)
	c.Env = childEnvironment()
	configureDesktopCommand(c)
	configureTreeCancellation(c)
	input, _ := json.Marshal(map[string]any{"monitor": p.Monitor, "region": p.Region, "max_width": p.MaxWidth, "quality": p.Quality})
	c.Stdin = bytes.NewReader(input)
	out := &captureBuffer{}
	c.Stdout = out
	c.Stderr = io.Discard
	if e = c.Run(); e != nil || out.overflow {
		return nil, errors.New("capture unavailable or exceeded output limit")
	}
	var v captureOutput
	if json.Unmarshal(out.Bytes(), &v) != nil || !v.OK {
		return nil, errors.New("capture refused: " + v.Error)
	}
	b, e := base64.StdEncoding.DecodeString(v.Image)
	if e != nil || len(b) > 2*1024*1024 || len(b) < 4 || b[0] != 0xff || b[1] != 0xd8 {
		return nil, errors.New("invalid capture image")
	}
	var metadata map[string]any
	if json.Unmarshal(v.Metadata, &metadata) != nil {
		return nil, errors.New("invalid capture metadata")
	}
	var geometry captureGeometry
	if json.Unmarshal(v.Metadata, &geometry) != nil {
		return nil, errors.New("invalid capture geometry")
	}
	if _, _, e = geometry.physical(0, 0); e != nil {
		return nil, e
	}
	foreground, ok := metadata["foreground_hwnd"].(float64)
	if !ok || foreground <= 0 {return nil, errors.New("screenshot missing foreground identity")}
	id := newID()
	a.mu.Lock()
	a.capture = &imageCaptureSnapshot{ID: id, Created: time.Now(), Geometry: geometry, ForegroundHWND: int64(foreground)}
	a.mu.Unlock()
	metadata["machine_id"] = p.MachineID
	metadata["capture_id"] = id
	metadata["capture_expires_seconds"] = 45
	text, _ := json.Marshal(metadata)
	return map[string]any{"isError": false, "content": []any{map[string]any{"type": "text", "text": string(text)}, map[string]any{"type": "image", "mimeType": "image/jpeg", "data": v.Image}}}, nil
}
