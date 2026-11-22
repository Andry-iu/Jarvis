@echo off
setlocal
set "JARVIS_SETUP_PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if exist "%SystemRoot%\Sysnative\WindowsPowerShell\v1.0\powershell.exe" set "JARVIS_SETUP_PS=%SystemRoot%\Sysnative\WindowsPowerShell\v1.0\powershell.exe"
set "JARVIS_INSTALL_SCRIPT=%~dp0Setup.ps1"
"%JARVIS_SETUP_PS%" -NoProfile -ExecutionPolicy Bypass -Command "$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;$p=Start-Process -FilePath ($env:SystemRoot+'\System32\WindowsPowerShell\v1.0\powershell.exe') -Verb RunAs -Wait -PassThru -ArgumentList ('-NoProfile -ExecutionPolicy Bypass -NoExit -File '+[char]34+$env:JARVIS_INSTALL_SCRIPT+[char]34+' -RenameResourcesOnly -ExpectedUserSid '+$sid);exit $p.ExitCode"
endlocal
