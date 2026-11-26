package main

import (
	"errors"
	"net/http"
	"net/netip"
	"time"
)

type ircHotspot struct {
	index  uint32
	name   string
	local  netip.Addr
	server netip.Addr
}

type ircDetectedDevice struct {
	device  ircDevice
	hotspot ircHotspot
	serial  *ircSerialPort
}

func (d ircDetectedDevice) location() map[string]any {
	if d.serial != nil {
		return map[string]any{"device_id": d.device.ID, "serial_port": d.serial.name, "source": "usb_ttl_discovery"}
	}
	return map[string]any{"device_id": d.device.ID, "interface_name": d.hotspot.name, "source": "hotspot_discovery"}
}

func (s ircStatus) hotspotStatus() bool {
	if s.Protocol != "0.3" || s.Kind != "autech-ircesp32" || s.LinkID == 0 {
		return false
	}
	return s.validCameraState()
}

func (s ircStatus) validCameraState() bool {
	switch s.CameraState {
	case "not_initialized", "pins_unverified", "invalid_pins", "psram_required",
		"init_failed", "unexpected_sensor", "ready", "capture_failed", "no_fresh_frame":
		return true
	}
	return false
}

// Discovery is explicit and only probes the DHCP server of an eligible,
// connected Wi-Fi interface. It never enqueues a photo or an input.
func (r *Runtime) ircDiscover(interfaceName string,
	hotspots func() ([]ircHotspot, error), clientFor func(ircHotspot) (*http.Client, error),
) (any, error) {
	r.ircMu.Lock()
	defer r.ircMu.Unlock()
	r.ircDetected = nil
	candidates, e := hotspots()
	if e != nil {
		return nil, e
	}
	var detected *ircDetectedDevice
	var status ircStatus
	for _, candidate := range candidates {
		if interfaceName != "" && interfaceName != candidate.name {
			continue
		}
		client, e := clientFor(candidate)
		if e != nil {
			if interfaceName != "" {
				return nil, e
			}
			continue
		}
		client.Timeout = 2 * time.Second
		device := ircDevice{Endpoint: "http://" + candidate.server.String()}
		found, e := ircGetStatus(client, device, "/v1/discover")
		client.CloseIdleConnections()
		if e != nil || !found.hotspotStatus() {
			continue
		}
		if detected != nil {
			return nil, errors.New("multiple IRCESP32 hotspots detected; select the Windows interface by name")
		}
		status = found
		device.ID, device.linkID, device.bootID = found.DeviceID, found.LinkID, found.BootID
		detected = &ircDetectedDevice{device: device, hotspot: candidate}
	}
	if detected == nil {
		return nil, errors.New("IRCESP32 not detected on eligible Wi-Fi; check the ESP32 hotspot, DHCP and interface routing")
	}
	r.ircDetected = detected
	return struct {
		ircStatus
		InterfaceName string `json:"interface_name"`
		Source        string `json:"source"`
	}{status, detected.hotspot.name, "hotspot_discovery"}, nil
}
