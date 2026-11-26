package main

import (
	"errors"
	"fmt"
	"os"
	"strings"
	"syscall"
	"time"
	"unsafe"
)

var (
	ircSetupAPI          = syscall.NewLazyDLL("setupapi.dll")
	ircGetSerialDevices  = ircSetupAPI.NewProc("SetupDiGetClassDevsW")
	ircEnumSerialDevices = ircSetupAPI.NewProc("SetupDiEnumDeviceInfo")
	ircSerialInstanceID  = ircSetupAPI.NewProc("SetupDiGetDeviceInstanceIdW")
	ircSerialOpenRegKey  = ircSetupAPI.NewProc("SetupDiOpenDevRegKey")
	ircDestroySerialSet  = ircSetupAPI.NewProc("SetupDiDestroyDeviceInfoList")
	ircKernel32          = syscall.NewLazyDLL("kernel32.dll")
	ircSetCommState      = ircKernel32.NewProc("SetCommState")
	ircSetCommTimeouts   = ircKernel32.NewProc("SetCommTimeouts")
	ircSetupComm         = ircKernel32.NewProc("SetupComm")
	ircPurgeComm         = ircKernel32.NewProc("PurgeComm")
)

// Ports device setup class and COM-port device interface class.
var ircPortsClass = syscall.GUID{Data1: 0x4d36e978, Data2: 0xe325, Data3: 0x11ce, Data4: [8]byte{0xbf, 0xc1, 0x08, 0x00, 0x2b, 0xe1, 0x03, 0x18}}
var ircCOMInterface = syscall.GUID{Data1: 0x86e0d1e0, Data2: 0x8089, Data3: 0x11d0, Data4: [8]byte{0x9c, 0xe4, 0x08, 0x00, 0x3e, 0x30, 0x1f, 0x73}}

type ircSerialDeviceInfo struct {
	size     uint32
	class    syscall.GUID
	instance uint32
	reserved uintptr
}

func ircSerialPorts() ([]ircSerialPort, error) {
	// Some USB-UART drivers register only the Ports setup class.
	ports, err := ircEnumerateUSBSerial(&ircPortsClass, 0)
	if err != nil {
		return nil, err
	}
	interfaces, err := ircEnumerateUSBSerial(&ircCOMInterface, 0x10) // DIGCF_DEVICEINTERFACE
	if err != nil {
		return nil, err
	}
	for _, port := range interfaces {
		duplicate := false
		for _, known := range ports {
			if port == known {
				duplicate = true
				break
			}
		}
		if !duplicate {
			ports = append(ports, port)
		}
	}
	return ports, nil
}

func ircEnumerateUSBSerial(class *syscall.GUID, flags uintptr) ([]ircSerialPort, error) {
	const digcfPresent = 2
	const errorNoMoreItems = syscall.Errno(259)
	set, _, err := ircGetSerialDevices.Call(uintptr(unsafe.Pointer(class)), 0, 0, flags|digcfPresent)
	if set == uintptr(syscall.InvalidHandle) {
		return nil, fmt.Errorf("IRCESP32 cannot enumerate Windows COM ports: %w", err)
	}
	defer ircDestroySerialSet.Call(set)
	var ports []ircSerialPort
	for i := uintptr(0); i < 1024; i++ {
		var info ircSerialDeviceInfo
		info.size = uint32(unsafe.Sizeof(info))
		ok, _, err := ircEnumSerialDevices.Call(set, i, uintptr(unsafe.Pointer(&info)))
		if ok == 0 {
			if errors.Is(err, errorNoMoreItems) {
				return ports, nil
			}
			return nil, fmt.Errorf("IRCESP32 cannot read Windows COM inventory: %w", err)
		}
		var buf [256]uint16
		ok, _, _ = ircSerialInstanceID.Call(set, uintptr(unsafe.Pointer(&info)), uintptr(unsafe.Pointer(&buf[0])), uintptr(len(buf)), 0)
		if ok == 0 {
			continue
		}
		instance := strings.ToUpper(syscall.UTF16ToString(buf[:]))
		if (!strings.HasPrefix(instance, `USB\VID_`) && !strings.HasPrefix(instance, `FTDIBUS\VID_`)) ||
			strings.HasPrefix(instance, `USB\VID_303A&`) { // ESP32 native USB is not the external USB-TTL bridge.
			continue
		}
		name := ircSerialPortName(set, &info)
		if ircCOMName.MatchString(name) {
			ports = append(ports, ircSerialPort{name: name, instance: instance})
		}
	}
	return nil, errors.New("Windows COM inventory exceeded the supported size")
}

