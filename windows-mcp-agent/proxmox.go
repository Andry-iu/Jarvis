package main

import (
    "context"
    "crypto/sha256"
    "crypto/subtle"
    "crypto/tls"
    "encoding/hex"
    "encoding/json"
    "errors"
    "fmt"
    "io"
    "net/http"
    "net/url"
    "os"
    "regexp"
    "strconv"
    "strings"
    "time"
)

// Credentials remain solely on the Windows host. MCP callers cannot provide
// endpoints, token IDs, secrets, certificate pins, or arbitrary API paths.
var proxmoxNodePattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,62}$`)

func proxmoxOperation(p runtimeRequest) (string, string, url.Values, error) {
    if !proxmoxNodePattern.MatchString(p.Node) {
        return "", "", nil, errors.New("invalid Proxmox node")
    }
    root := "/api2/json/nodes/" + url.PathEscape(p.Node)
    switch p.Operation {
    case "proxmox_status":
        return http.MethodGet, root + "/status", nil, nil
    case "proxmox_vm_list":
        return http.MethodGet, root + "/qemu", nil, nil
    case "proxmox_storage_list":
        return http.MethodGet, root + "/storage", nil, nil
    case "proxmox_shutdown":
        return http.MethodPost, root + "/status", url.Values{"command": {"shutdown"}}, nil
    case "proxmox_vm_action":
        if p.VMID < 100 || p.VMID > 999999999 {
            return "", "", nil, errors.New("invalid VMID")
        }
        if p.Command != "start" && p.Command != "shutdown" {
            return "", "", nil, errors.New("only start and graceful shutdown are allowed")
        }
        return http.MethodPost, root + "/qemu/" + strconv.Itoa(p.VMID) + "/status/" + p.Command, url.Values{}, nil
    default:
        return "", "", nil, errors.New("unsupported Proxmox operation")
    }
}

func proxmoxClient() (*http.Client, string, string, error) {
    raw := strings.TrimSpace(os.Getenv("JARVIS_PROXMOX_URL"))
    id := os.Getenv("JARVIS_PROXMOX_API_TOKEN_ID")
    secret := os.Getenv("JARVIS_PROXMOX_API_TOKEN_SECRET")
    pin := strings.ToLower(strings.ReplaceAll(strings.TrimSpace(os.Getenv("JARVIS_PROXMOX_CERT_SHA256")), ":", ""))
    if raw == "" || id == "" || secret == "" || len(pin) != 64 {
        return nil, "", "", errors.New("Proxmox connection not configured; set HTTPS URL, token and certificate fingerprint on the agent")
    }
    expected, err := hex.DecodeString(pin)
    if err != nil || len(expected) != sha256.Size {
        return nil, "", "", errors.New("invalid Proxmox certificate fingerprint")
    }
    if strings.ContainsAny(id, "\r\n") || strings.ContainsAny(secret, "\r\n") {
        return nil, "", "", errors.New("invalid Proxmox token")
    }
    u, err := url.Parse(raw)
    if err != nil || u.Scheme != "https" || u.Host == "" || u.User != nil || u.RawQuery != "" || u.Fragment != "" || (u.Path != "" && u.Path != "/") {
        return nil, "", "", errors.New("invalid Proxmox URL; HTTPS origin only")
    }
    origin := "https://" + u.Host
    transport := &http.Transport{
        Proxy: nil,
        TLSClientConfig: &tls.Config{
            MinVersion: tls.VersionTLS12,
            // A private self-signed Proxmox certificate is accepted only when
            // the exact SHA-256 DER leaf pin provided by the administrator matches.
            InsecureSkipVerify: true, // #nosec G402 - replaced with mandatory fingerprint verification
            VerifyConnection: func(cs tls.ConnectionState) error {
                if len(cs.PeerCertificates) == 0 {
                    return errors.New("Proxmox TLS certificate missing")
                }
                leaf := cs.PeerCertificates[0]
                actual := sha256.Sum256(leaf.Raw)
                if subtle.ConstantTimeCompare(actual[:], expected) != 1 {
                    return errors.New("Proxmox certificate fingerprint mismatch")
                }
                now := time.Now()
                if now.Before(leaf.NotBefore) || now.After(leaf.NotAfter) {
                    return errors.New("Proxmox certificate expired or not yet valid")
                }
                return nil
            },
        },
        DisableKeepAlives: true,
    }
    return &http.Client{
        Timeout: 12 * time.Second,
        Transport: transport,
        CheckRedirect: func(_ *http.Request, _ []*http.Request) error {
            return errors.New("Proxmox redirect refused")
        },
    }, origin, "PVEAPIToken=" + id + "=" + secret, nil
}

func callProxmox(p runtimeRequest) (any, error) {
    method, path, form, err := proxmoxOperation(p)
    if err != nil { return nil, err }
    client, base, token, err := proxmoxClient()
    if err != nil { return nil, err }
    defer client.CloseIdleConnections()

    var body io.Reader
    if method == http.MethodPost { body = strings.NewReader(form.Encode()) }
    ctx, cancel := context.WithTimeout(context.Background(), 12*time.Second)
    defer cancel()
    req, err := http.NewRequestWithContext(ctx, method, base + path, body)
    if err != nil { return nil, errors.New("Proxmox request initialization failed") }
    req.Header.Set("Authorization", token)
    if method == http.MethodPost { req.Header.Set("Content-Type", "application/x-www-form-urlencoded") }
    resp, err := client.Do(req)
    if err != nil {
        // Never return URL, HTTP request, or raw network error: those may contain
        // an internal host, token, or potentially sensitive diagnostic.
        return nil, errors.New("Proxmox connection or certificate verification failed; check agent configuration and node availability")
    }
    defer resp.Body.Close()
    if resp.StatusCode < 200 || resp.StatusCode >= 300 {
        return nil, fmt.Errorf("Proxmox API returned HTTP %d", resp.StatusCode)
    }
    raw, err := io.ReadAll(io.LimitReader(resp.Body, 131073))
    if err != nil || len(raw) > 131072 {
        return nil, errors.New("Proxmox response too large or unreadable")
    }
    var doc struct { Data json.RawMessage `json:"data"` }
    if err := json.Unmarshal(raw, &doc); err != nil || len(doc.Data) == 0 {
        return nil, errors.New("invalid Proxmox response")
    }
    if method == http.MethodPost {
        var task any
        if err := json.Unmarshal(doc.Data, &task); err != nil { return nil, errors.New("invalid Proxmox task result") }
        return map[string]any{"dispatched": true, "verified": false, "node": p.Node, "operation": p.Operation, "vmid": p.VMID, "task": task}, nil
    }
    var result any
    if err := json.Unmarshal(doc.Data, &result); err != nil { return nil, errors.New("invalid Proxmox data") }
    return map[string]any{"node": p.Node, "operation": p.Operation, "data": result}, nil
}
