package main

import (
	"context"
	"crypto/sha256"
	"crypto/tls"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/http"
	"net/netip"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

func (r *Runtime) deviceName() string {
	var v struct {
		Name string `json:"device_name"`
	}
	b, e := os.ReadFile(filepath.Join(r.home, "Config", "installation.json"))
	if e == nil && json.Unmarshal(b, &v) == nil && v.Name != "" {
		return v.Name
	}
	name, _ := os.Hostname()
	return name
}
func privateIPv4() []netip.Prefix {
	var out []netip.Prefix
	interfaces, _ := net.Interfaces()
	for _, nic := range interfaces {
		if nic.Flags&net.FlagUp == 0 || nic.Flags&net.FlagLoopback != 0 {
			continue
		}
		addresses, _ := nic.Addrs()
		for _, address := range addresses {
			p, e := netip.ParsePrefix(address.String())
			if e == nil && p.Addr().Is4() && p.Addr().IsPrivate() {
				out = append(out, p)
			}
		}
	}
	return out
}
func listenerAddresses(address string) []string {
	host, port, _ := net.SplitHostPort(address)
	if host != "auto" {
		return []string{address}
	}
	out := []string{net.JoinHostPort("127.0.0.1", port)}
	for _, p := range privateIPv4() {
		out = append(out, net.JoinHostPort(p.Addr().String(), port))
	}
	return out
}
func discoveryIPs(query string) ([]string, error) {
	var prefixes []netip.Prefix
	if query != "" {
		p, e := netip.ParsePrefix(query)
		if e != nil {
			return nil, errors.New("query must be a private IPv4 CIDR (maximum /24)")
		}
		prefixes = []netip.Prefix{p}
	} else {
		for _, p := range privateIPv4() {
			if p.Bits() < 24 {
				p = netip.PrefixFrom(p.Addr(), 24)
			}
			prefixes = append(prefixes, p)
		}
	}
	ips := map[string]bool{}
	for _, p := range prefixes {
		if !p.Addr().Is4() || !p.Addr().IsPrivate() || p.Bits() < 24 {
			return nil, errors.New("bounded discovery requires a private IPv4 /24 or narrower")
		}
		p = p.Masked()
		for ip := p.Addr(); p.Contains(ip); ip = ip.Next() {
			if len(ips) >= 1024 {
				return nil, errors.New("too many attached networks; select one CIDR")
			}
			ips[ip.String()] = true
		}
	}
	out := make([]string, 0, len(ips))
	for ip := range ips {
		out = append(out, ip)
	}
	sort.Strings(out)
	return out, nil
}
func (r *Runtime) discoverDevices(p runtimeRequest) (any, error) {
	ips, e := discoveryIPs(p.Query)
	if e != nil {
		return nil, e
	}
	port := p.Port
	if port == 0 {
		port = 8766
	}
	if port < 1024 || port > 65535 {
		return nil, errors.New("invalid peer port")
	}
	type found struct {
		MachineID string `json:"machine_id"`
		Name      string `json:"name"`
		Version   string `json:"version"`
		Endpoint  string `json:"endpoint"`
		Pin       string `json:"certificate_sha256"`
		Trusted   bool   `json:"trusted"`
	}
	var results []found
	var mu sync.Mutex
	var wg sync.WaitGroup
	slots := make(chan struct{}, 32)
	peers, _, e := readPeers(r.home)
	if e != nil {
		return nil, e
	}
	for _, ip := range ips {
		wg.Add(1)
		go func(ip string) {
			defer wg.Done()
			slots <- struct{}{}
			defer func() { <-slots }()
			ctx, cancel := context.WithTimeout(context.Background(), 700*time.Millisecond)
			defer cancel()
			// Discovery sends NO credentials or executable commands. Its output is
			// untrusted until paired against a private enrollment file/certificate pin.
			tr := &http.Transport{TLSClientConfig: &tls.Config{MinVersion: tls.VersionTLS12, InsecureSkipVerify: true}, Proxy: nil}
			defer tr.CloseIdleConnections()
			client := &http.Client{Transport: tr, CheckRedirect: func(*http.Request, []*http.Request) error { return errors.New("redirect refused") }}
			endpoint := "https://" + net.JoinHostPort(ip, strconv.Itoa(port)) + "/gateway"
			req, _ := http.NewRequestWithContext(ctx, "GET", endpoint+"/identity", nil)
			response, e := client.Do(req)
			if e != nil {
				return
			}
			defer response.Body.Close()
			if response.StatusCode != 200 || response.TLS == nil || len(response.TLS.PeerCertificates) == 0 {
				return
			}
			var v found
			if json.NewDecoder(io.LimitReader(response.Body, 4096)).Decode(&v) != nil || !requestIDPattern.MatchString(v.MachineID) || len(v.Name) > 160 {
				return
			}
			sum := sha256.Sum256(response.TLS.PeerCertificates[0].Raw)
			v.Pin = hex.EncodeToString(sum[:])
			v.Endpoint = endpoint
			for _, peer := range peers {
				if peer.MachineID == v.MachineID && strings.EqualFold(peer.CertificateSHA256, v.Pin) && peer.Status != "retired" {
					v.Trusted = true
				}
			}
			mu.Lock()
			results = append(results, v)
			mu.Unlock()
		}(ip)
	}
	wg.Wait()
	sort.Slice(results, func(i, j int) bool { return results[i].Endpoint < results[j].Endpoint })
	return map[string]any{"devices": results, "probed_addresses": len(ips), "port": port, "checked_at": utcNow(), "automatic_pairing": false, "note": "Discovery alone never authorizes control. Automatic discovery scans at most the local /24 of each attached private interface; supply a CIDR for another bounded range."}, nil
}
