package main

import (
	"bufio"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

var envName = regexp.MustCompile(`^[A-Za-z_][A-Za-z0-9_]*$`)

// loadLocalConfig reads the fixed local configuration files before agent setup.
// Existing process environment overrides files, allowing controlled deployments.
func loadLocalConfig() error {
	root := os.Getenv("JARVIS_HOME")
	if root == "" {
		exe, err := os.Executable()
		if err != nil {
			return err
		}
		dir := filepath.Dir(exe)
		if strings.EqualFold(filepath.Base(dir), "Agent") {
			root = filepath.Dir(dir)
		} else {
			root = `D:\Jarvis`
		}
	}
	if !filepath.IsAbs(root) {
		return errors.New("JARVIS_HOME must be absolute")
	}
	if err := readEnvFile(filepath.Join(root, "Config", "agent.env"), false); err != nil {
		return err
	}
	if err := readEnvFile(filepath.Join(root, "Config", "secrets.env"), true); err != nil {
		return err
	}
	if os.Getenv("JARVIS_HOME") == "" {
		return os.Setenv("JARVIS_HOME", root)
	}
	return nil
}
func readEnvFile(path string, required bool) error {
	f, err := os.Open(path)
	if os.IsNotExist(err) && !required {
		return nil
	}
	if err != nil {
		return fmt.Errorf("configuration file unavailable (%s): %w", filepath.Base(path), err)
	}
	defer f.Close()
	scanner := bufio.NewScanner(f)
	scanner.Buffer(make([]byte, 4096), 1024*1024)
	lineNum := 0
	for scanner.Scan() {
		lineNum++
		s := strings.TrimSpace(scanner.Text())
		if lineNum == 1 {
			s = strings.TrimPrefix(s, "\ufeff")
		}
		if s == "" || strings.HasPrefix(s, "#") {
			continue
		}
		name, value, ok := strings.Cut(s, "=")
		name = strings.TrimSpace(name)
		if !ok || !envName.MatchString(name) {
			return fmt.Errorf("invalid configuration in %s line %d", filepath.Base(path), lineNum)
		}
		if strings.ContainsAny(value, "\x00\r\n") {
			return fmt.Errorf("invalid value in %s line %d", filepath.Base(path), lineNum)
		}
		if _, exists := os.LookupEnv(name); !exists {
			if err := os.Setenv(name, value); err != nil {
				return fmt.Errorf("unable to set %s", name)
			}
		}
	}
	if err := scanner.Err(); err != nil {
		return fmt.Errorf("read %s: %w", filepath.Base(path), err)
	}
	return nil
}