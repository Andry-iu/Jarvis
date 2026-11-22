# Jarvis Windows MCP: pointer-only movement (2026-11-22)

Status: source candidate only; not yet deployed, verified or released. Based on `ca9fe07ae64d0b19eaf9f3020307ac1e37dfd030`.

## Change
- `ui_mouse` now accepts `mouse_action=move` in addition to existing click, double_click, right_click and wheel.
- It uses the existing one-use 90-second snapshot, exact target window identity, foreground, interactive session, unlocked desktop, on-window and occlusion checks, modifier guard, request receipt and no automatic replay.
- Native `SetCursorPos` moves the pointer; for `move`, execution returns before sending any mouse button or wheel event.
- `wheel_delta` is rejected for `move`.
- The action reports `dispatched=true, verified=false`: there is no independent visual pointer-position verification.

## Separate issue: ChatGPT connector
On Desktop1, `machine_info` at 2026-11-22 reports v0.8.5 and a `ui_mouse` capability, but this ChatGPT session exposes no callable `mcp__Jarvis_Desktop1__ui_mouse` function. The agent server already registers `ui_mouse` in its runtime tool catalog; fixing its missing exposure requires the connected plugin/host tool registry to expose and refresh that function. Editing agent code alone cannot force ChatGPT to publish it. Desktop2 is not involved.

## Validation required
1. Run Go unit tests including `TestUIMouseRequestValidation` and execute PowerShell parse/worker checks.
2. Validate tests/build and check release policy; if creating a new release, align manifest, installer and changelog. Do not report this source-only commit as an installed version.
3. On Desktop1, after the connector actually exposes `ui_mouse`, perform an innocuous `move` within a verified foreground window using a fresh snapshot and confirm the pointer visually. Then use a new snapshot to click a safe target and visually confirm the actual UI result. No VM changes and no replay of uncertain actions.
