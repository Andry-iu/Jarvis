package main

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"math/big"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestAccessJWT(t *testing.T) {
	private, e := rsa.GenerateKey(rand.Reader, 2048)
	if e != nil {
		t.Fatal(e)
	}
	enc := base64.RawURLEncoding.EncodeToString
	jwks := map[string]any{"keys": []any{map[string]string{"kty": "RSA", "kid": "test-key", "alg": "RS256", "n": enc(private.N.Bytes()), "e": enc(big.NewInt(int64(private.E)).Bytes())}}}
	payload, _ := json.Marshal(jwks)
	transport := roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if r.URL.Host != "my-team.cloudflareaccess.com" || r.URL.Path != "/cdn-cgi/access/certs" {
			return nil, fmt.Errorf("unexpected JWKS URL")
		}
		return &http.Response{StatusCode: 200, Body: ioNopCloser{strings.NewReader(string(payload))}, Header: make(http.Header)}, nil
	})
	v := &AccessVerifier{TeamDomain: "https://my-team.cloudflareaccess.com", Audience: "app-audience", Client: &http.Client{Transport: transport}}
	issue := func(aud string, expiration int64) string {
		header, _ := json.Marshal(map[string]string{"alg": "RS256", "kid": "test-key"})
		claims, _ := json.Marshal(map[string]any{"iss": v.TeamDomain, "aud": []string{aud}, "exp": expiration, "sub": "person@example.com"})
		body := enc(header) + "." + enc(claims)
		digest := sha256.Sum256([]byte(body))
		signature, e := rsa.SignPKCS1v15(rand.Reader, private, crypto.SHA256, digest[:])
		if e != nil {
			t.Fatal(e)
		}
		return body + "." + enc(signature)
	}
	good := issue("app-audience", time.Now().Add(time.Hour).Unix())
	if e := v.Verify(context.Background(), good); e != nil {
		t.Fatal(e)
	}
	if e := v.Verify(context.Background(), issue("other", time.Now().Add(time.Hour).Unix())); e == nil {
		t.Fatal("wrong audience accepted")
	}
	if e := v.Verify(context.Background(), issue("app-audience", time.Now().Add(-time.Minute).Unix())); e == nil {
		t.Fatal("expired JWT accepted")
	}
	if e := v.Verify(context.Background(), good[:len(good)-2]+"AA"); e == nil {
		t.Fatal("bad signature accepted")
	}
	server := httptest.NewServer(handler(&Agent{}, "", v))
	defer server.Close()
	req, _ := http.NewRequest("POST", server.URL+"/mcp", strings.NewReader(`{"jsonrpc":"2.0","id":1,"method":"tools/list"}`))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Cf-Access-Jwt-Assertion", good)
	res, e := http.DefaultClient.Do(req)
	if e != nil {
		t.Fatal(e)
	}
	res.Body.Close()
	if res.StatusCode != 200 {
		t.Fatal(res.StatusCode)
	}
}

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

type ioNopCloser struct{ *strings.Reader }

func (i ioNopCloser) Close() error { return nil }
