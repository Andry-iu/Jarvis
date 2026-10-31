//go:build windows

package main

import (
	"strings"
	"testing"
)

func TestDetachedBatchCommand(t *testing.T) {
	c := detachedBatchCommand(`D:\Jarvis\Scripts\updates\0123456789abcdef0123456789abcdef.cmd`)
	if len(c.Args) != 4 || c.Args[1] != "/D" || c.Args[2] != "/C" || !strings.HasSuffix(c.Args[3], ".cmd") {
		t.Fatalf("unexpected args: %q", c.Args)
	}
	if c.SysProcAttr == nil || c.SysProcAttr.CreationFlags == 0 {
		t.Fatal("missing detached flags")
	}
}