func ircSerialPortName(set uintptr, info *ircSerialDeviceInfo) string {
	const dicsFlagGlobal, diregDev = 1, 1
	key, _, _ := ircSerialOpenRegKey.Call(set, uintptr(unsafe.Pointer(info)), dicsFlagGlobal, 0, diregDev, syscall.KEY_QUERY_VALUE)
	if key == uintptr(syscall.InvalidHandle) {
		return ""
	}
	defer syscall.RegCloseKey(syscall.Handle(key))
	value, _ := syscall.UTF16PtrFromString("PortName")
	var text [16]uint16
	size, kind := uint32(unsafe.Sizeof(text)), uint32(0)
	if err := syscall.RegQueryValueEx(syscall.Handle(key), value, nil, &kind, (*byte)(unsafe.Pointer(&text[0])), &size); err != nil || kind != syscall.REG_SZ {
		return ""
	}
	return strings.ToUpper(syscall.UTF16ToString(text[:]))
}

// Win32 DCB and COMMTIMEOUTS. fBinary is the only set flag: no flow control
// and DTR/RTS disabled so ordinary access does not request bootloader mode.
type ircDCB struct {
	length, baud, flags        uint32
	reserved, xonLim, xoffLim  uint16
	dataBits, parity, stop     byte
	xon, xoff, err, eof, event byte
	reserved1                  uint16
}

type ircCommTimeouts struct {
	readInterval, readMultiplier, readConstant, writeMultiplier, writeConstant uint32
}

type ircWindowsSerial struct {
	handle   syscall.Handle
	deadline time.Time
}

func ircOpenSerial(port ircSerialPort) (ircSerialIO, error) {
	ports, err := ircSerialPorts()
	if err != nil {
		return nil, err
	}
	matches := 0
	for _, current := range ports {
		if current == port {
			matches++
		} else if current.name == port.name {
			return nil, errors.New("IRCESP32 USB port identity changed or is ambiguous; discover again")
		}
	}
	if matches != 1 {
		return nil, errors.New("IRCESP32 USB port disconnected; discover again")
	}
	path, _ := syscall.UTF16PtrFromString(`\\.\` + port.name)
	handle, err := syscall.CreateFile(path, syscall.GENERIC_READ|syscall.GENERIC_WRITE, 0, nil, syscall.OPEN_EXISTING, 0, 0)
	if err != nil {
		return nil, fmt.Errorf("IRCESP32 cannot open %s; close Arduino Serial Monitor or other programs using it: %w", port.name, err)
	}
	err = ircConfigureSerial(handle)
	if err != nil {
		syscall.CloseHandle(handle)
		return nil, fmt.Errorf("IRCESP32 cannot configure %s at %d baud: %w", port.name, ircSerialBaud, err)
	}
	return &ircWindowsSerial{handle: handle}, nil
}

func ircConfigureSerial(h syscall.Handle) error {
	if ok, _, err := ircSetupComm.Call(uintptr(h), 65536, 4096); ok == 0 {
		return err
	}
	dcb := ircDCB{length: uint32(unsafe.Sizeof(ircDCB{})), baud: ircSerialBaud, flags: 1, dataBits: 8, xon: 17, xoff: 19}
	if ok, _, err := ircSetCommState.Call(uintptr(h), uintptr(unsafe.Pointer(&dcb))); ok == 0 {
		return err
	}
	const maxDWORD = ^uint32(0)
	// Return buffered bytes immediately, or wait at most 75 ms for the first.
	timeouts := ircCommTimeouts{readInterval: maxDWORD, readMultiplier: maxDWORD, readConstant: 75, writeConstant: 500}
	if ok, _, err := ircSetCommTimeouts.Call(uintptr(h), uintptr(unsafe.Pointer(&timeouts))); ok == 0 {
		return err
	}
	const purgeTXClear, purgeRXClear = 4, 8
	if ok, _, err := ircPurgeComm.Call(uintptr(h), purgeTXClear|purgeRXClear); ok == 0 {
		return err
	}
	return nil
}

func (s *ircWindowsSerial) setDeadline(t time.Time) { s.deadline = t }
func (s *ircWindowsSerial) Close() error            { return syscall.CloseHandle(s.handle) }

func (s *ircWindowsSerial) Read(p []byte) (int, error) {
	if len(p) == 0 {
		return 0, nil
	}
	for time.Now().Before(s.deadline) {
		var n uint32
		err := syscall.ReadFile(s.handle, p, &n, nil)
		if n != 0 || err != nil {
			return int(n), err
		}
		// A driver may return immediately when there is no data.
		time.Sleep(time.Millisecond)
	}
	return 0, os.ErrDeadlineExceeded
}

func (s *ircWindowsSerial) Write(p []byte) (int, error) {
	var n uint32
	err := syscall.WriteFile(s.handle, p, &n, nil)
	return int(n), err
}
