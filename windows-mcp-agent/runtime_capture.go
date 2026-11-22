package main

import (
	"bytes"
	"context"
	_ "embed"
	"encoding/base64"
	"encoding/json"
	"errors"
	"io"
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
func screenCapture(p runtimeRequest) (any, error) {
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
	metadata["machine_id"] = p.MachineID
	text, _ := json.Marshal(metadata)
	return map[string]any{"isError": false, "content": []any{map[string]any{"type": "text", "text": string(text)}, map[string]any{"type": "image", "mimeType": "image/jpeg", "data": v.Image}}}, nil
}
