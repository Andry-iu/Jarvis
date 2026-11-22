//go:build !windows

package main

import (
	"errors"
	"os"
)

func transientFileError(err error) bool { return false }

func isReparse(st os.FileInfo) bool                      { return false }
func validateOpenedFile(root *os.Root, f *os.File) error { return nil }
func moveNoReplace(root *os.Root, src, dst string) error {
	return errors.New("path_move currently requires Windows no-replace rename")
}
