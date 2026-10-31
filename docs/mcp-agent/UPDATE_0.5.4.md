# MCP 0.5.4: log inspection and retention

Adds Cloudflare Access authenticated `log_list` and `log_read` tools. Read is limited to regular `.log` files below `D:\Jarvis\Logs`, rejects symlink traversal and parent paths, and limits a response to 32768 bytes. Lists are capped at 300 entries. Logs with last-modified times older than `JARVIS_LOG_RETENTION_DAYS` (default 30, configured range 1..365) are removed on agent startup and every 24 hours. Non-log files and symlinks are ignored. Avoid writing secrets to logs. `JARVIS_UPDATE_RETENTION_DAYS` cleanup for batches remains future work and is not part of this release.

Go unit tests passed locally and Windows x64 cross-compilation succeeded. Windows runtime behavior is NOT yet verified. Windows binary remains local, not committed to Git. Desktop agent remains 0.5.3 until deployment. SHA-256 binary: 7a29039c153409c35135d11c9cd891bc7366a3c9713aa77c3cff947780b0288e.
