package main

import (
    "strings"
    "testing"
)

// A regression test for the external MCP catalog (not merely the internal
// runtime tool registry). If these tests pass but the ChatGPT connector does
// not expose the names, the host's cached tool schemas or connection setup
// need refreshing; changing authorization inside Jarvis will not fix it.
func TestPublicMCPDirectUITools(t *testing.T) {
    listed, ok := toolsList().(map[string]any)
    if !ok {t.Fatal("toolsList must return a JSON-like object")}
    raw, ok := listed["tools"].([]any)
    if !ok {t.Fatal("tools list missing")}

    byName := map[string]map[string]any{}
    for _, v := range raw {
        item, ok := v.(map[string]any)
        if !ok {t.Fatal("invalid tool descriptor")}
        name, ok := item["name"].(string)
        if !ok {t.Fatal("missing tool name")}
        if _, duplicate := byName[name]; duplicate {t.Fatalf("duplicate public tool: %q", name)}
        byName[name] = item
    }

    requiredFields := map[string][]string{
        "window_list": nil,
        "window_inspect": {"window_id"},
        "window_focus": {"window_id", "request_id"},
        "screen_capture": nil,
        "ui_session_open": {"window_id", "capture_id", "request_id"},
        "ui_sequence": {"session_id", "steps", "request_id"},
        "ui_session_close": {"session_id", "request_id"},
        "ui_mouse": {"snapshot_id", "mouse_action", "x", "y", "request_id"},
        "ui_mouse_screen": {"capture_id", "mouse_action", "x", "y", "request_id"},
        "ui_keys": {"snapshot_id", "keys", "request_id"},
        "ui_action": {"snapshot_id", "element_id", "action", "request_id"},
    }
    for name, fields := range requiredFields {
        entry, ok := byName[name]
        if !ok { t.Errorf("MCP tools/list missing direct tool %q", name); continue }
        schema := entry["inputSchema"].(map[string]any)
        props := schema["properties"].(map[string]any)
        required := map[string]bool{}
        for _, v := range schema["required"].([]string) {required[v] = true}
        for _, field := range fields {
            if props[field] == nil {t.Errorf("%s missing property %s", name, field)}
            if !required[field] {t.Errorf("%s does not require %s", name, field)}
        }
        if name != "screen_capture" && name != "window_list" && !required["machine_id"] {
            t.Errorf("%s must identify its target machine", name)
        }
        if _, exists := props["session_token"]; exists {
            t.Errorf("%s must not expose a conversational session token", name)
        }
        if schema["additionalProperties"] != false {
            t.Errorf("%s must not accept arbitrary top-level fields", name)
        }
        if name == "ui_sequence" {
            steps := props["steps"].(map[string]any)
            if steps["minItems"] != 1 || steps["maxItems"] != 24 {
                t.Errorf("ui_sequence has unexpected step limits: %v", steps)
            }
            items := steps["items"].(map[string]any)
            stepProps := items["properties"].(map[string]any)
            pause := stepProps["settle_ms"].(map[string]any)
            if pause["minimum"] != 0 || pause["maximum"] != 500 {
                t.Errorf("ui_sequence settle_ms range must remain 0..500")
            }
        }
        if name != "screen_capture" && !isRuntimeTool(name) {
            t.Errorf("%s not routed as runtime tool", name)
        }
    }
    if byName["task_start"] == nil {
        t.Fatal("legacy compatibility wrapper must remain published")
    }
    description := strings.ToLower(byName["task_start"]["description"].(string))
    for _, keyword := range []string{"fallback", "prefer", "dedicated", "ui_sequence"} {
        if !strings.Contains(description, keyword) {
            t.Errorf("task_start description must discourage use when dedicated tools exist: missing %q", keyword)
        }
    }
}
