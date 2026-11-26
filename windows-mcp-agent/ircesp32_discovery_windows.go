//go:build windows && (amd64 || arm64)

package main

import (
	"context"
	"encoding/binary"
	"errors"
	"math/bits"
	"net"
	"net/http"
	"net/netip"
	"runtime"
	"syscall"
	"time"
	"unsafe"
)

const (
	ircWiFiType       = 71   // IF_TYPE_IEEE80211
	ircOperUp         = 1    // IfOperStatusUp
	ircDHCPEnabled    = 4    // IP_ADAPTER_DHCP_ENABLED
	ircIncludeGateway = 0x80 // GAA_FLAG_INCLUDE_GATEWAYS
	ircIPUnicastIF    = 31   // IP_UNICAST_IF
)

var ircIPHelper = syscall.NewLazyDLL("iphlpapi.dll")
var ircGetAdapters = ircIPHelper.NewProc("GetAdaptersAddresses")
var ircGetBestInterface = ircIPHelper.NewProc("GetBestInterface")

// Prefix of IP_ADAPTER_ADDRESSES_LH, through Dhcpv4Server, on Windows x64/ARM64.
type ircWindowsAdapter struct {
	length, index          uint32
	next                   *ircWindowsAdapter
	namesAndAddresses      [8]uintptr
	physicalAddress        [8]byte
	physicalLength         uint32
	flags, mtu, ifType     uint32
	operStatus, ipv6Idx    uint32
	zoneIndices            [16]uint32
	firstPrefix            uintptr
	txSpeed, rxSpeed       uint64
	firstWINS              uintptr
	firstGateway           uintptr
	ipv4Metric, ipv6Metric uint32
	luid                   uint64
	dhcpAddress            *syscall.RawSockaddrInet4
	dhcpAddressLength      int32
}

func ircHotspots() ([]ircHotspot, error) {
	size := uint32(15000)
	for attempt := 0; attempt < 3 && size <= 1024*1024; attempt++ {
		buf := make([]byte, size)
		rc, _, _ := ircGetAdapters.Call(syscall.AF_INET, ircIncludeGateway, 0,
			uintptr(unsafe.Pointer(&buf[0])), uintptr(unsafe.Pointer(&size)))
		if rc == uintptr(syscall.ERROR_BUFFER_OVERFLOW) {
			continue
		}
		if rc == 232 { // ERROR_NO_DATA
			return nil, nil
		}
		if rc != 0 {
			return nil, syscall.Errno(rc)
		}
		var candidates []ircHotspot
		for a := (*ircWindowsAdapter)(unsafe.Pointer(&buf[0])); a != nil; a = a.next {
			if uintptr(a.length) < unsafe.Sizeof(*a) || a.ifType != ircWiFiType ||
				a.operStatus != ircOperUp || a.flags&ircDHCPEnabled == 0 ||
				a.firstGateway != 0 || a.index == 0 || a.dhcpAddress == nil ||
				a.dhcpAddressLength < int32(unsafe.Sizeof(syscall.RawSockaddrInet4{})) ||
				a.dhcpAddress.Family != syscall.AF_INET {
				continue
			}
			server := netip.AddrFrom4(a.dhcpAddress.Addr)
			if !server.IsPrivate() {
				continue
			}
			iface, err := net.InterfaceByIndex(int(a.index))
			if err != nil {
				continue
			}
			addrs, err := iface.Addrs()
			if err != nil {
				continue
			}
			candidate := ircHotspot{index: a.index, name: iface.Name, server: server}
			for _, addr := range addrs {
				prefix, err := netip.ParsePrefix(addr.String())
				if err == nil && prefix.Addr().Is4() && prefix.Addr() != server && prefix.Contains(server) {
					// Do not guess the source on an interface with overlapping leases.
					if candidate.local.IsValid() {
						candidate.local = netip.Addr{}
						break
					}
					candidate.local = prefix.Addr()
				}
			}
			if candidate.local.IsValid() {
				candidates = append(candidates, candidate)
			}
		}
		runtime.KeepAlive(buf)
		return candidates, nil
	}
	return nil, errors.New("Windows adapter inventory exceeded the supported size")
}

func ircHotspotClient(h ircHotspot) (*http.Client, error) {
	client := ircHTTPClient()
	transport := client.Transport.(*http.Transport)
	transport.DialContext = func(ctx context.Context, _, address string) (net.Conn, error) {
		if address != net.JoinHostPort(h.server.String(), "80") {
			return nil, errors.New("IRCESP32 request outside the detected hotspot")
		}
		// Reject a changed adapter or DHCP lease before connecting.
		current, err := ircHotspots()
		if err != nil {
			return nil, err
		}
		matched := false
		for _, c := range current {
			if c == h {
				matched = true
				break
			}
		}
		var best uint32
		ip := h.server.As4()
		rc, _, _ := ircGetBestInterface.Call(uintptr(binary.LittleEndian.Uint32(ip[:])),
			uintptr(unsafe.Pointer(&best)))
		if !matched || rc != 0 || best != h.index {
			return nil, errors.New("IRCESP32 hotspot route unavailable or ambiguous; discover again")
		}
		dialer := net.Dialer{
			Timeout:   3 * time.Second,
			LocalAddr: &net.TCPAddr{IP: net.IP(h.local.AsSlice())},
			Control: func(_, _ string, conn syscall.RawConn) error {
				var optErr error
				if err := conn.Control(func(fd uintptr) {
					optErr = syscall.SetsockoptInt(syscall.Handle(fd), syscall.IPPROTO_IP,
						ircIPUnicastIF, int(bits.ReverseBytes32(h.index)))
				}); err != nil {
					return err
				}
				return optErr
			},
		}
		return dialer.DialContext(ctx, "tcp4", address)
	}
	return client, nil
}
