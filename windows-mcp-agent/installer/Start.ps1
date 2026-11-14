param([string]$HomeDir=(Split-Path $PSScriptRoot -Parent))
. "$PSScriptRoot\Common.ps1"
$s=Read-Install $HomeDir;$HomeDir=$s.home
if(Test-Path (Join-Path $HomeDir 'Config\installer-scripts-pending.json')){throw 'Aggiornamento installer interrotto: riprendere INSTALLA.cmd prima dell avvio.'}
$guard=$null;$agent=$null;$tunnel=$null;$record=$null
try {
    $guard=[IO.File]::Open((Join-Path $HomeDir 'Config\runner.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    Assert-Stopped $HomeDir
    if (Get-NetTCPConnection -LocalPort $s.port -State Listen -ErrorAction SilentlyContinue) { throw 'Porta occupata; nessun processo viene sostituito.' }
    if ((Get-FileHash (Join-Path $HomeDir 'Agent\jarvis-mcp.exe')).Hash -ine $s.agent_sha256) { throw 'Hash agente diverso dal pacchetto installato.' }
    Get-ChildItem -LiteralPath (Join-Path $HomeDir 'Logs') -File -Filter '*.log' | Where-Object { $_.LastWriteTimeUtc -lt [DateTime]::UtcNow.AddDays(-30) -and -not ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) } | ForEach-Object { Remove-Item -LiteralPath $_.FullName -ErrorAction SilentlyContinue }
    $agent=Start-Child (Join-Path $HomeDir 'Agent\jarvis-mcp.exe') ('-listen 127.0.0.1:'+$s.port) $HomeDir 'agent'
    $record=@{agent=(Get-Record $agent.process);tunnel=$null;machine_id='';at=[DateTime]::UtcNow.ToString('o')}
    Write-Utf8 (Join-Path $HomeDir 'Config\running.json') ($record|ConvertTo-Json -Depth 5)
    $ready=$false
    for ($i=0;$i -lt 60;$i++) {
        if ($agent.process.HasExited) { throw 'Agente terminato durante avvio: consultare Logs.' }
        try {
            $listener=@(Get-NetTCPConnection -LocalPort $s.port -State Listen -ErrorAction SilentlyContinue)
            if ($listener.Count -eq 1 -and $listener[0].OwningProcess -eq $agent.process.Id) {
                $identity=Invoke-LocalTool $HomeDir 'machine_info'
                if ($identity.version -ne $s.agent_version) { throw 'Versione agente inattesa.' }
                $record.machine_id=$identity.machine_id;$ready=$true;break
            }
        } catch { if ($i -eq 59) { throw } }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) { throw 'Avvio locale non confermato; nessun retry automatico.' }
    if ($s.remote_configured) {
        $cf=Get-Content -LiteralPath (Join-Path $HomeDir 'Config\cloudflared.json') -Raw|ConvertFrom-Json
        if ((Get-FileHash (Join-Path $HomeDir 'Agent\cloudflared.exe')).Hash -ine $cf.sha256) { throw 'Hash cloudflared inatteso.' }
        $args='tunnel --no-autoupdate run --token-file "'+(Join-Path $HomeDir 'Config\tunnel-token.txt')+'"'
        $tunnel=Start-Child (Join-Path $HomeDir 'Agent\cloudflared.exe') $args $HomeDir 'tunnel'
        $record.tunnel=Get-Record $tunnel.process
    }
    Write-Utf8 (Join-Path $HomeDir 'Config\running.json') ($record|ConvertTo-Json -Depth 5)
    Write-Host ('Agente attivo. Dispositivo: '+$s.device_name+'; computer Windows: '+$s.computer+'; ID: '+$record.machine_id)
    if (-not $s.remote_configured) { Write-Host ('Agente locale attivo. Cloudflare facoltativo; collegamento diretto ChatGPT: '+$s.plugin_name+' in ChatGPT.') }
    $tunnelExitReported=$false
    while (-not $agent.process.WaitForExit(1000)) {
        if ($tunnel -and $tunnel.process.HasExited -and -not $tunnelExitReported) { Write-Host 'Tunnel terminato: agente locale ancora attivo; verificare Logs. Nessun riavvio automatico.'; $tunnelExitReported=$true }
    }
} finally {
    if ($tunnel) { Stop-OwnedTree $record.tunnel (Join-Path $HomeDir 'Agent\cloudflared.exe') }
    if ($agent -and -not $agent.process.HasExited) { Stop-OwnedTree $record.agent (Join-Path $HomeDir 'Agent\jarvis-mcp.exe') }
    foreach ($child in @($agent,$tunnel)) { if ($child) { try { $null=$child.out_task.Wait(3000);$null=$child.err_task.Wait(3000) } finally { $child.out.Dispose();$child.err.Dispose() } } }
    if ($guard) { $guard.Dispose() }
}
