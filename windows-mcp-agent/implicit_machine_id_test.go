package main

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestConnectedMachineImplicitForEveryRuntimeTool(t *testing.T) {
	for _, spec := range runtimeCatalog {
		p := runtimeRequest{Operation: spec.Name}
		if err := bindConnectedMachine(&p, "desktop-1"); err != nil {
			t.Fatalf("%s: implicit binding: %v", spec.Name, err)
		}
		if spec.Name != "machine_info" && p.MachineID != "desktop-1" {
			t.Errorf("%s missing implicit local machine", spec.Name)
		}
		p = runtimeRequest{Operation: spec.Name, MachineID: "desktop-2"}
		if err := bindConnectedMachine(&p, "desktop-1"); err == nil {
			t.Errorf("%s accepted a different computer ID", spec.Name)
		}
		p = runtimeRequest{Operation: spec.Name, MachineID: "desktop-1"}
		if err := bindConnectedMachine(&p, "desktop-1"); err != nil {
			t.Errorf("%s rejected a matching legacy machine ID: %v", spec.Name, err)
		}
	}
}

func TestMCPAllDedicatedToolsOmitPersistentMachineID(t *testing.T) {
	catalog := toolsList().(map[string]any)
	for _, v := range catalog["tools"].([]any) {
		item := v.(map[string]any)
		name := item["name"].(string)
		if !isRuntimeTool(name) {
			continue
		}
		schema := item["inputSchema"].(map[string]any)
		props := schema["properties"].(map[string]any)
		if _, ok := props["machine_id"]; ok {
			t.Errorf("%s still discloses machine_id in public input schema", name)
		}
		required := schema["required"].([]string)
		for _, field := range required {
			if field == "machine_id" {
				t.Errorf("%s still requires machine_id", name)
			}
		}
	}
}

func TestConnectedMachineLegacyAndNewCalls(t *testing.T) {
	for _, input := range []string{
		`{"operation":"project_catalog"}`,
		`{"operation":"screen_capture"}`,
		`{"operation":"ui_sequence","session_id":"ui-test","request_id":"test-implicit-target-1234","steps":[{"kind":"keys","keys":["ESC"]}]}`,
		`{"operation":"device_call","device_id":"paired-target","call":{}}`,
		`{"operation":"project_catalog","machine_id":"desktop-1"}`,
	} {
		p, err := decodeRuntime("", json.RawMessage(input))
		if err != nil {
			t.Fatalf("decode failed for %s: %v", input, err)
		}
		if err := bindConnectedMachine(&p, "desktop-1"); err != nil {
			t.Fatalf("bind failed for %s: %v", input, err)
		}
		if p.MachineID != "desktop-1" {
			t.Fatalf("wrong target for %s", input)
		}
	}
	p, err := decodeRuntime("", json.RawMessage(`{"operation":"project_catalog","machine_id":"desktop-2"}`))
	if err != nil || !strings.Contains(bindConnectedMachine(&p, "desktop-1").Error(), "mismatch") {
		t.Fatal("explicit wrong target was not refused")
	}
}
