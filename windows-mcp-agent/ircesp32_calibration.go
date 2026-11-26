package main

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"image"
	"image/jpeg"
	"math"
	"os"
	"path/filepath"
	"time"
)

const ircCameraMoveLimit = 32

type ircCalibration struct {
	Version         int             `json:"version"`
	ID              string          `json:"calibration_id"`
	DeviceID        string          `json:"device_id"`
	TargetMachineID string          `json:"target_machine_id"`
	Monitor         int             `json:"monitor"`
	Screen          captureGeometry `json:"windows_screen"`
	Width           int             `json:"photo_width"`
	Height          int             `json:"photo_height"`
	PhotoToScreen   ircHomography   `json:"photo_to_screen"`
	CreatedAt       string          `json:"calibrated_at"`
}

type ircCameraFrame struct {
	ID, DeviceID, BootID string
	Width, Height        int
	Received             time.Time
}

func init() {
	runtimeCatalog = append(runtimeCatalog,
		runtimeTool{"ircesp32_calibrate", "Start here when the user asks in chat to calibrate IRCESP32 perspective. First call with bitmap_ready=false, relay its instructions and wait for the user's explicit confirmation in chat. Then call with bitmap_ready=true, device_id, target_device_id (a paired Windows device from device_list, or local only if the camera films the bridge's own monitor), monitor (one-based screen_capture index) and a unique request_id. Leave the bitmap visible until the tool returns. The bridge captures both views and persists their perspective transform. No mouse input.", []string{"device_id", "target_device_id", "monitor", "bitmap_ready", "request_id"}, []string{"bitmap_ready"}, false},
		runtimeTool{"ircesp32_mouse_camera", "Make one relative USB mouse correction using the active IRCESP32 perspective calibration. Use x,y for the cursor tip and end_x,end_y for the destination, both in original JPEG pixels from the same fresh ircesp32_capture. Send its capture_id and a unique request_id. Verify position in another photo before any further correction or separate click. No click or wheel is sent.", []string{"device_id", "capture_id", "x", "y", "end_x", "end_y", "request_id"}, []string{"device_id", "capture_id", "x", "y", "end_x", "end_y", "request_id"}, false},
	)
}

func calibrationJPEG(result any) (image.Image, json.RawMessage, error) {
	var reply struct {
		IsError bool `json:"isError"`
		Content []struct {
			Type string `json:"type"`
			Text string `json:"text"`
			Mime string `json:"mimeType"`
			Data string `json:"data"`
		} `json:"content"`
	}
	b, err := json.Marshal(result)
	if err != nil || json.Unmarshal(b, &reply) != nil || len(reply.Content) == 0 {
		return nil, nil, errors.New("calibration capture returned an invalid reply")
	}
	if reply.IsError {
		return nil, nil, fmt.Errorf("calibration capture refused: %.500s", reply.Content[0].Text)
	}
	if len(reply.Content) != 2 || reply.Content[0].Type != "text" || reply.Content[1].Type != "image" || reply.Content[1].Mime != "image/jpeg" ||
		len(reply.Content[1].Data) > base64.StdEncoding.EncodedLen(2*1024*1024) {
		return nil, nil, errors.New("calibration capture did not return the expected JPEG and metadata")
	}
	data, err := base64.StdEncoding.DecodeString(reply.Content[1].Data)
	if err != nil {
		return nil, nil, errors.New("calibration capture returned invalid JPEG data")
	}
	cfg, err := jpeg.DecodeConfig(bytes.NewReader(data))
	if err != nil || cfg.Width < 100 || cfg.Height < 100 || cfg.Width > 2560 || cfg.Height > 16384 || cfg.Width*cfg.Height > 8_000_000 {
		return nil, nil, errors.New("calibration JPEG dimensions out of range")
	}
	img, err := jpeg.Decode(bytes.NewReader(data))
	return img, json.RawMessage(reply.Content[0].Text), err
}

