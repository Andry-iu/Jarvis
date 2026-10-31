//go:build !windows

package main

import "os"

// Other platforms preserve os.Root confinement but cannot exclude external
// writers. Mandatory writer exclusion is implemented on the Windows target.
func openStableRead(root *os.Root, n string) (*os.File, error) { return root.Open(n) }

