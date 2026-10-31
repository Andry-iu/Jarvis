# Jarvis MCP 0.5.5: Structured diagnostic status

Adds Cloudflare Access authenticated `diagnostic_status` with a required 32-hex `id`. It reads only `D:\Jarvis\Logs\diagnostics\<id>.status`, and returns short allowlisted values: PHASE, GO, GIT, TEST, BUILD, EXIT_CODE, DONE. It does not expose logs, secrets, or arbitrary filesystem access. The status file is produced by a diagnostic batch and is limited to 4096 bytes; see Go tests. This is independent of existing `log_read`, which sometimes returns no usable result through the current plugin connection. It does not alter update execution or backup logic.

The source has passed `go test ./...` and Windows amd64 cross-compilation. Desktop installation and live MCP tool discovery are pending. No binary was pushed to GitHub. Built EXE SHA-256 `f47ca4b7e9b4dd7eba0500f840632129f7823423c92519d022fe5ff2a339fc43`.
