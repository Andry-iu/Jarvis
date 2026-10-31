JARVIS MCP 0.3.0 - Windows 64 bit

STATUS
- Go source and Windows amd64 executable compiled and tested on Linux.
- No claim of successful ChatGPT connector registration or real Windows execution.
- Prototype supports only status, diagnostic start and stop. No desktop/Codex control.

CHANGES 0.2.0 -> 0.3.0
- Stateless MCP Streamable HTTP endpoint on POST /mcp.
- Handles MCP initialize, ping, tools/list, tools/call and notifications/initialized.
- Negotiates protocol version 2025-03-26, 2025-06-18 or 2025-11-25 (where offered).
- Enforces Accept application/json and text/event-stream for explicitly supplied Accept.
- Clear HTTP status codes for GET/DELETE (405; no SSE support), malformed JSON-RPC,
  invalid methods and unauthenticated requests.
- Preserves bearer token and signed Cloudflare Access JWT validation.
- Keeps localhost bind to avoid exposing the agent as a direct network service.

IMPORTANT
Stateless MCP permits HTTP 405 on GET if the server does not offer an SSE stream.
No session ID is issued and no SSE stream is implemented. Streamable HTTP
interoperability with ChatGPT remains UNVERIFIED. OAuth authorization server is
provided by Cloudflare Managed OAuth, not by this binary. Cloudflare Access
must pass a valid Cf-Access-Jwt-Assertion header to the origin for JWT validation.

UPGRADE IN WINDOWS POWERSHELL
1. Do not alter your existing Cloudflare Tunnel or Access rules.
2. Use Ctrl+C to stop the old 0.2.0 agent (it occupies port 8765).
3. Unzip the new package into D:\Jarvis (or your preferred directory).
4. In the SAME PowerShell window, ensure these env variables are present:
   JARVIS_MCP_TOKEN: at least 24 characters (local testing secret)
   JARVIS_CF_TEAM_DOMAIN: https://<team>.cloudflareaccess.com
   JARVIS_CF_APP_AUD: Access application AUD tag
   DO NOT paste secrets or their values into chat.
5. Run: .\jarvis-mcp-windows-v0.3.0-amd64.exe
6. Verify: curl.exe -i http://127.0.0.1:8765/health
   Expected JSON: {"ok":true,"version":"0.3.0"}
7. Try the existing ChatGPT MCP connector with OAuth and the same public URL.
   If rejected, obtain diagnostics before changing Cloudflare or auth settings.

SECURITY
- Only bind on 127.0.0.1:8765.
- Do not publish the service without Cloudflare Access configured.
- Team Domain must include https:// prefix; values are not automatically
  shared between distinct PowerShell sessions.
- Cloudflare Access login alone does not guarantee MCP OAuth negotiation.

TESTING: go test -race ./...
BUILD: GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o jarvis-mcp-windows-v0.3.0-amd64.exe .
