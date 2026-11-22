//go:build windows

package main

import (
	"flag"
	"os"
	"path/filepath"
	"testing"
)

// Only the go-test binary has this entrypoint. The shipping executable cannot
// select a test launcher through arguments, configuration or environment.
func TestMain(m *testing.M) {
	if len(os.Args) > 1 && (os.Args[1] == "-listen" || os.Args[1] == "-restart-admin-helper") {
		exe, e := os.Executable()
		if e != nil {
			os.Exit(2)
		}
		os.Setenv("JARVIS_HOME", filepath.Dir(filepath.Dir(exe)))
		if len(os.Args) == 5 && os.Args[3] == "-test-elevation-notify" {
			notify := os.Args[4]
			os.Args = os.Args[:3]
			elevationLauncher = func(x *adminRestart) error { return os.WriteFile(notify, []byte(x.ID), 0600) }
		}
		flag.CommandLine = flag.NewFlagSet("isolated-agent-test", flag.ExitOnError)
		main()
		os.Exit(0)
	}
	os.Exit(m.Run())
}
