# 0.5.1 automatic local configuration

The Windows agent now loads `D:\Jarvis\Config\agent.env` (optional) and `D:\Jarvis\Config\secrets.env` (required) at startup, with UTF-8 KEY=value lines and # comments. If binary is in `D:\Jarvis\Agent` it derives the root from its executable path. Override using JARVIS_HOME (absolute). Existing process environment takes precedence over file values. Never commit populated secret files. Run the executable directly after setting file permissions for the Windows account. To launch elevated, Windows UAC still applies.

Build on Go 1.23.2: `go test ./...` and `GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -o jarvis-mcp.exe .`. Source/tests built on Linux; Windows runtime test and self-updater end-to-end remain pending. Desktop remains 0.4.1 until user installs a new executable. 

SHA-256 of Windows artifact produced locally: `56d3177891afc9ec88be99680798a3e5ae0a6e08c18879d06cab15306e38870b`.
