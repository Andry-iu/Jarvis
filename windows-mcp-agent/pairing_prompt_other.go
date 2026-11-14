//go:build !windows

package main

import (
	"context"
	"errors"
)

func confirmPeerPair(context.Context, pairPrompt) (bool, error) {
	return false, errors.New("local pairing confirmation requires Windows")
}