func (a *Agent) ircCalibrate(r *Runtime, p runtimeRequest) (any, error) {
	if !p.BitmapReady {
		return map[string]any{
			"state":        "awaiting_bitmap_confirmation",
			"instructions": "Apri a tutto schermo, sul monitor inquadrato dalla ESP32, una bitmap bianca con un unico grande rettangolo nero pieno e un margine bianco su tutti i lati. Lascia visibili tutti e quattro gli angoli del monitor, sposta il cursore verso il margine e dimmi quando la bitmap è visibile.",
		}, nil
	}
	if !ircID.MatchString(p.DeviceID) || p.TargetDeviceID == "" || p.Monitor < 1 || p.Monitor > 64 {
		return nil, errors.New("calibration requires the ESP32 device, an explicit Windows target and one monitor index")
	}
	r.ircCalibrationMu.Lock()
	defer r.ircCalibrationMu.Unlock()
	return r.externalMutation(p, func() (any, error) {
		ref := runtimeRequest{Operation: "screen_capture", MachineID: r.MachineID, Monitor: p.Monitor, MaxWidth: 1920, Quality: 85}
		var windows any
		var err error
		if p.TargetDeviceID == "local" {
			windows, err = a.screenCapture(ref)
		} else {
			ref.MachineID = ""
			raw, _ := json.Marshal(ref)
			windows, err = r.deviceCall(runtimeRequest{DeviceID: p.TargetDeviceID, Call: raw})
		}
		if err != nil {
			return nil, fmt.Errorf("Windows reference capture: %w", err)
		}
		screen, raw, err := calibrationJPEG(windows)
		if err != nil {
			return nil, fmt.Errorf("Windows reference capture: %w", err)
		}
		var refMeta struct {
			captureGeometry
			MachineID string `json:"machine_id"`
		}
		if json.Unmarshal(raw, &refMeta) != nil || !requestIDPattern.MatchString(refMeta.MachineID) ||
			refMeta.ImageWidth != screen.Bounds().Dx() || refMeta.ImageHeight != screen.Bounds().Dy() {
			return nil, errors.New("Windows reference capture identity or dimensions invalid")
		}
		if _, _, err := refMeta.physical(0, 0); err != nil {
			return nil, err
		}
		reference, err := ircFindRectangle(screen)
		if err != nil {
			return nil, fmt.Errorf("Windows reference: %w", err)
		}
		if !ircReferenceRectangle(reference) {
			return nil, errors.New("Windows reference rectangle must be large and aligned on the selected monitor")
		}
		camera, err := r.ircCall(runtimeRequest{Operation: "ircesp32_capture", MachineID: r.MachineID, DeviceID: p.DeviceID, RequestID: newID()})
		if err != nil {
			return nil, fmt.Errorf("ESP32 calibration capture: %w", err)
		}
		photo, raw, err := calibrationJPEG(camera)
		if err != nil {
			return nil, fmt.Errorf("ESP32 calibration capture: %w", err)
		}
		var photoMeta struct {
			DeviceID  string `json:"device_id"`
			CaptureID string `json:"capture_id"`
		}
		if json.Unmarshal(raw, &photoMeta) != nil || photoMeta.DeviceID != p.DeviceID {
			return nil, errors.New("ESP32 calibration capture device identity mismatch")
		}
		corners, err := ircFindRectangle(photo)
		if err != nil {
			return nil, fmt.Errorf("ESP32 photo: %w", err)
		}
		matrix, err := ircCalibrationMatrix(corners, reference)
		if err != nil {
			return nil, err
		}
		profile := ircCalibration{
			Version: 1, ID: newID(), DeviceID: p.DeviceID, TargetMachineID: refMeta.MachineID,
			Monitor: p.Monitor, Screen: refMeta.captureGeometry, Width: photo.Bounds().Dx(),
			Height: photo.Bounds().Dy(), PhotoToScreen: matrix, CreatedAt: utcNow(),
		}
		r.ircMu.Lock()
		defer r.ircMu.Unlock()
		if err := atomicJSON(r.ircCalibrationPath(p.DeviceID), profile); err != nil {
			return nil, fmt.Errorf("save calibration: %w", err)
		}
		if r.ircFrame.ID == photoMeta.CaptureID {
			r.ircFrame = ircCameraFrame{}
		}
		return map[string]any{"state": "calibrated", "calibration": profile}, nil
	})
}

func ircReferenceRectangle(q ircQuad) bool {
	return q[1].X-q[0].X >= .45 && q[3].Y-q[0].Y >= .40 &&
		math.Abs(q[0].Y-q[1].Y) < .01 && math.Abs(q[1].X-q[2].X) < .01 &&
		math.Abs(q[2].Y-q[3].Y) < .01 && math.Abs(q[3].X-q[0].X) < .01
}

