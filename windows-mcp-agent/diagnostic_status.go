package main

import (
	"bufio"
	"errors"
	"os"
	"path/filepath"
	"strings"
)

// diagnosticStatus reads a fixed-format status file, not a script or log body.
// Values are constrained to short, single-line, non-secret tokens.
func diagnosticStatus(id string) (any, error) {
	if !updateIDPattern.MatchString(id) {
		return nil, errors.New("invalid diagnostic id")
	}
	root, err := logRoot()
	if err != nil {
		return nil, err
	}
	path := filepath.Join(root, "diagnostics", id+".status")
	fi, err := os.Lstat(path)
	if err != nil {
		return nil, err
	}
	if !fi.Mode().IsRegular() || fi.Size() > 4096 {
		return nil, errors.New("invalid status file")
	}
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	fields := map[string]any{"id": id}
	allowed := map[string]bool{"PHASE": true, "GO": true, "GIT": true, "TEST": true, "BUILD": true, "EXIT_CODE": true, "DONE": true}
	scanner := bufio.NewScanner(f)
	scanner.Buffer(make([]byte, 128), 4096)
	for scanner.Scan() {
		line := scanner.Text()
		key, val, ok := strings.Cut(line, "=")
		if !ok || !allowed[key] || len(val) > 96 || len(val) == 0 {
			continue
		}
		valid := true
		for _, ch := range val {
			if !(ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9' || ch == '.' || ch == '_' || ch == '-' || ch == '/') {
				valid = false
				break
			}
		}
		if valid {
			fields[strings.ToLower(key)] = val
		}
	}
	if err := scanner.Err(); err != nil {
		return nil, err
	}
	return fields, nil
}