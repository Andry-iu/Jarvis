package main

import ("os/exec";"syscall")

// A worker must never create/activate a console over the target application.
func configureDesktopCommand(cmd *exec.Cmd) {
 cmd.SysProcAttr=&syscall.SysProcAttr{HideWindow:true,CreationFlags:0x08000000}
}
