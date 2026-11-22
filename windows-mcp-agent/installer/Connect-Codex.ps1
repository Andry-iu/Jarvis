[CmdletBinding()]
param([string]$HomeDir,[string]$CodexExecutable='',[switch]$NonInteractive)
. "$PSScriptRoot\Common.ps1"
if (-not $HomeDir) {
    if ($NonInteractive) { throw 'HomeDir obbligatoria.' }
    $HomeDir=Read-Host 'Cartella di installazione Jarvis [D:\Jarvis]'
    if (-not $HomeDir) { $HomeDir='D:\Jarvis' }
}
$HomeDir=Get-FullPath $HomeDir
$exe=Find-Codex $CodexExecutable
if (-not $exe) { throw 'Collegamento non completato: CLI richiesta 0.165.0-alpha.1.2 non accessibile.' }
$s=Read-Install $HomeDir
if (-not (Test-Path -LiteralPath (Join-Path $HomeDir 'Config\agent.env'))) {
    & "$PSScriptRoot\Repair.ps1" -HomeDir $HomeDir -CodexExecutable $exe -NonInteractive -NoStart
    $s=Read-Install $HomeDir
}
$runningPath=Join-Path $HomeDir 'Config\running.json'
$active=$null
if (Test-Path -LiteralPath $runningPath) {
    $r=Get-Content -LiteralPath $runningPath -Raw|ConvertFrom-Json
    $active=Get-OwnedProcess $r.agent (Join-Path $HomeDir 'Agent\jarvis-mcp.exe')
    if ($active) {
        $values=Read-AgentEnv $HomeDir
        if($values.JARVIS_CODEX_ENABLED -ne '1' -or $values.JARVIS_CODEX_EXE -ine $exe) { throw 'Jarvis e gia avviato con una configurazione diversa. Terminare i lavori e usare Arresta.cmd, poi ripetere COLLEGA-CODEX. Nessun processo e stato fermato.' }
    }
}
if (-not $active -and $s.task_registered) {
    for($n=0;$n -lt 40;$n++) {
        $task=Get-ScheduledTask -TaskName $s.task_name -ErrorAction Stop
        if($task.State -ne 'Running'){break}
        Start-Sleep -Milliseconds 250
    }
    if($task.State -eq 'Running'){throw 'Avvio precedente ancora in chiusura; nessuna modifica al collegamento.'}
}
if (-not $active) {
    & "$PSScriptRoot\Enable-Codex.ps1" -HomeDir $HomeDir -CodexExecutable $exe
    if($s.task_registered -and (Get-ScheduledTask -TaskName $s.task_name).Settings.Enabled) { Start-ScheduledTask -TaskName $s.task_name }
    else { Start-Process powershell.exe -ArgumentList ('-NoProfile -ExecutionPolicy Bypass -File "'+(Join-Path $HomeDir 'Scripts\Start.ps1')+'" -HomeDir "'+$HomeDir+'"') -WindowStyle Hidden|Out-Null }
}
$identity=$null
for($n=0;$n -lt 80;$n++) {
    try { $identity=Invoke-LocalTool $HomeDir 'machine_info';break } catch { Start-Sleep -Milliseconds 250 }
}
if(-not $identity){throw 'Avvio Jarvis non confermato; verificare Logs senza ripetere elaborazioni.'}
$status=Invoke-LocalTool $HomeDir 'codex_status' @{machine_id=$identity.machine_id}
$result=[pscustomobject]@{codex_connected=($status.account_type -eq 'chatgpt');codex_version=$status.version;account_type=$status.account_type;plan_type=$status.plan_type;usage=$status.usage;inference_allowed=$status.inference_allowed;blocked_reason=$status.blocked_reason;checked_at=$status.checked_at;machine_id=$identity.machine_id}
Write-Utf8 (Join-Path $HomeDir 'Config\codex-connection.json') ($result|ConvertTo-Json -Depth 12)
if($status.account_type -ne 'chatgpt'){throw 'App Server raggiunto ma account ChatGPT non verificato. Accedere dalla CLI con codex login e il proprio account Business; nessuna API key.'}
$result|Format-List
Write-Host 'Collegamento Codex verificato senza inferenza. inference_allowed indica separatamente se le guardie consentono un turno.'
