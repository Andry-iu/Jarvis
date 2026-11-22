# Jarvis UI session and sequence (0.9.0)

## Purpose

Reuse one screenshot for a bounded sequence of real Windows mouse and keyboard actions. No application-specific drawing scripts and no shell commands in UI sequences.

## API

1. `window_list` to select a verified `window_id`.
2. `screen_capture` once, with `max_width` chosen by the operator; note screenshot coordinates in the returned JPEG and `capture_id`.
3. `ui_session_open` with `machine_id`, `window_id`, `capture_id`, unique `request_id`. The screenshot must be at most 45 seconds old and the selected window must have been foreground when it was captured. The response includes `session_id` and idle timeout.
4. `ui_sequence` with `session_id`, unique `request_id`, and 1–24 ordered steps. Every mouse position is expressed in pixels from that **single screenshot**. No screenshots are captured during sequence execution.
5. `ui_session_close` to revoke it early; otherwise it expires after 300 seconds without a successful sequence. Each agent maintains at most one active session.

Example sequence steps:

```json
[
  {"kind":"mouse","mouse_action":"click","x":360,"y":140},
  {"kind":"mouse","mouse_action":"drag","x":320,"y":300,"end_x":690,"end_y":505},
  {"kind":"keys","keys":["CTRL","A"]},
  {"kind":"text","text":"Testo Unicode: à è 中文"},
  {"kind":"keys","keys":["ENTER"]}
]
```

`kind=mouse` supports `move`, `click`, `double_click`, `right_click`, `wheel` (wheel_delta multiple of 120 within ±1200), and `drag` (both end_x/end_y mandatory). `kind=keys` accepts a single valid chord of 1–5 keys; `kind=text` types 1–1024 Unicode characters. Text across the sequence is limited to 4096 characters. Each step optionally accepts `settle_ms` (0–500 ms) to allow the application to process queued events; the default is 90 ms. Use longer settling after changing tools, colors, clearing documents, or opening dialogs. Unknown actions and extra fields are refused. Commands execute in order and stop on error; never replay a partly executed or uncertain sequence.

## Distinct from chat-wide authorization

The `ui_session_open` timeout is only a *window/screenshot-scoped convenience*, not the user's intended overall Jarvis authorization. The separate requirement is one authorization for all permitted agent tools for a single verified ChatGPT conversation, renewing with each command and expiring after five minutes with no Jarvis commands. See [chat-wide authorization specification](CHAT-WIDE-AUTHORIZATION-20261122.md). The current MCP connector does not provide a trustworthy conversation ID or a grant signal, so this requirement is **not yet implemented**; do not describe the UI session as fulfillment of it.

## Security and timeout

- The session is bound to the exact foreground window identity (HWND/PID/process start) and initial window bounds, as well as the screenshot's physical geometry. It cannot silently switch windows. Window movement invalidates the geometry until a new screenshot/session is opened.
- Each sequence rechecks interactive/secure desktop, live window identity, foreground and unchanged bounds. Mouse primitives continue to validate visible target/occlusion; drag always releases the button. Keyboard actions require the same foreground window.
- Session geometry can become visually stale if an application changes its UI without changing window bounds. The operator must not use uncertain coordinates; recapture and open a new session whenever the layout is no longer reliable.
- A sequence is one mutation with a unique persistent receipt and a 25-second worker timeout; partial or uncertain outcomes invalidate the session. No automatic retry or replay.
- **Five minutes apply to UI session inactivity**, not to Cloudflare authentication, Windows permissions, tool authorization, UAC, window identity or the host application. The agent has no verified ChatGPT conversation identifier in current MCP requests, so a session is not cryptographically bound to a specific conversation.
- `screen_capture` has a hard agent-side limit of **one invocation per second** (also after a failed attempt), and no sequence captures automatically.

## Tools policy and performance

Prefer existing Jarvis MCP tools for GUI, files, processes, Git, updates and inspection. Do **not** substitute standalone BAT/CMD/PowerShell operations while a dedicated agent tool exists. When a fallback becomes recurrent, propose a generic, reusable Jarvis MCP tool instead of perpetuating scripts. Procedures needed for specific projects belong to their project repository.

This release reduces N UI subprocess startups to one for a sequence, but the 0.9.0 UI worker still starts a PowerShell subprocess per **sequence**. A persistent, authenticated native Windows UI worker (or equivalent long-lived process) is a separate potential optimization and must retain isolation, receipts, security and watchdog behavior. Do not claim it has already been implemented.

## Verification

Run Go tests, vet, native/Windows builds and Windows PowerShell 5.1 parsing/native helper compilation; then install and independently verify version/hash and cleanup on each desktop. Practical Paint test: clear the canvas via UI, draw a black rectangle and four small red circles using one open session and one sequence, then inspect a final screenshot.
