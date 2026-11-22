//go:build windows

package main

import (
	"bytes"
	"context"
	"os"
	"strings"
	"syscall"
	"testing"
	"time"
	"unsafe"
)

func TestPairingPromptHasNoConsoleButDoesNotHideGUI(t *testing.T) {
	cmd := pairingPromptCommand(context.Background(), pairPrompt{})
	if cmd.SysProcAttr == nil || cmd.SysProcAttr.HideWindow || cmd.SysProcAttr.CreationFlags&0x08000000 == 0 {
		t.Fatal("pairing must suppress the console without SW_HIDE on its GUI")
	}
}

// Opt-in integration test uses the REAL production command, not a substitute
// ProcessStartInfo launcher. It only dismisses a dummy dialog; no peer or trust
// configuration is created and no approval is synthesized.
func TestPairingPromptNativeVisible(t *testing.T) {
	if os.Getenv("JARVIS_TEST_PAIRING_UI") != "1" {
		t.Skip("unlocked Windows desktop required")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	cmd := pairingPromptCommand(ctx, pairPrompt{Name: "PROVA DIAGNOSTICA - NON CONSENTIRE", Machine: "fixture", Address: "127.0.0.1", Code: "ABCD-1234-5678"})
	var output, stderr bytes.Buffer
	cmd.Stdout = &output
	cmd.Stderr = &stderr
	if e := cmd.Start(); e != nil {
		t.Fatal(e)
	}
	defer func() { _ = cmd.Process.Kill(); _ = cmd.Wait() }()
	user := syscall.NewLazyDLL("user32.dll")
	var handle uintptr
	visible := false
	callback := syscall.NewCallback(func(hwnd, unused uintptr) uintptr {
		var pid uint32
		user.NewProc("GetWindowThreadProcessId").Call(hwnd, uintptr(unsafe.Pointer(&pid)))
		if pid != uint32(cmd.Process.Pid) {
			return 1
		}
		var title [128]uint16
		user.NewProc("GetWindowTextW").Call(hwnd, uintptr(unsafe.Pointer(&title[0])), uintptr(len(title)))
		if syscall.UTF16ToString(title[:]) == "Jarvis - Autorizza collegamento" {
			handle = hwnd
			v, _, _ := user.NewProc("IsWindowVisible").Call(hwnd)
			visible = v != 0
		}
		return 1
	})
	for i := 0; i < 200; i++ {
		user.NewProc("EnumWindows").Call(callback, 0)
		if visible {
			break
		}
		time.Sleep(50 * time.Millisecond)
	}
	// Inspect only this dummy dialog. Never synthesize a correct approval.
	testedWrongAttempts := false
	if visible {
		var input, allow, countdown uintptr
		readText := func(hwnd uintptr) string {
			var text [1024]uint16
			user.NewProc("SendMessageW").Call(hwnd, 0x000D, uintptr(len(text)), uintptr(unsafe.Pointer(&text[0])))
			return syscall.UTF16ToString(text[:])
		}
		childCallback := syscall.NewCallback(func(hwnd, unused uintptr) uintptr {
			text := readText(hwnd)
			if strings.Contains(text, "ABCD-1234-5678") || strings.Contains(text, "ABCD12345678") {
				t.Error("expected pairing code is visible in a control")
			}
			var class [128]uint16
			user.NewProc("GetClassNameW").Call(hwnd, uintptr(unsafe.Pointer(&class[0])), uintptr(len(class)))
			if strings.Contains(syscall.UTF16ToString(class[:]), "EDIT") || strings.Contains(strings.ToLower(syscall.UTF16ToString(class[:])), "edit") {
				input = hwnd
			}
			if text == "Consenti" {
				allow = hwnd
			}
			if strings.HasPrefix(text, "Tempo rimasto: ") {
				countdown = hwnd
			}
			return 1
		})
		user.NewProc("EnumChildWindows").Call(handle, childCallback, 0)
		if input == 0 || allow == 0 || countdown == 0 {
			t.Error("missing code input, approval button or countdown")
		} else {
			enabled, _, _ := user.NewProc("IsWindowEnabled").Call(allow)
			if enabled != 0 {
				t.Error("empty input enables approval")
			}
			before := readText(countdown)
			time.Sleep(1200 * time.Millisecond)
			if after := readText(countdown); after == before || !strings.HasPrefix(after, "Tempo rimasto: ") {
				t.Error("countdown did not advance")
			}
			testedWrongAttempts = true
			wrong, _ := syscall.UTF16PtrFromString("0000-0000-0000")
			for i := 0; i < 3; i++ {
				user.NewProc("SendMessageW").Call(input, 0x000C, 0, uintptr(unsafe.Pointer(wrong)))
				user.NewProc("SendMessageW").Call(allow, 0x00F5, 0, 0)
			}
		}
	}
	if handle != 0 && !testedWrongAttempts {
		user.NewProc("PostMessageW").Call(handle, 0x10, 0, 0)
	}
	if !visible {
		t.Fatal("production pairing dialog was not visible on the interactive desktop")
	}
	if e := cmd.Wait(); e != nil {
		t.Fatal("dummy dialog did not dismiss cleanly", e)
	}
	if output.String() != "DENY" {
		t.Fatal("dummy dialog must not grant approval")
	}
}
