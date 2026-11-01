[CmdletBinding()]
param([string]$HomeDir,[string]$CodexExecutable='',[switch]$NonInteractive,[switch]$NoTask,[switch]$NoShortcuts,[switch]$NoStart)
. "$PSScriptRoot\Common.ps1"
$principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Avviare RIPRENDI.cmd e consentire la richiesta amministratore.' }
if (-not $HomeDir) {
    if ($NonInteractive) { throw 'HomeDir obbligatoria.' }
    $HomeDir=Read-Host 'Cartella della precedente installazione incompleta [D:\Jarvis]'
    if (-not $HomeDir) { $HomeDir='D:\Jarvis' }
}
$HomeDir=Get-FullPath $HomeDir
$intent=Join-Path $HomeDir 'Jarvis-Install-Intent.json'
if(Test-Path (Join-Path $HomeDir 'Config\installation.json')){$s=Read-Install $HomeDir}
elseif(Test-Path $intent){
    $s=Get-Content $intent -Raw|ConvertFrom-Json
    if($s.format -ne 1 -or $s.home -ine $HomeDir -or $s.computer -ine $env:COMPUTERNAME -or $s.user_sid -ne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value){throw 'Tentativo appartenente ad altro contesto.'}
}else{throw 'Nessun tentativo riconosciuto.'}
$ownAttempt=($s.installer_version -in @('1.1.0','1.1.1','1.2.0','1.2.1','1.2.2') -and (Test-Path $intent))
if($ownAttempt -and (Test-Path (Join-Path $HomeDir 'Config\install-complete.json'))){throw 'Installazione completa: usare Setup, senza ripristinare stato.'}
$oldTaskXml=$null
$null=Get-DeviceNames $s.device_name
if (($s.installer_version -ne '1.0.1' -and -not $ownAttempt) -or $s.agent_version -ne '0.6.1' -or (-not $ownAttempt -and $s.task_registered) -or $s.remote_configured) { throw 'Ripresa limitata alla prima installazione 1.0.1 fallita prima della configurazione.' }
if (-not $ownAttempt -and (-not (Test-Path -LiteralPath (Join-Path $HomeDir 'Config\install-failed.txt')) -or (Test-Path -LiteralPath (Join-Path $HomeDir 'Config\agent.env')))) { throw 'Installazione non riconosciuta come incompleta nel rilevamento Codex; nessuna modifica.' }
if ($s.task_name -notmatch '^Jarvis-[A-Za-z0-9_-]+-[a-f0-9]{12}$') { throw 'Identificativo task non valido.' }
$priorTask=Get-ScheduledTask -TaskName $s.task_name -ErrorAction SilentlyContinue
if($priorTask){
    try{$taskSid=(New-Object Security.Principal.SecurityIdentifier($priorTask.Principal.UserId)).Value}catch{try{$taskSid=(New-Object Security.Principal.NTAccount($priorTask.Principal.UserId)).Translate([Security.Principal.SecurityIdentifier]).Value}catch{throw 'Account task non riconosciuto.'}}
    $expected='-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "'+(Join-Path $HomeDir 'Scripts\Start.ps1')+'" -HomeDir "'+$HomeDir+'"'
    if(-not $ownAttempt -or $priorTask.State -eq 'Running' -or @($priorTask.Actions).Count -ne 1 -or $priorTask.Actions[0].Arguments -ine $expected -or $taskSid -ine $s.user_sid -or $priorTask.Actions[0].Execute -ine "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"){throw 'Task non riconosciuto o attivo: nessuna modifica.'}
    $oldTaskXml=Export-ScheduledTask -TaskName $s.task_name
}
if (Get-NetTCPConnection -LocalPort $s.port -State Listen -ErrorAction SilentlyContinue) { throw 'Porta occupata: nessun processo viene arrestato.' }
if (-not $ownAttempt -and (Get-FileHash (Join-Path $HomeDir 'Agent\jarvis-mcp.exe')).Hash -ine '9FF4A136951F464925CC55B96E1A968174204199FA9C7FF681B998B3542742C1') { throw 'Binario precedente non riconosciuto.' }
if(-not $ownAttempt){$null=Get-LocalToken $HomeDir}
$configAllowed=@('installation.json','secrets.env','install-failed.txt');if($ownAttempt){$configAllowed+=@('agent.env','installation.json.previous')}
foreach ($f in @(if(Test-Path (Join-Path $HomeDir 'Config')){Get-ChildItem -LiteralPath (Join-Path $HomeDir 'Config') -Force})) {
    if ($f.PSIsContainer -or $f.Name -notin $configAllowed) { throw 'Configurazione aggiuntiva presente: ripresa automatica rifiutata.' }
}
$allowed=@('Agent','Scripts','Config','State','Logs','Backups','Projects')
foreach ($item in Get-ChildItem -LiteralPath $HomeDir -Force) {
    if($ownAttempt -and -not $item.PSIsContainer -and $item.Name -in @('Jarvis-Install-Intent.json','Avvia.cmd','Arresta.cmd','Verifica.cmd','Configura-Cloudflare.cmd','Abilita-Codex.cmd','Disabilita-avvio.cmd','LEGGIMI.txt')){continue}
    if (-not $item.PSIsContainer -or $item.Name -notin $allowed) { throw 'File o cartelle aggiunti: ripresa automatica rifiutata.' }
    $null=Get-FullPath $item.FullName
    if ($item.Name -notin @('Agent','Scripts','Config') -and @(Get-ChildItem -LiteralPath $item.FullName -Force).Count) { throw 'Stato, progetti o backup gia presenti: conservarli e verificare prima della ripresa.' }
}
foreach ($dir in @('Agent','Scripts')) {
    foreach ($f in @(if(Test-Path (Join-Path $HomeDir $dir)){Get-ChildItem -LiteralPath (Join-Path $HomeDir $dir) -Force})) {
        $null=Get-FullPath $f.FullName
        if ($f.PSIsContainer) { throw 'Struttura precedente inattesa.' }
        $manifest=Get-Content "$PSScriptRoot\package.json" -Raw|ConvertFrom-Json
        if($f.Name -notin @($manifest.files.path)){throw 'File aggiuntivo nella copia parziale: nessuna ripresa distruttiva.'}
    }
}
$projects=Get-FullPath $s.projects_root
$snapshot=Join-Path (Split-Path $HomeDir -Parent) ((Split-Path $HomeDir -Leaf)+'-incompleta-'+[guid]::NewGuid().ToString('N'))
$moved=$false;$removedTask=$false
try {
    if($oldTaskXml){Unregister-ScheduledTask -TaskName $s.task_name -Confirm:$false;$removedTask=$true}
    [IO.Directory]::Move($HomeDir,$snapshot);$moved=$true
    Write-Host ('Copia incompleta conservata integralmente in: '+$snapshot)
    & "$PSScriptRoot\Install.ps1" -DeviceName $s.device_name -InstallDir $HomeDir -ProjectsRoot $projects -Port $s.port -CodexExecutable $CodexExecutable -NonInteractive -NoTask:$NoTask -NoShortcuts:$NoShortcuts -NoStart
    Write-Utf8 (Join-Path $HomeDir 'Config\recovery.json') (@{from_version=$s.installer_version;backup=$snapshot;at=[DateTime]::UtcNow.ToString('o');note='Nessuno stato operativo precedente; credenziale della copia fallita conservata nel backup.'}|ConvertTo-Json)
    Write-Host 'Installazione ripresa. La copia precedente resta nel backup; configurare Cloudflare dal comando dedicato se necessario.'
} catch {
    if(-not $moved){if($removedTask){Register-ScheduledTask -TaskName $s.task_name -Xml $oldTaskXml|Out-Null};throw}
    if (Test-Path -LiteralPath $HomeDir) {
        if (Test-Path -LiteralPath (Join-Path $HomeDir 'Config\installation.json')) {
            $newSettings=Read-Install $HomeDir
            if ($newSettings.installer_version -in @('1.1.0','1.1.1','1.2.0','1.2.1','1.2.2') -and $newSettings.task_registered) { Unregister-ScheduledTask -TaskName $newSettings.task_name -Confirm:$false -ErrorAction SilentlyContinue }
        }
        $failed=$HomeDir+'-ripresa-fallita-'+[guid]::NewGuid().ToString('N')
        [IO.Directory]::Move($HomeDir,$failed)
    }
    [IO.Directory]::Move($snapshot,$HomeDir)
    if($removedTask){Register-ScheduledTask -TaskName $s.task_name -Xml $oldTaskXml|Out-Null}
    throw 'Ripresa non completata; cartella originale ripristinata e nuova prova conservata. Nessuna ripetizione automatica.'
}
if (-not $NoStart) {
    $ready=Read-Install $HomeDir
    if ($ready.task_registered) { Start-ScheduledTask -TaskName $ready.task_name }
    else { Start-Process powershell.exe -ArgumentList ('-NoProfile -ExecutionPolicy Bypass -File "'+(Join-Path $HomeDir 'Scripts\Start.ps1')+'" -HomeDir "'+$HomeDir+'"')|Out-Null }
}
