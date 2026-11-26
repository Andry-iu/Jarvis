package main

import (
	"strings"
	"testing"
	"unsafe"
)

func TestIRCESP32WindowsUSBInventory(t *testing.T) {
	if unsafe.Sizeof(ircDCB{}) != 28 || unsafe.Sizeof(ircCommTimeouts{}) != 20 {
		t.Fatal("unexpected Win32 serial structure layout")
	}
	ports, err := ircSerialPorts()
	if err != nil {
		t.Fatal(err)
	}
	for _, p := range ports {
		if !ircCOMName.MatchString(p.name) ||
			(!strings.HasPrefix(p.instance, `USB\VID_`) && !strings.HasPrefix(p.instance, `FTDIBUS\VID_`)) {
			t.Fatalf("unexpected COM inventory item: %+v", p)
		}
	}
}
