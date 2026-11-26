package main

import (
	"bufio"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"regexp"
	"strconv"
	"strings"
	"time"
)

const ircSerialBaud = 921600

var ircCOMName = regexp.MustCompile(`^COM[1-9][0-9]{0,4}$`)

type ircSerialPort struct {
	name     string
	instance string
}

type ircSerialIO interface {
	io.ReadWriteCloser
	setDeadline(time.Time)
}

type ircSerialClient struct {
	port ircSerialIO
	read *bufio.Reader
}

func newIRCSerialClient(p ircSerialIO) *ircSerialClient {
	return &ircSerialClient{p, bufio.NewReader(p)}
}

func (c *ircSerialClient) exchange(request string, timeout time.Duration) (string, error) {
	var random [8]byte
	if _, err := rand.Read(random[:]); err != nil {
		return "", err
	}
	prefix := "IRC4 " + hex.EncodeToString(random[:]) + " "
	c.port.setDeadline(time.Now().Add(timeout))
	out := "\n" + prefix + request + "\n"
	if n, err := io.WriteString(c.port, out); err != nil || n != len(out) {
		return "", errors.New("IRCESP32 USB write unavailable or outcome uncertain; do not replay")
	}
	for budget := 8192; budget > 0; {
		line, err := c.read.ReadSlice('\n')
		if err != nil {
			return "", errors.New("IRCESP32 USB reply unavailable or malformed; do not replay")
		}
		budget -= len(line)
		reply := strings.TrimRight(string(line), "\r\n")
		if body, match := strings.CutPrefix(reply, prefix); match && body != request {
			return body, nil
		}
	}
	return "", errors.New("IRCESP32 USB reply not identified")
}

func (c *ircSerialClient) status() (ircStatus, error) {
	reply, err := c.exchange("STATUS", 900*time.Millisecond)
	if err != nil {
		return ircStatus{}, err
	}
	payload, ok := strings.CutPrefix(reply, "STATUS ")
	s, err := ircParseStatus([]byte(payload))
	if !ok || err != nil || s.Protocol != "0.4" || s.Kind != "autech-ircesp32" ||
		s.BootID == "00000000" || !s.validCameraState() {
		return ircStatus{}, errors.New("IRCESP32 USB status unavailable or unsupported firmware")
	}
	return s, nil
}

func (c *ircSerialClient) command(s ircStatus, seq uint32, text string) error {
	reply, err := c.exchange(fmt.Sprintf("COMMAND %s %d %s", s.BootID, seq, text), 1500*time.Millisecond)
	if err != nil {
		return err
	}
	if !strings.HasPrefix(reply, fmt.Sprintf("REPLY OK %d ", seq)) {
		return errors.New("IRCESP32 USB command refused or result uncertain; do not replay")
	}
	return nil
}

func (c *ircSerialClient) capture(d ircDevice, s ircStatus, seq uint32) (string, map[string]any, error) {
	requested := time.Now().UTC().Format(time.RFC3339Nano)
	reply, err := c.exchange(fmt.Sprintf("CAPTURE %s %d", s.BootID, seq), 20*time.Second)
	if err != nil {
		return "", nil, err
	}
	fields := strings.Split(reply, " ")
	if len(fields) != 10 || fields[0] != "JPEG" {
		return "", nil, errors.New("IRCESP32 USB photo unavailable; check camera status; do not replay")
	}
	size, err := strconv.Atoi(fields[4])
	if err != nil || size < 4 || size > ircMaxJPEG || fields[1] != d.ID ||
		fields[2] != s.BootID || fields[3] != strconv.FormatUint(uint64(seq), 10) {
		return "", nil, errors.New("IRCESP32 invalid USB photo envelope; do not replay")
	}
	jpeg := make([]byte, size)
	if _, err := io.ReadFull(c.read, jpeg); err != nil {
		return "", nil, errors.New("IRCESP32 incomplete USB JPEG; do not replay")
	}
	return ircValidatePhoto(jpeg, d, s, seq, requested, ircPhotoHeader{
		fields[1], fields[2], fields[3], fields[9], fields[5], fields[6], fields[7], fields[8],
	})
}

func (r *Runtime) ircDiscoverTransport(p runtimeRequest) (any, error) {
	switch p.Transport {
	case "", "wifi":
		if p.SerialPort == "" {
			return r.ircDiscover(p.InterfaceName, ircHotspots, ircHotspotClient)
		}
		if p.Transport == "wifi" {
			return nil, errors.New("serial_port applies only to USB discovery")
		}
	case "usb":
	default:
		return nil, errors.New("IRCESP32 transport must be usb or wifi")
	}
	if p.InterfaceName != "" {
		return nil, errors.New("interface_name applies only to Wi-Fi discovery")
	}
	return r.ircDiscoverSerial(p.SerialPort, ircSerialPorts, ircOpenSerial)
}

func (r *Runtime) ircDiscoverSerial(name string, ports func() ([]ircSerialPort, error), open func(ircSerialPort) (ircSerialIO, error)) (any, error) {
	name = strings.ToUpper(name)
	if name != "" && !ircCOMName.MatchString(name) {
		return nil, errors.New("IRCESP32 serial_port must be a Windows COM name, for example COM7")
	}
	r.ircMu.Lock()
	defer r.ircMu.Unlock()
	r.ircDetected = nil
	candidates, err := ports()
	if err != nil {
		return nil, err
	}
	if name == "" && len(candidates) > 16 {
		return nil, errors.New("too many USB serial ports; select the exact serial_port")
	}
	var detected *ircDetectedDevice
	var status ircStatus
	for _, candidate := range candidates {
		if name != "" && name != candidate.name {
			continue
		}
		port, err := open(candidate)
		if err != nil {
			if name != "" {
				return nil, err
			}
			continue
		}
		found, err := newIRCSerialClient(port).status()
		port.Close()
		if err != nil {
			continue
		}
		if detected != nil {
			return nil, errors.New("multiple IRCESP32 devices detected over USB; select the exact serial_port")
		}
		status = found
		detected = &ircDetectedDevice{
			device: ircDevice{ID: found.DeviceID, bootID: found.BootID},
			serial: &candidate,
		}
	}
	if detected == nil {
		return nil, errors.New("IRCESP32 not detected over USB; check the TTL data cable, Windows COM driver and firmware 0.4, and close Arduino Serial Monitor")
	}
	r.ircDetected = detected
	return struct {
		ircStatus
		SerialPort string `json:"serial_port"`
		Source     string `json:"source"`
	}{status, detected.serial.name, "usb_ttl_discovery"}, nil
}
