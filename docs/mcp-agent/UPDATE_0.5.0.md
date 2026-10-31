# Windows MCP update agent 0.5.0 (source release)

New tools: `update_prepare` and `update_execute`, **only accessible through valid Cloudflare Access JWT**. Existing local bearer is not sufficient for these tools. Full batch sent as UTF-8 with CRLF line breaks. First line must be `REM JARVIS-UPDATE-ID: <32 hex digits>`. Request includes SHA-256 of exact UTF-8 bytes, and update ID. The agent stages each script with O_EXCL under `D:\\Jarvis\\Scripts\\updates\\<id>.cmd`, rechecks bytes and SHA before invoking Windows cmd as a separate process. It does not claim success after launch.

The content of a release-specific batch is supplied via MCP; it must implement backup, stop, update, restart, health check and rollback. Secrets are kept **locally** under `D:\\Jarvis\\Config\\secrets.env` (never commit them), loaded by launch scripts. Deployment scripts need Windows integration testing; execution of arbitrary elevated batch is a privileged operation.

**Deployment status:** Sources uploaded only; notebook remains on 0.4.1 until manually installed, because v0.4.1 does not expose updater tools. Do not claim 0.5.0 active. Pure Go tests and Windows integration tests should run before first installation. NestingOpt clone remains paused.