func ircCalibrationMatrix(photo, screen ircQuad) (ircHomography, error) {
	matrix, err := ircFitPerspective(photo, screen)
	if err != nil {
		return matrix, err
	}
	inverse, err := ircFitPerspective(screen, photo)
	if err != nil {
		return matrix, err
	}
	for _, corner := range (ircQuad{{0, 0}, {1, 0}, {1, 1}, {0, 1}}) {
		q, ok := ircProject(inverse, corner)
		if !ok || q.X < -.01 || q.X > 1.01 || q.Y < -.01 || q.Y > 1.01 {
			return matrix, errors.New("calibration requires the entire selected monitor in the camera frame")
		}
		// A horizon inside the monitor would make extrapolation from its inner rectangle unsafe.
		if inverse[6]*corner.X+inverse[7]*corner.Y+1 < 1e-6 || matrix[6]*q.X+matrix[7]*q.Y+1 < 1e-6 {
			return matrix, errors.New("calibration perspective is too steep; align the camera")
		}
	}
	return matrix, nil
}

func (r *Runtime) ircCalibrationPath(deviceID string) string {
	return filepath.Join(r.dir, "irc-calibration-"+deviceID+".json")
}

func (c ircCalibration) screenPoint(x, y int) (ircPoint, error) {
	if x < 0 || y < 0 || x >= c.Width || y >= c.Height {
		return ircPoint{}, errors.New("point outside ESP32 camera frame")
	}
	q, ok := ircProject(c.PhotoToScreen, ircPoint{(float64(x) + .5) / float64(c.Width), (float64(y) + .5) / float64(c.Height)})
	if !ok || q.X < 0 || q.X >= 1 || q.Y < 0 || q.Y >= 1 {
		return ircPoint{}, errors.New("point outside the calibrated monitor")
	}
	return ircPoint{float64(c.Screen.OriginX) + q.X*float64(c.Screen.OriginalWidth), float64(c.Screen.OriginY) + q.Y*float64(c.Screen.OriginalHeight)}, nil
}

// Called under ircMu after the device status has been verified.
func (r *Runtime) ircCameraMove(p runtimeRequest, status ircStatus) (string, map[string]any, error) {
	if p.EndX == nil || p.EndY == nil || p.CaptureID == "" {
		return "", nil, errors.New("camera move requires the cursor, destination and capture ID")
	}
	f := r.ircFrame
	if f.ID != p.CaptureID || f.DeviceID != p.DeviceID || f.BootID != status.BootID || time.Since(f.Received) > 45*time.Second {
		return "", nil, errors.New("ESP32 photo expired, used or superseded; take a new photo")
	}
	data, err := os.ReadFile(r.ircCalibrationPath(p.DeviceID))
	if err != nil {
		return "", nil, errors.New("calibrate this ESP32 on the bridge first")
	}
	var c ircCalibration
	if json.Unmarshal(data, &c) != nil || c.Version != 1 || c.DeviceID != p.DeviceID ||
		c.Width != f.Width || c.Height != f.Height {
		return "", nil, errors.New("calibration unavailable for this camera format; recalibrate")
	}
	saved, err := time.Parse(time.RFC3339Nano, c.CreatedAt)
	if err != nil || !f.Received.After(saved) {
		return "", nil, errors.New("capture a fresh photo after calibration")
	}
	from, err := c.screenPoint(p.X, p.Y)
	if err != nil {
		return "", nil, err
	}
	to, err := c.screenPoint(*p.EndX, *p.EndY)
	if err != nil {
		return "", nil, err
	}
	dx, dy := to.X-from.X, to.Y-from.Y
	scale := math.Max(1, math.Max(math.Abs(dx), math.Abs(dy))/ircCameraMoveLimit)
	hx, hy := int(math.Round(dx/scale)), int(math.Round(dy/scale))
	r.ircFrame = ircCameraFrame{}
	v := map[string]any{
		"calibration_id": c.ID, "device_id": p.DeviceID, "target_machine_id": c.TargetMachineID,
		"estimated_cursor": from, "estimated_destination": to, "hid_dx": hx, "hid_dy": hy, "target_effect_verified": false,
	}
	if hx == 0 && hy == 0 {
		v["state"] = "within_one_estimated_pixel"
		return "", v, nil
	}
	return fmt.Sprintf("MOUSE %d %d 0 0", hx, hy), v, nil
}
