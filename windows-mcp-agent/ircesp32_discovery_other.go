//go:build !windows || (!amd64 && !arm64)

package main

import (
	"errors"
	"net/http"
)

func ircHotspots() ([]ircHotspot, error) {
	return nil, errors.New("IRCESP32 hotspot discovery requires Windows x64 or ARM64")
}

func ircHotspotClient(ircHotspot) (*http.Client, error) {
	return nil, errors.New("IRCESP32 hotspot discovery requires Windows x64 or ARM64")
}
