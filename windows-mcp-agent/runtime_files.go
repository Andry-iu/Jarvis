package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
	"unicode/utf8"
)

const maxFileBytes = 1024 * 1024

func protectedPart(n string) bool {
	n = strings.ToLower(n)
	switch n {
	case ".git", ".codex", ".ssh", ".aws", ".azure", ".kube", "credentials", "secrets", "config", "auth.json", "secrets.env", "agent.env", "credentials.json", "credentials.xml", "id_rsa", "id_ed25519", "desktop.ini":
		return true
	}
	if n == ".env" || strings.HasPrefix(n, ".env.") || strings.Contains(n, "secret") || strings.HasSuffix(n, ".pem") || strings.HasSuffix(n, ".key") || strings.HasSuffix(n, ".pfx") || strings.HasSuffix(n, ".p12") || strings.HasSuffix(n, ".jks") || strings.HasSuffix(n, ".keystore") {
		return true
	}
	return false
}
func validRelative(n string, allowRoot bool) error {
	if n == "." && allowRoot {
		return nil
	}
	if n == "" || filepath.IsAbs(n) || strings.ContainsAny(n, ":\x00\r\n") || strings.Contains(n, "\\") && filepath.Separator != '\\' {
		return errors.New("use a relative project path without streams, UNC or drive names")
	}
	if filepath.Clean(n) != n || !filepath.IsLocal(n) {
		return errors.New("noncanonical or escaping path")
	}
	for _, part := range strings.FieldsFunc(n, func(r rune) bool { return r == '/' || r == '\\' }) {
		if part == "." || part == ".." || strings.HasSuffix(part, ".") || strings.HasSuffix(part, " ") || strings.ContainsAny(part, "*?<>|\"") || protectedPart(part) || strings.HasPrefix(part, ".jarvis-") {
			return errors.New("protected or unsupported path component")
		}
		stem := strings.ToUpper(strings.SplitN(part, ".", 2)[0])
		if stem == "NUL" || stem == "CON" || stem == "PRN" || stem == "AUX" || (len(stem) == 4 && (strings.HasPrefix(stem, "COM") || strings.HasPrefix(stem, "LPT")) && stem[3] >= '0' && stem[3] <= '9') {
			return errors.New("reserved Windows device name")
		}
	}
	return nil
}
func safePath(root *os.Root, n string, missing bool) error {
	if e := validRelative(n, true); e != nil {
		return e
	}
	current := "."
	parts := strings.FieldsFunc(n, func(r rune) bool { return r == '/' || r == '\\' })
	for _, part := range parts {
		current = filepath.Join(current, part)
		st, e := root.Lstat(current)
		if os.IsNotExist(e) && missing {
			return nil
		}
		if e != nil {
			return e
		}
		if st.Mode()&os.ModeSymlink != 0 || isReparse(st) {
			return errors.New("links and reparse points are not allowed")
		}
		if !st.IsDir() && !st.Mode().IsRegular() {
			return errors.New("only ordinary files and directories are supported")
		}
	}
	return nil
}
func readRegular(root *os.Root, n string) ([]byte, error) {
	if e := safePath(root, n, false); e != nil {
		return nil, e
	}
	f, e := root.Open(n)
	if e != nil {
		return nil, e
	}
	defer f.Close()
	if e = validateOpenedFile(root, f); e != nil {
		return nil, e
	}
	st, e := f.Stat()
	if e != nil {
		return nil, e
	}
	if !st.Mode().IsRegular() || st.Size() > maxFileBytes {
		return nil, errors.New("file is not regular or exceeds 1 MiB")
	}
	b, e := io.ReadAll(io.LimitReader(f, maxFileBytes+1))
	if e != nil {
		return nil, e
	}
	if len(b) > maxFileBytes {
		return nil, errors.New("file exceeds limit")
	}
	if bytes.Contains(b, []byte("-----BEGIN PRIVATE KEY-----")) || bytes.Contains(b, []byte("-----BEGIN OPENSSH PRIVATE KEY-----")) {
		return nil, errors.New("private key material excluded")
	}
	return b, nil
}
func pathDigest(root *os.Root, n string) (string, error) {
	if e := safePath(root, n, false); os.IsNotExist(e) {
		return "missing", nil
	} else if e != nil {
		return "", e
	}
	info, e := root.Stat(n)
	if e != nil {
		return "", e
	}
	if !info.IsDir() {
		b, e := readRegular(root, n)
		return digest(b), e
	}
	h := sha256.New()
	count := 0
	total := 0
	e = fs.WalkDir(root.FS(), filepath.ToSlash(n), func(p string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		count++
		if count > 5000 {
			return errors.New("directory fingerprint exceeds 5000 entries")
		}
		if e := safePath(root, filepath.FromSlash(p), false); e != nil {
			return e
		}
		rel, _ := filepath.Rel(n, filepath.FromSlash(p))
		fmt.Fprintf(h, "%s\x00%t\x00", filepath.ToSlash(rel), d.IsDir())
		if !d.IsDir() {
			b, e := readRegular(root, filepath.FromSlash(p))
			if e != nil {
				return e
			}
			total += len(b)
			if total > 64*maxFileBytes {
				return errors.New("directory fingerprint exceeds 64 MiB")
			}
			fmt.Fprint(h, digest(b))
		}
		return nil
	})
	if e != nil {
		return "", e
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}
func (r *Runtime) filesRead(p runtimeRequest) (any, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	root, e := r.projectRoot(p.Project)
	if e != nil {
		return nil, e
	}
	defer root.Close()
	n := p.Path
	if n == "" {
		n = "."
	}
	if e = safePath(root, n, false); e != nil {
		return nil, e
	}
	if p.Operation == "file_read" {
		b, e := readRegular(root, n)
		if e != nil {
			return nil, e
		}
		s := ""
		encoding := p.Encoding
		if encoding == "" {
			encoding = "utf8"
		}
		switch encoding {
		case "utf8":
			if !utf8.Valid(b) {
				return nil, errors.New("binary file: request encoding=base64")
			}
			s = string(b)
		case "base64":
			s = base64.StdEncoding.EncodeToString(b)
		default:
			return nil, errors.New("unsupported encoding")
		}
		return map[string]any{"path": n, "bytes": len(b), "sha256": digest(b), "encoding": encoding, "content": s}, nil
	}
	if p.Operation == "file_search" && (p.Query == "" || len(p.Query) > 1024) {
		return nil, errors.New("query must contain 1-1024 bytes")
	}
	entries := []map[string]any{}
	walked := 0
	limited := false
	e = fs.WalkDir(root.FS(), filepath.ToSlash(n), func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if path == filepath.ToSlash(n) && d.IsDir() {
			return nil
		}
		walked++
		if walked > 5000 {
			limited = true
			return fs.SkipAll
		}
		file := filepath.FromSlash(path)
		if e := safePath(root, file, false); e != nil {
			if d.IsDir() {
				return fs.SkipDir
			}
			return nil
		}
		info, e := d.Info()
		if e != nil {
			return nil
		}
		item := map[string]any{"path": file, "directory": d.IsDir(), "bytes": info.Size(), "modified_at": info.ModTime().UTC().Format("2006-01-02T15:04:05.999999999Z07:00")}
		match := p.Operation == "file_list" || strings.Contains(strings.ToLower(file), strings.ToLower(p.Query))
		if !d.IsDir() && info.Size() <= maxFileBytes {
			b, e := readRegular(root, file)
			if e == nil {
				item["sha256"] = digest(b)
				if p.Operation == "file_search" && utf8.Valid(b) && strings.Contains(string(b), p.Query) {
					match = true
					item["text_match"] = true
				}
			}
		}
		if match {
			entries = append(entries, item)
		}
		if d.IsDir() && !p.Recursive && p.Operation == "file_list" {
			return fs.SkipDir
		}
		return nil
	})
	if e != nil {
		return nil, e
	}
	sort.Slice(entries, func(i, j int) bool { return entries[i]["path"].(string) < entries[j]["path"].(string) })
	limit := p.Limit
	if limit == 0 {
		limit = 100
	}
	start := p.Offset
	if start > len(entries) {
		start = len(entries)
	}
	end := start + limit
	if end > len(entries) {
		end = len(entries)
	}
	v := map[string]any{"entries": entries[start:end], "offset": start, "next_offset": end, "total_matches": len(entries), "scan_truncated": limited, "walked": walked}
	if n != "." {
		h, e := pathDigest(root, n)
		if e == nil {
			v["path_sha256"] = h
		} else {
			v["fingerprint_unavailable"] = true
		}
	}
	return v, nil
}
func writeRootFile(root *os.Root, n string, b []byte, expected string) error {
	if e := safePath(root, n, true); e != nil {
		return e
	}
	before, e := pathDigest(root, n)
	if e != nil {
		return e
	}
	if before != expected {
		return errors.New("file changed or destination exists; expected_sha256 mismatch")
	}
	tmp := filepath.Join(filepath.Dir(n), ".jarvis-"+newID()+".tmp")
	f, e := root.OpenFile(tmp, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
	if e != nil {
		return e
	}
	defer root.Remove(tmp)
	_, e = f.Write(b)
	if e == nil {
		e = f.Sync()
	}
	ce := f.Close()
	if e == nil {
		e = ce
	}
	if e != nil {
		return e
	}
	// Recheck immediately before replacement; agent operations are serialized.
	if h, e := pathDigest(root, n); e != nil || h != expected {
		return errors.New("file changed during write preparation")
	}
	if expected == "missing" {
		// Link is no-replace: even a concurrently created destination is preserved.
		if e = root.Link(tmp, n); e != nil {
			return e
		}
		if e = root.Remove(tmp); e != nil {
			return e
		}
	} else {
		for attempt := 0; ; attempt++ {
			e = root.Rename(tmp, n)
			if e == nil {
				break
			}
			if attempt >= 10 || !transientFileError(e) {
				return e
			}
			// Retry only a refused filesystem operation with both preconditions
			// still intact. Never replay an uncertain desktop or model action.
			if _, se := root.Lstat(tmp); se != nil {
				return errors.New("replacement outcome uncertain; temporary no longer exists")
			}
			if h, se := pathDigest(root, n); se != nil || h != expected {
				return errors.New("destination changed while waiting for a file lock")
			}
			time.Sleep(100 * time.Millisecond)
		}
	}
	got, e := pathDigest(root, n)
	if e != nil || got != digest(b) {
		return errors.New("write verification failed; inspect backup and receipt")
	}
	return nil
}
func (r *Runtime) filesMutate(p runtimeRequest, x *Receipt) (any, error) {
	if p.Operation == "file_restore" {
		return r.restoreFile(p, x)
	}
	root, e := r.projectRoot(p.Project)
	if e != nil {
		return nil, e
	}
	defer root.Close()
	if p.Path == "." {
		return nil, errors.New("cannot mutate project root")
	}
	if e = safePath(root, p.Path, true); e != nil {
		return nil, e
	}
	before, e := pathDigest(root, p.Path)
	if e != nil {
		return nil, e
	}
	x.Before = before
	if p.Operation == "directory_create" {
		if before != "missing" {
			return nil, errors.New("destination already exists")
		}
		current := "."
		missingParent := false
		for _, part := range strings.FieldsFunc(p.Path, func(v rune) bool { return v == '/' || v == '\\' }) {
			current = filepath.Join(current, part)
			if missingParent {
				x.CreatedPaths = append(x.CreatedPaths, current)
				continue
			}
			_, err := root.Stat(current)
			if os.IsNotExist(err) {
				missingParent = true
				x.CreatedPaths = append(x.CreatedPaths, current)
			} else if err != nil {
				return nil, err
			}
		}
		if e = r.saveReceipt(x); e != nil {
			return nil, e
		}
		for _, n := range x.CreatedPaths {
			if e = root.Mkdir(n, 0700); e != nil {
				return nil, e
			}
		}
		x.After, e = pathDigest(root, p.Path)
		if e != nil {
			return nil, e
		}
		return map[string]any{"path": p.Path, "sha256": x.After, "request_id": x.ID}, nil
	}
	if p.Expected == "" || p.Expected != before {
		return nil, errors.New("expected_sha256 mismatch; read current content/fingerprint first")
	}
	if p.Operation == "file_write" {
		b := []byte(p.Content)
		switch p.Encoding {
		case "", "utf8":
			if !utf8.Valid(b) {
				return nil, errors.New("invalid UTF-8")
			}
		case "base64":
			b, e = base64.StdEncoding.DecodeString(p.Content)
			if e != nil {
				return nil, e
			}
		default:
			return nil, errors.New("unsupported encoding")
		}
		if len(b) > maxFileBytes {
			return nil, errors.New("file exceeds 1 MiB")
		}
		if before != "missing" {
			old, e := readRegular(root, p.Path)
			if e != nil {
				return nil, e
			}
			x.Backup = x.ID + ".bin"
			if e = os.WriteFile(filepath.Join(r.dir, "backups", x.Backup), old, 0600); e != nil {
				return nil, e
			}
			check, e := os.ReadFile(filepath.Join(r.dir, "backups", x.Backup))
			if e != nil || digest(check) != before {
				return nil, errors.New("backup verification failed")
			}
		}
		x.After = digest(b)
		if e = r.saveReceipt(x); e != nil {
			return nil, e
		}
		if e = writeRootFile(root, p.Path, b, before); e != nil {
			return nil, e
		}
	} else if p.Operation == "path_move" {
		if before == "missing" {
			return nil, errors.New("source missing")
		}
		if e = safePath(root, p.Destination, true); e != nil {
			return nil, e
		}
		if p.Destination == "." {
			return nil, errors.New("destination cannot be project root")
		}
		dst, e := pathDigest(root, p.Destination)
		if e != nil || dst != "missing" {
			return nil, errors.New("destination already exists or is unavailable")
		}
		x.After = before
		if e = r.saveReceipt(x); e != nil {
			return nil, e
		}
		if e = moveNoReplace(root, p.Path, p.Destination); e != nil {
			return nil, e
		}
		h, e := pathDigest(root, p.Destination)
		if e != nil || h != before {
			return nil, errors.New("move verification failed; preserve both locations and inspect receipt")
		}
	} else {
		return nil, errors.New("unsupported file mutation")
	}
	return map[string]any{"path": p.Path, "destination": p.Destination, "sha256": x.After, "request_id": x.ID, "backup_verified": x.Backup != "" || p.Operation == "path_move"}, nil
}
func (r *Runtime) restoreFile(p runtimeRequest, x *Receipt) (any, error) {
	if !requestIDPattern.MatchString(p.RestoreID) {
		return nil, errors.New("invalid restore_id")
	}
	b, e := os.ReadFile(filepath.Join(r.dir, "requests", p.RestoreID+".json"))
	if e != nil {
		return nil, e
	}
	var old Receipt
	if json.Unmarshal(b, &old) != nil || old.Status != "completed" {
		return nil, errors.New("only completed file receipts can be restored")
	}
	root, e := r.projectRoot(old.Project)
	if e != nil {
		return nil, e
	}
	defer root.Close()
	x.Project = old.Project
	x.Path = old.Path
	x.Destination = old.Destination
	if old.Operation == "path_move" {
		h, e := pathDigest(root, old.Destination)
		if e != nil || h != old.After {
			return nil, errors.New("moved content has changed")
		}
		h, e = pathDigest(root, old.Path)
		if e != nil || h != "missing" {
			return nil, errors.New("original path is occupied")
		}
		if e = r.saveReceipt(x); e != nil {
			return nil, e
		}
		if e = moveNoReplace(root, old.Destination, old.Path); e != nil {
			return nil, e
		}
	} else if old.Operation == "file_write" || old.Operation == "directory_create" {
		h, e := pathDigest(root, old.Path)
		if e != nil || h != old.After {
			return nil, errors.New("current content differs from receipt")
		}
		if old.Before == "missing" {
			if e = r.saveReceipt(x); e != nil {
				return nil, e
			}
			if old.Operation == "directory_create" {
				for i := len(old.CreatedPaths) - 1; i >= 0; i-- {
					if e = safePath(root, old.CreatedPaths[i], false); e != nil {
						return nil, e
					}
					if e = root.Remove(old.CreatedPaths[i]); e != nil {
						return nil, e
					}
				}
			} else if e = root.Remove(old.Path); e != nil {
				return nil, e
			}
		} else {
			if old.Backup != old.ID+".bin" {
				return nil, errors.New("backup unavailable")
			}
			b, e := os.ReadFile(filepath.Join(r.dir, "backups", old.Backup))
			if e != nil || digest(b) != old.Before {
				return nil, errors.New("backup checksum mismatch")
			}
			if e = r.saveReceipt(x); e != nil {
				return nil, e
			}
			if e = writeRootFile(root, old.Path, b, old.After); e != nil {
				return nil, e
			}
		}
	} else {
		return nil, errors.New("receipt is not a reversible file operation")
	}
	want := old.Before
	if old.Operation == "path_move" {
		want = old.After
	}
	actual, e := pathDigest(root, old.Path)
	if e != nil || actual != want {
		return nil, errors.New("restore verification failed; inspect receipt before further changes")
	}
	return map[string]any{"restored": old.ID, "request_id": x.ID, "verified": true}, nil
}
