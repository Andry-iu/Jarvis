package main

import (
    "encoding/json"
    "testing"
)

func TestUIMouseRequestValidation(t *testing.T) {
    cases := []struct{ name, payload string; ok bool }{
        {"move", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"mouse_action":"move","request_id":"ui-mouse-test-move-001"}`, true},
        {"move_reject_wheel", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"mouse_action":"move","wheel_delta":120,"request_id":"ui-mouse-test-move-wheel-001"}`, false},
        {"click", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"mouse_action":"click","request_id":"ui-mouse-test-click-001"}`, true},
        {"drag", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"end_x":450,"end_y":360,"mouse_action":"drag","request_id":"ui-mouse-test-drag-001"}`, true},
        {"drag_zero_endpoint", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"end_x":0,"end_y":0,"mouse_action":"drag","request_id":"ui-mouse-test-drag-zero"}`, true},
        {"drag_missing_end_x", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"end_y":360,"mouse_action":"drag","request_id":"ui-mouse-test-drag-missing-x"}`, false},
        {"drag_missing_end_y", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"end_x":450,"mouse_action":"drag","request_id":"ui-mouse-test-drag-missing-y"}`, false},
        {"drag_negative_endpoint", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"end_x":-1,"end_y":360,"mouse_action":"drag","request_id":"ui-mouse-test-drag-negative"}`, false},
        {"drag_outside_bounds", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"end_x":32768,"end_y":360,"mouse_action":"drag","request_id":"ui-mouse-test-drag-range"}`, false},
        {"drag_wheel_rejected", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"end_x":450,"end_y":360,"wheel_delta":120,"mouse_action":"drag","request_id":"ui-mouse-test-drag-wheel"}`, false},
        {"click_with_drag_end_rejected", `{"machine_id":"machine","snapshot_id":"fresh","x":150,"y":200,"end_x":450,"end_y":360,"mouse_action":"click","request_id":"ui-mouse-test-click-with-end"}`, false},
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
