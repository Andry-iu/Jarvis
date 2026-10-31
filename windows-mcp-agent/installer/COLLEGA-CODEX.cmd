@echo off
setlocal
set "JARVIS_INSTALL_SCRIPT=%~dp0Setup.ps1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;$p=Start-Process -FilePath ($env:SystemRoot+'\System32\WindowsPowerShell\v1.0\powershell.exe') -Verb RunAs -Wait -PassThru -ArgumentList ('-NoProfile -ExecutionPolicy Bypass -NoExit -File '+[char]34+$env:JARVIS_INSTALL_SCRIPT+[char]34+' -ExpectedUserSid '+$sid);exit $p.ExitCode"
endlocal
