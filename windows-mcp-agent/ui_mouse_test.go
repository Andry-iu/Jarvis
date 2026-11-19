package main

import (
    "encoding/json"
    "testing"
)

func TestUIMouseRequestValidation(t *testing.T) {
    cases := []struct{ name, payload string; ok bool }{
        {"click", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"mouse_action":"click","request_id":"ui-mouse-test-click-001"}`, true},
        {"wheel", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"mouse_action":"wheel","wheel_delta":-120,"request_id":"ui-mouse-test-wheel-001"}`, true},
        {"missing_x", `{"machine_id":"machine","snapshot_id":"fresh","y":200,"mouse_action":"click","request_id":"ui-mouse-test-missing-x"}`, false},
        {"missing_y", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"mouse_action":"click","request_id":"ui-mouse-test-missing-y"}`, false},
        {"unsupported_action", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"mouse_action":"drag","request_id":"ui-mouse-test-action-001"}`, false},
        {"bad_wheel_delta", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"mouse_action":"wheel","wheel_delta":1,"request_id":"ui-mouse-test-wheel-bad"}`, false},
        {"extra_field", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"mouse_action":"click","unsafe":true,"request_id":"ui-mouse-test-extra-001"}`, false},
    }
    for _, tc := range cases {
        t.Run(tc.name, func(t *testing.T) {
            _, err := decodeRuntime("ui_mouse", json.RawMessage(tc.payload))
            if (err == nil) != tc.ok { t.Fatalf("accepted=%t, want=%t: %v", err == nil, tc.ok, err) }
        })
    }
}

func TestUIMouseToolIsMutation(t *testing.T) {
    if !isDirectUI("ui_mouse") { t.Fatal("mouse tool not dispatched through guarded UI") }
    for _, spec := range directToolSpecs {
        if spec.Name == "ui_mouse" {
            if spec.ReadOnly { t.Fatal("mouse dispatch must not be read-only") }
            if len(spec.Required) != 5 { t.Fatalf("wrong required parameters: %v", spec.Required) }
            return
        }
    }
    t.Fatal("ui_mouse missing from catalog")
}
