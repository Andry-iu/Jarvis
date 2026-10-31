package main

import (
	"context"
	"crypto"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

// AccessVerifier checks JWT signatures and registered application audience. It
// must not trust client-provided email or headers without signature validation.
type AccessVerifier struct {
	TeamDomain string
	Audience   string
	Client     *http.Client
	mu         sync.Mutex
	keys       map[string]*rsa.PublicKey
	fetched    time.Time
}

type accessJWKS struct {
	Keys []struct {
		Kty string `json:"kty"`
		Kid string `json:"kid"`
		Alg string `json:"alg"`
		N   string `json:"n"`
		E   string `json:"e"`
	} `json:"keys"`
}

func (v *AccessVerifier) loadKeys(ctx context.Context) (map[string]*rsa.PublicKey, error) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.keys != nil && time.Since(v.fetched) < 10*time.Minute {
		return v.keys, nil
	}
	u, e := url.Parse(v.TeamDomain)
	if e != nil || u.Scheme != "https" || u.Hostname() == "" || u.User != nil || u.RawQuery != "" || u.Fragment != "" || u.Path != "" {
		return nil, errors.New("invalid team domain: use https://<team>.cloudflareaccess.com")
	}
	if !strings.HasSuffix(u.Hostname(), ".cloudflareaccess.com") {
		return nil, errors.New("team domain must end in .cloudflareaccess.com")
	}
	req, e := http.NewRequestWithContext(ctx, "GET", v.TeamDomain+"/cdn-cgi/access/certs", nil)
	if e != nil {
		return nil, e
	}
	client := v.Client
	if client == nil {
		client = &http.Client{Timeout: 5 * time.Second}
	}
	resp, e := client.Do(req)
	if e != nil {
		return nil, e
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("Access JWKS HTTP %d", resp.StatusCode)
	}
	var jwks accessJWKS
	if e = json.NewDecoder(http.MaxBytesReader(nil, resp.Body, 256*1024)).Decode(&jwks); e != nil {
		return nil, e
	}
	keys := make(map[string]*rsa.PublicKey)
	for _, k := range jwks.Keys {
		if k.Kty != "RSA" || (k.Alg != "" && k.Alg != "RS256") || k.Kid == "" {
			continue
		}
		nb, e1 := base64.RawURLEncoding.DecodeString(k.N)
		eb, e2 := base64.RawURLEncoding.DecodeString(k.E)
		if e1 != nil || e2 != nil || len(nb) < 256 || len(eb) == 0 || len(eb) > 4 {
			continue
		}
		n := new(big.Int).SetBytes(nb)
		exp := new(big.Int).SetBytes(eb).Int64()
		if exp < 3 || exp > 2147483647 || exp%2 == 0 {
			continue
		}
		keys[k.Kid] = &rsa.PublicKey{N: n, E: int(exp)}
	}
	if len(keys) == 0 {
		return nil, errors.New("Access JWKS has no usable signing keys")
	}
	v.keys = keys
	v.fetched = time.Now()
	return keys, nil
}
func (v *AccessVerifier) Verify(ctx context.Context, jwt string) error {
	if len(jwt) > 16384 {
		return errors.New("JWT too long")
	}
	parts := strings.Split(jwt, ".")
	if len(parts) != 3 {
		return errors.New("invalid JWT")
	}
	var h struct {
		Alg string `json:"alg"`
		Kid string `json:"kid"`
		Typ string `json:"typ"`
	}
	hb, e := base64.RawURLEncoding.DecodeString(parts[0])
	if e != nil || json.Unmarshal(hb, &h) != nil || h.Alg != "RS256" || h.Kid == "" {
		return errors.New("invalid JWT header")
	}
	keys, e := v.loadKeys(ctx)
	if e != nil {
		return e
	}
	key := keys[h.Kid]
	if key == nil {
		return errors.New("unknown signing key")
	}
	sig, e := base64.RawURLEncoding.DecodeString(parts[2])
	if e != nil {
		return errors.New("invalid JWT signature encoding")
	}
	hash := sha256.Sum256([]byte(parts[0] + "." + parts[1]))
	if e = rsa.VerifyPKCS1v15(key, crypto.SHA256, hash[:], sig); e != nil {
		return errors.New("JWT signature mismatch")
	}
	var claims struct {
		Iss string   `json:"iss"`
		Aud []string `json:"aud"`
		Exp int64    `json:"exp"`
		Nbf int64    `json:"nbf"`
		Iat int64    `json:"iat"`
		Sub string   `json:"sub"`
	}
	pb, e := base64.RawURLEncoding.DecodeString(parts[1])
	if e != nil || json.Unmarshal(pb, &claims) != nil {
		return errors.New("invalid JWT claims")
	}
	if strings.TrimSuffix(claims.Iss, "/") != v.TeamDomain {
		return errors.New("wrong issuer")
	}
	validAud := false
	for _, a := range claims.Aud {
		if subtle.ConstantTimeCompare([]byte(a), []byte(v.Audience)) == 1 {
			validAud = true
		}
	}
	if !validAud {
		return errors.New("wrong audience")
	}
	now := time.Now().Unix()
	if claims.Exp == 0 || now >= claims.Exp || claims.Nbf > now+60 || claims.Iat > now+60 {
		return errors.New("JWT expired or not yet valid")
	}
	if claims.Sub == "" {
		return errors.New("missing subject")
	}
	return nil
}

// Prevent JWT parsing of arbitrary audiences (a single string is not valid for Cloudflare Access JWTs).
