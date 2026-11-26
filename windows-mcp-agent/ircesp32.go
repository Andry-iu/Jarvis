package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"image/jpeg"
	"io"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"
)

type ircDevice struct {
	ID       string `json:"device_id"`
	Label    string `json:"label"`
	Endpoint string `json:"endpoint"`
	TokenEnv string `json:"token_env"`
	linkID   uint32
	bootID   string
}
type ircStatus struct {
	Protocol     string `json:"protocol"`
	DeviceID     string `json:"device_id"`
	BootID       string `json:"boot_id"`
	HighWater    uint32 `json:"high_water"`
	Armed        bool   `json:"armed"`
	CameraState  string `json:"camera_state"`
	PinsVerified bool   `json:"camera_pins_verified"`
	HIDReady     bool   `json:"usb_hid_ready"`
	Kind         string `json:"kind,omitempty"`
	LinkID       uint32 `json:"link_id,omitempty"`
}

var ircID = regexp.MustCompile(`^ircesp32-[0-9a-f]{12}$`)
var ircBoot = regexp.MustCompile(`^[0-9a-f]{8}$`)
var ircToken = regexp.MustCompile(`^[0-9a-fA-F]{64}$`)
var ircTokenEnv = regexp.MustCompile(`^JARVIS_IRCESP32_TOKEN_[A-Z0-9_]+$`)

func init() {
	runtimeCatalog = append(runtimeCatalog, []runtimeTool{
		{"ircesp32_discover", "Detect one IRCESP32 on this Windows bridge for later explicit status, photo or input requests. Use transport=usb for USB-TTL (optional serial_port, e.g. COM7), or transport=wifi or omit it for the connected private hotspot (optional interface_name). Discovery takes no photos and sends no HID input.", []string{"transport", "serial_port", "interface_name"}, nil, true},
		{"ircesp32_list", "List configured and previously detected IRCESP32 devices on this bridge. Call ircesp32_discover for a fresh detection or ircesp32_status for live status.", nil, nil, true},
		{"ircesp32_status", "Read IRCESP32 identity, boot, camera readiness and HID state on its selected USB-TTL or Wi-Fi connection.", []string{"device_id"}, []string{"device_id"}, true},
		{"ircesp32_capture", "Request one fresh camera JPEG and return an MCP image with identity, hash, device uptime and UTC receive time. Refuses unverified pinout. Unique request_id; never replay on timeout.", []string{"device_id", "request_id"}, []string{"device_id", "request_id"}, false},
		{"ircesp32_input", "Send one USB HID action through the configured IRCESP32: text KEY <usage> <modifiers>, MOUSE <dx> <dy> <wheel> <buttons>, or STOP. Automatically arms immediately before one action, releases afterwards. Dispatch is not target effect; no replay.", []string{"device_id", "text", "request_id"}, []string{"device_id", "text", "request_id"}, false},
	}...)
}

