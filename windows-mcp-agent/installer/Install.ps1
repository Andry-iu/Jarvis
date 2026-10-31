[CmdletBinding()]
param([string]$DeviceName,[string]$InstallDir,[string]$ProjectsRoot,[ValidateRange(1024,65535)][int]$Port=8765,[switch]$NonInteractive,[switch]$NoTask,[switch]$NoShortcuts,[switch]$NoStart)
. "$PSScriptRoot\Common.ps1"
if (-not [Environment]::Is64BitOperatingSystem -or $env:PROCESSOR_ARCHITECTURE -ne 'AMD64') { throw 'Pacchetto per Windows x64; usare PowerShell a 64 bit.' }
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Avviare INSTALLA.cmd e consentire la richiesta amministratore.' }
if (-not $DeviceName) {
    if (-not $NonInteractive) { $DeviceName=Read-Host 'Nome del dispositivo Jarvis [Desktop2]' }
    if (-not $DeviceName) { $DeviceName='Desktop2' }
}
$names=Get-DeviceNames $DeviceName
$DeviceName=$names.device_name
if (-not $InstallDir) {
    if ($NonInteractive) { throw 'InstallDir obbligatoria.' }
    $InstallDir = Read-Host ('Cartella di installazione ['+$names.default_home+']')
    if (-not $InstallDir) { $InstallDir=$names.default_home }
}
$InstallDir = Get-FullPath $InstallDir
if (-not $ProjectsRoot) {
    if ($NonInteractive) { $ProjectsRoot=Join-Path $InstallDir 'Projects' }
    else { $ProjectsRoot=Read-Host ('Cartella progetti ['+(Join-Path $InstallDir 'Projects')+']'); if (-not $ProjectsRoot) { $ProjectsRoot=Join-Path $InstallDir 'Projects' } }
}
$ProjectsRoot = Get-FullPath $ProjectsRoot
foreach ($private in @('Config','State','Agent','Scripts','Logs','Backups')) {
    if ((Test-Overlap $ProjectsRoot (Join-Path $InstallDir $private)) -or (Test-Overlap $ProjectsRoot $InstallDir) -and -not $ProjectsRoot.StartsWith($InstallDir+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'La cartella progetti deve essere separata dallo stato privato e non contenere l installazione.' }
}
if ($ProjectsRoot -ieq $InstallDir) { throw 'Non autorizzare come progetto l intera installazione.' }
if ((Test-Path -LiteralPath $InstallDir) -and @(Get-ChildItem -LiteralPath $InstallDir -Force).Count -gt 0) { throw 'Cartella non vuota: nessuna installazione precedente viene sovrascritta.' }
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw 'Porta gia occupata: scegliere un altra porta; nessun processo viene fermato.' }
$manifest = Get-Content -LiteralPath "$PSScriptRoot\package.json" -Raw | ConvertFrom-Json
if ($manifest.agent_version -ne '0.6.1' -or $manifest.installer_version -ne '1.0.2') { throw 'Versione pacchetto non valida.' }
foreach ($f in $manifest.files) {
    if ($f.path -notmatch '^[A-Za-z0-9_.-]+$') { throw 'Percorso payload non valido.' }
    if ((Get-FileHash -LiteralPath (Join-Path $PSScriptRoot $f.path)).Hash -ine $f.sha256) { throw ('Pacchetto alterato o incompleto: '+$f.path) }
}
$taskName = $names.task_prefix+[guid]::NewGuid().ToString('N').Substring(0,12)
$codex=Find-Codex
$taskCreated=$false;$newRoot=$false
try {
    if (-not (Test-Path -LiteralPath $InstallDir)) { New-Item -ItemType Directory -Path $InstallDir | Out-Null; $newRoot=$true }
    Protect-Folder $InstallDir
    foreach ($n in @('Agent','Scripts','Config','State','Logs','Backups')) { New-Item -ItemType Directory -Path (Join-Path $InstallDir $n) | Out-Null }
    New-Item -ItemType Directory -Force -Path $ProjectsRoot | Out-Null
    foreach ($f in $manifest.files) {
        if ($f.path -eq 'jarvis-mcp.exe') { $to=Join-Path $InstallDir 'Agent\jarvis-mcp.exe' }
        elseif ($f.path -like '*.ps1' -and $f.path -notin @('Install.ps1','Test-Installer.ps1','Repair.ps1')) { $to=Join-Path $InstallDir ('Scripts\'+$f.path) }
        else { continue }
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $f.path) -Destination $to
        if ((Get-FileHash -LiteralPath $to).Hash -ine $f.sha256) { throw 'Verifica copia non riuscita.' }
    }
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $settings=@{format=1;installer_version='1.0.2';device_name=$DeviceName;plugin_name=$names.plugin_name;agent_version='0.6.1';agent_sha256=$manifest.agent_sha256;home=$InstallDir;projects_root=$ProjectsRoot;port=$Port;computer=$env:COMPUTERNAME;user_sid=$sid;task_name=$taskName;task_registered=$false;remote_configured=$false;public_endpoint='';installed_at=[DateTime]::UtcNow.ToString('o');source_commit=$manifest.source_commit}
    Write-Utf8 (Join-Path $InstallDir 'Config\installation.json') ($settings|ConvertTo-Json)
    $bytes=New-Object byte[] 32; $rng=[Security.Cryptography.RandomNumberGenerator]::Create(); try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    Write-Utf8 (Join-Path $InstallDir 'Config\secrets.env') ('JARVIS_MCP_TOKEN='+[Convert]::ToBase64String($bytes)+"`r`n")
    $values=@{JARVIS_HOME=$InstallDir;JARVIS_PROJECTS_ROOT=$ProjectsRoot;JARVIS_STATE_DIR=(Join-Path $InstallDir 'State\runtime-v1');JARVIS_DESKTOP_ENABLED='1';JARVIS_CODEX_ENABLED='0'}
    if ($codex) { $values.JARVIS_CODEX_ENABLED='1';$values.JARVIS_CODEX_EXE=$codex }
    Write-AgentEnv $InstallDir $values
    foreach ($pair in @(@('Avvia','Start.ps1'),@('Arresta','Stop.ps1'),@('Verifica','Check.ps1'),@('Configura-Cloudflare','Configure-Cloudflare.ps1'),@('Abilita-Codex','Enable-Codex.ps1'),@('Disabilita-avvio','Disable-Startup.ps1'))) {
        $cmd='@echo off'+"`r`n"+'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Scripts\'+$pair[1]+'" -HomeDir "%~dp0."'+"`r`n"+'pause'+"`r`n"
        Write-Utf8 (Join-Path $InstallDir ($pair[0]+'.cmd')) $cmd
    }
    if (-not $NoTask) {
        $action=New-ScheduledTaskAction -Execute "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -Argument ('-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "'+(Join-Path $InstallDir 'Scripts\Start.ps1')+'" -HomeDir "'+$InstallDir+'"')
        $trigger=New-ScheduledTaskTrigger -AtLogOn -User ([Security.Principal.WindowsIdentity]::GetCurrent().Name)
        $taskPrincipal=New-ScheduledTaskPrincipal -UserId $sid -LogonType Interactive -RunLevel Highest
        $options=New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew
        Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $taskPrincipal -Settings $options | Out-Null
        $taskCreated=$true;$settings.task_registered=$true
        Write-Utf8 (Join-Path $InstallDir 'Config\installation.json') ($settings|ConvertTo-Json)
    }
    if (-not $NoShortcuts) {
        $shell=New-Object -ComObject WScript.Shell
        $link=$shell.CreateShortcut((Join-Path ([Environment]::GetFolderPath('Desktop')) ($taskName+'.lnk')))
        if ($taskCreated) { $link.TargetPath="$env:SystemRoot\System32\schtasks.exe"; $link.Arguments='/Run /TN '+$taskName }
        else { $link.TargetPath=Join-Path $InstallDir 'Avvia.cmd' }
        $link.WorkingDirectory=$InstallDir;$link.Description=$names.plugin_name+' - avvio agente locale';$link.Save()
    }
    Copy-Item -LiteralPath "$PSScriptRoot\LEGGIMI.txt" -Destination (Join-Path $InstallDir 'LEGGIMI.txt')
    Write-Host ('Installazione locale pronta: '+$DeviceName+'; '+$InstallDir)
    Write-Host 'Identita nuova al primo avvio; nessuno stato, segreto o attestazione economica copiato da Desktop1.'
    if (-not $NonInteractive) {
        if ((Read-Host 'Configurare ora il tunnel Cloudflare dedicato? [s/N]') -match '^[sS]$') {
            try { & (Join-Path $InstallDir 'Scripts\Configure-Cloudflare.ps1') -HomeDir $InstallDir }
            catch { Write-Warning 'Configurazione Cloudflare non completata. Installazione locale conservata; riprendere da Configura-Cloudflare.cmd con agente fermo.' }
        }
    }
    if (-not $NoStart) {
        if ($taskCreated) { Start-ScheduledTask -TaskName $taskName }
        else { Start-Process powershell.exe -ArgumentList ('-NoProfile -ExecutionPolicy Bypass -File "'+(Join-Path $InstallDir 'Scripts\Start.ps1')+'" -HomeDir "'+$InstallDir+'"') | Out-Null }
    }
} catch {
    if ($taskCreated) { Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath (Join-Path $InstallDir 'Config')) { Write-Utf8 (Join-Path $InstallDir 'Config\install-failed.txt') 'Installazione non completata; nessuna ripetizione automatica. Conservare questa cartella per la verifica.' }
    throw
}
