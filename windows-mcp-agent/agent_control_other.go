//go:build !windows

package main

import "errors"

func currentProcessElevated() (bool, error) {
	return false, errors.New("Windows process token unavailable")
}
func restartIdentity() (string, uint64, error)   { return "", 0, errors.New("Windows only") }
func launchAdminRestart(*adminRestart) error     { return errors.New("Windows only") }
func runAdminRestartHelper(string, string) error { return errors.New("Windows only") }