func (d ircDevice) validate() error {
	u, e := url.Parse(d.Endpoint)
	if e != nil || u.Scheme != "http" || u.User != nil || u.Path != "" || u.RawQuery != "" || u.Fragment != "" || u.Opaque != "" || u.ForceQuery {
		return errors.New("IRCESP32 endpoint must be a private literal IPv4 HTTP origin")
	}
	ip, e := netip.ParseAddr(u.Hostname())
	if e != nil || !ip.Is4() || !ip.IsPrivate() || (u.Port() != "" && u.Port() != "80") || u.Host == "" {
		return errors.New("IRCESP32 endpoint must use private IPv4 port 80")
	}
	if !ircID.MatchString(d.ID) || !ircTokenEnv.MatchString(d.TokenEnv) || len(d.Label) > 80 {
		return errors.New("invalid IRCESP32 device configuration")
	}
	return nil
}
func (r *Runtime) ircDevices() ([]ircDevice, error) {
	path := filepath.Join(r.home, "Config", "ircesp32.json")
	f, e := os.Lstat(path)
	if os.IsNotExist(e) {
		return []ircDevice{}, nil
	}
	if e != nil || !f.Mode().IsRegular() || f.Size() > 32768 {
		return nil, errors.New("IRCESP32 configuration unavailable or invalid")
	}
	b, e := os.ReadFile(path)
	if e != nil {
		return nil, errors.New("IRCESP32 configuration unavailable")
	}
	var v struct {
		Devices []ircDevice `json:"devices"`
	}
	dec := json.NewDecoder(bytes.NewReader(b))
	dec.DisallowUnknownFields()
	if dec.Decode(&v) != nil || len(v.Devices) > 16 {
		return nil, errors.New("invalid IRCESP32 configuration")
	}
	var trailing any
	if dec.Decode(&trailing) != io.EOF {
		return nil, errors.New("invalid IRCESP32 configuration")
	}
	ids := map[string]bool{}
	endpoints := map[string]bool{}
	for _, d := range v.Devices {
		if d.validate() != nil || ids[d.ID] || endpoints[d.Endpoint] {
			return nil, errors.New("invalid or duplicate IRCESP32 configuration")
		}
		ids[d.ID] = true
		endpoints[d.Endpoint] = true
	}
	return v.Devices, nil
}
func ircHTTPClient() *http.Client {
	return &http.Client{Timeout: 20 * time.Second, CheckRedirect: func(*http.Request, []*http.Request) error { return errors.New("IRCESP32 redirects refused") }, Transport: &http.Transport{
		Proxy: nil, DialContext: (&net.Dialer{Timeout: 3 * time.Second}).DialContext, DisableKeepAlives: true,
		ResponseHeaderTimeout: 12 * time.Second, MaxResponseHeaderBytes: 8192,
	}}
}
func ircRequest(c *http.Client, d ircDevice, method, path, body string) (*http.Response, error) {
	var token string
	if d.linkID == 0 && path != "/v1/discover" {
		token = os.Getenv(d.TokenEnv)
	}
	if d.linkID == 0 && path != "/v1/discover" && !ircToken.MatchString(token) {
		return nil, errors.New("IRCESP32 token unavailable or invalid")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	req, e := http.NewRequestWithContext(ctx, method, d.Endpoint+path, strings.NewReader(body))
	if e != nil {
		cancel()
		return nil, errors.New("IRCESP32 request invalid")
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	} else {
		req.Header.Set("X-IR-Client", "jarvis")
		if d.linkID != 0 {
			req.Header.Set("X-IR-Link", strconv.FormatUint(uint64(d.linkID), 10))
		}
	}
	req.Header.Set("Content-Type", "text/plain")
	// Prevent automatic HTTP transport retries: no GetBody and no idempotency headers.
	req.GetBody = nil
	resp, e := c.Do(req)
	if e != nil {
		cancel()
		return nil, errors.New("IRCESP32 connection unavailable or outcome uncertain; inspect before another request")
	}
	resp.Body = &ircBody{ReadCloser: resp.Body, cancel: cancel}
	return resp, nil
}

type ircBody struct {
	io.ReadCloser
	cancel context.CancelFunc
}

func (b *ircBody) Close() error { defer b.cancel(); return b.ReadCloser.Close() }
func ircReadStatus(c *http.Client, d ircDevice) (ircStatus, error) {
	s, e := ircGetStatus(c, d, "/v1/status")
	if e != nil || s.DeviceID != d.ID {
		return ircStatus{}, errors.New("IRCESP32 status refused or device identity mismatch")
	}
	if d.linkID != 0 {
		if !s.hotspotStatus() || s.BootID != d.bootID || s.LinkID != d.linkID {
			return ircStatus{}, errors.New("IRCESP32 hotspot or boot changed; run ircesp32_discover again")
		}
	} else if s.Protocol != "0.2" {
		return ircStatus{}, errors.New("IRCESP32 status protocol mismatch")
	}
	return s, nil
}

func ircGetStatus(c *http.Client, d ircDevice, path string) (ircStatus, error) {
	var s ircStatus
	resp, e := ircRequest(c, d, "GET", path, "")
	if e != nil {
		return s, e
	}
	defer resp.Body.Close()
	b, e := io.ReadAll(io.LimitReader(resp.Body, 4097))
	if e != nil || len(b) > 4096 || resp.StatusCode != 200 {
		return s, errors.New("IRCESP32 status unavailable or malformed")
	}
	return ircParseStatus(b)
}

func ircParseStatus(b []byte) (ircStatus, error) {
	var s ircStatus
	if json.Unmarshal(b, &s) != nil || !ircID.MatchString(s.DeviceID) || !ircBoot.MatchString(s.BootID) {
		return s, errors.New("IRCESP32 status unavailable or malformed")
	}
	return s, nil
}
func ircSendCommand(c *http.Client, d ircDevice, s ircStatus, seq uint32, text string) error {
	body := fmt.Sprintf("%s %d %s", s.BootID, seq, text)
	resp, e := ircRequest(c, d, "POST", "/v1/command", body)
	if e != nil {
		return e
	}
	defer resp.Body.Close()
	b, e := io.ReadAll(io.LimitReader(resp.Body, 257))
	if e != nil || len(b) > 256 || resp.StatusCode != 200 || !strings.HasPrefix(string(b), fmt.Sprintf("OK %d ", seq)) {
		return errors.New("IRCESP32 command refused or result uncertain; do not replay")
	}
	return nil
}
func ircValidateInput(text string) error {
	fields := strings.Split(text, " ")
	var limits [][2]int
	switch {
	case len(fields) == 1 && fields[0] == "STOP":
		return nil
	case len(fields) == 3 && fields[0] == "KEY":
		limits = [][2]int{{0, 115}, {0, 255}}
	case len(fields) == 5 && fields[0] == "MOUSE":
		limits = [][2]int{{-127, 127}, {-127, 127}, {-127, 127}, {0, 7}}
	default:
		return errors.New("expected KEY usage modifiers, MOUSE dx dy wheel buttons or STOP")
	}
	for i, limit := range limits {
		n, e := strconv.Atoi(fields[i+1])
		if e != nil || strconv.Itoa(n) != fields[i+1] || n < limit[0] || n > limit[1] || (fields[0] == "KEY" && i == 0 && n > 0 && n < 4) {
			return errors.New("invalid IRCESP32 input argument")
		}
	}
	return nil
}

const ircMaxJPEG = 1024 * 1024

type ircPhotoHeader struct {
	device, boot, sequence, hash, width, height, started, captured string
}

func ircReadPhoto(c *http.Client, d ircDevice, s ircStatus, seq uint32) (string, map[string]any, error) {
	requested := time.Now().UTC().Format(time.RFC3339Nano)
	resp, e := ircRequest(c, d, "POST", "/v1/capture", fmt.Sprintf("%s %d", s.BootID, seq))
	if e != nil {
		return "", nil, e
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 || resp.Header.Get("Content-Type") != "image/jpeg" {
		return "", nil, errors.New("IRCESP32 photo unavailable; check camera_state, pinout and PSRAM; do not replay")
	}
	b, e := io.ReadAll(io.LimitReader(resp.Body, ircMaxJPEG+1))
	if e != nil {
		return "", nil, errors.New("IRCESP32 incomplete JPEG")
	}
	h := resp.Header
	return ircValidatePhoto(b, d, s, seq, requested, ircPhotoHeader{
		h.Get("X-IR-Device"), h.Get("X-IR-Boot"), h.Get("X-IR-Sequence"), h.Get("X-IR-SHA256"),
		h.Get("X-IR-Width"), h.Get("X-IR-Height"), h.Get("X-IR-Requested-Us"), h.Get("X-IR-Captured-Us"),
	})
}

func ircValidatePhoto(b []byte, d ircDevice, s ircStatus, seq uint32, requested string, header ircPhotoHeader) (string, map[string]any, error) {
	if len(b) < 4 || len(b) > ircMaxJPEG || b[0] != 0xff || b[1] != 0xd8 || b[len(b)-2] != 0xff || b[len(b)-1] != 0xd9 {
		return "", nil, errors.New("IRCESP32 incomplete or invalid JPEG")
	}
	h := sha256.Sum256(b)
	sum := hex.EncodeToString(h[:])
	if header.device != d.ID || header.boot != s.BootID || header.sequence != strconv.FormatUint(uint64(seq), 10) || header.hash != sum {
		return "", nil, errors.New("IRCESP32 photo identity or hash mismatch")
	}
	width, ew := strconv.Atoi(header.width)
	height, eh := strconv.Atoi(header.height)
	started, es := strconv.ParseInt(header.started, 10, 64)
	captured, ec := strconv.ParseInt(header.captured, 10, 64)
	cfg, ej := jpeg.DecodeConfig(bytes.NewReader(b))
	if ew != nil || eh != nil || es != nil || ec != nil || ej != nil || started < 0 || captured < started || width < 1 || height < 1 || width > 2048 || height > 1536 || width != cfg.Width || height != cfg.Height {
		return "", nil, errors.New("IRCESP32 stale frame or invalid photo metadata")
	}
	meta := map[string]any{"source": "ircesp32_camera", "device_id": d.ID, "boot_id": s.BootID, "sequence": seq, "capture_id": fmt.Sprintf("%s-%s-%d", d.ID, s.BootID, seq), "sha256": sum, "width": width, "height": height, "device_requested_uptime_us": started, "device_captured_uptime_us": captured, "clock_source": "device_uptime", "captured_at_utc": nil, "requested_at_utc": requested, "received_at_utc": utcNow()}
	return base64.StdEncoding.EncodeToString(b), meta, nil
}
func (r *Runtime) ircCall(p runtimeRequest) (any, error) {
	return r.ircCallWithClient(p, nil)
}

func (r *Runtime) ircCallWithClient(p runtimeRequest, c *http.Client) (any, error) {
	return r.ircCallWithTransports(p, c, ircOpenSerial)
}

func (r *Runtime) ircCallWithTransports(p runtimeRequest, c *http.Client, openSerial func(ircSerialPort) (ircSerialIO, error)) (any, error) {
	r.ircMu.Lock()
	defer r.ircMu.Unlock()
	detected := r.ircDetected
	if p.Operation == "ircesp32_list" {
		devices, e := r.ircDevices()
		if e != nil {
			return nil, e
		}
		v := []any{}
		if detected != nil {
			v = append(v, detected.location())
		}
		for _, d := range devices {
			if detected != nil && d.ID == detected.device.ID {
				continue
			}
			v = append(v, map[string]any{"device_id": d.ID, "label": d.Label, "endpoint": d.Endpoint})
		}
		return map[string]any{"devices": v, "configured_only": detected == nil}, nil
	}
	var d ircDevice
	if detected != nil && detected.device.ID == p.DeviceID {
		d = detected.device
		if c == nil && detected.serial == nil {
			var e error
			c, e = ircHotspotClient(detected.hotspot)
			if e != nil {
				r.ircDetected = nil
				return nil, e
			}
		}
	} else {
		devices, e := r.ircDevices()
		if e != nil {
			return nil, e
		}
		for _, v := range devices {
			if v.ID == p.DeviceID {
				d = v
				break
			}
		}
	}
	if d.ID == "" {
		return nil, errors.New("IRCESP32 device not configured or detected on this bridge; run ircesp32_discover")
	}
	if p.Operation == "ircesp32_input" {
		if e := ircValidateInput(p.Text); e != nil {
			return nil, e
		}
	}
	var read func() (ircStatus, error)
	var command func(ircStatus, uint32, string) error
	var capture func(ircStatus, uint32) (string, map[string]any, error)
	serialSelected := detected != nil && d.ID == detected.device.ID && detected.serial != nil
	if serialSelected {
		var client *ircSerialClient
		defer func() {
			if client != nil {
				client.port.Close()
			}
		}()
		read = func() (ircStatus, error) {
			port, err := openSerial(*detected.serial)
			if err != nil {
				return ircStatus{}, err
			}
			client = newIRCSerialClient(port)
			s, err := client.status()
			if err == nil && (s.DeviceID != d.ID || s.BootID != d.bootID) {
				err = errors.New("IRCESP32 USB device or boot changed; discover again")
			}
			return s, err
		}
		command = func(s ircStatus, seq uint32, text string) error { return client.command(s, seq, text) }
		capture = func(s ircStatus, seq uint32) (string, map[string]any, error) { return client.capture(d, s, seq) }
	} else {
		if c == nil {
			c = ircHTTPClient()
		}
		defer c.CloseIdleConnections()
		read = func() (ircStatus, error) { return ircReadStatus(c, d) }
		command = func(s ircStatus, seq uint32, text string) error { return ircSendCommand(c, d, s, seq, text) }
		capture = func(s ircStatus, seq uint32) (string, map[string]any, error) { return ircReadPhoto(c, d, s, seq) }
	}
	readStatus := func() (ircStatus, error) {
		s, e := read()
		if e != nil && d.linkID != 0 {
			r.ircDetected = nil
		}
		return s, e
	}
	if p.Operation == "ircesp32_status" {
		return readStatus()
	}
	var image string
	var photo map[string]any
	v, e := r.externalMutation(p, func() (any, error) {
		s, e := readStatus()
		if e != nil {
			return nil, e
		}
		if s.HighWater >= ^uint32(0)-1 {
			return nil, errors.New("IRCESP32 sequence exhausted; restart device explicitly")
		}
		seq := s.HighWater + 1
		if p.Operation == "ircesp32_capture" {
			if !s.PinsVerified {
				return nil, errors.New("IRCESP32 camera pinout unverified; capture refused")
			}
			image, photo, e = capture(s, seq)
			return photo, e
		}
		if p.Text != "STOP" {
			if (serialSelected || d.linkID != 0) && !s.HIDReady {
				return nil, errors.New("IRCESP32 USB HID is not ready on the target")
			}
			if e = command(s, seq, "ARM"); e != nil {
				return nil, e
			}
			seq++
		}
		if e = command(s, seq, p.Text); e != nil {
			return nil, e
		}
		return map[string]any{"device_id": d.ID, "boot_id": s.BootID, "sequence": seq, "state": "dispatched", "target_effect_verified": false}, nil
	})
	if e != nil {
		return nil, e
	}
	if p.Operation != "ircesp32_capture" {
		return v, nil
	}
	if image == "" {
		return nil, errors.New("capture request already completed; read request_status for metadata; no new image acquired")
	}
	photo["machine_id"] = r.MachineID
	photo["request_id"] = p.RequestID
	meta, _ := json.Marshal(photo)
	return map[string]any{"isError": false, "content": []any{map[string]any{"type": "text", "text": string(meta)}, map[string]any{"type": "image", "mimeType": "image/jpeg", "data": image}}}, nil
}
