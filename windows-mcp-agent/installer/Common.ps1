# Shared installer helpers; Windows PowerShell 5.1.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
function Write-Utf8([string]$Path,[string]$Text) {
    [IO.File]::WriteAllText($Path,$Text,(New-Object Text.UTF8Encoding($false)))
}
function Get-FullPath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path) -or $Path -match '[\r\n"\x00]') { throw 'Percorso non valido.' }
    if (-not [IO.Path]::IsPathRooted($Path) -or $Path.StartsWith('\\')) { throw 'Usare un percorso locale assoluto.' }
    $full = [IO.Path]::GetFullPath($Path).TrimEnd('\')
    if ($full.Length -le 3) { throw 'Non usare la radice del disco.' }
    $check = $full
    while ($check) {
        if (Test-Path -LiteralPath $check) {
            if ((Get-Item -LiteralPath $check -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Link e junction non consentiti nei percorsi di installazione.' }
        }
        $parent = Split-Path $check -Parent
        if ($parent -eq $check) { break }; $check = $parent
    }
    return $full
}
function Test-Overlap([string]$A,[string]$B) {
    return $A.Equals($B,[StringComparison]::OrdinalIgnoreCase) -or $A.StartsWith($B+'\',[StringComparison]::OrdinalIgnoreCase) -or $B.StartsWith($A+'\',[StringComparison]::OrdinalIgnoreCase)
}
function Protect-Folder([string]$Path) {
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & "$env:SystemRoot\System32\icacls.exe" $Path /inheritance:r /grant:r ('*'+$sid+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Impossibile proteggere la cartella privata.' }
}
function Read-Install([string]$HomeDir) {
    $HomeDir = Get-FullPath $HomeDir
    $s = [IO.File]::ReadAllText((Join-Path $HomeDir 'Config\installation.json')) | ConvertFrom-Json
    if ($s.format -ne 1 -or $s.home -ine $HomeDir -or $s.computer -ine $env:COMPUTERNAME -or $s.user_sid -ne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value) { throw 'Installazione appartenente a un altro percorso, utente o computer.' }
    return $s
}
function Read-AgentEnv([string]$HomeDir) {
    $values = @{}
    foreach ($line in [IO.File]::ReadAllLines((Join-Path $HomeDir 'Config\agent.env'))) {
        if ($line -match '^([A-Z_]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
        elseif ($line.Trim() -and -not $line.StartsWith('#')) { throw 'Configurazione agente non valida.' }
    }
    return $values
}
function Write-AgentEnv([string]$HomeDir,[hashtable]$Values) {
    $text = ($Values.Keys | Sort-Object | ForEach-Object {
        if ("$($Values[$_])" -match '[\r\n\x00]') { throw 'Valore di configurazione non valido.' }
        $_+'='+$Values[$_]
    }) -join "`r`n"
    $path = Join-Path $HomeDir 'Config\agent.env'
    $temp = Join-Path $HomeDir ('Config\pending-'+[guid]::NewGuid().ToString('N'))
    Write-Utf8 $temp ($text+"`r`n")
    if (Test-Path -LiteralPath $path) {
        [IO.File]::Replace($temp,$path,(Join-Path $HomeDir ('Backups\agent-'+[guid]::NewGuid().ToString('N')+'.env')))
    } else { [IO.File]::Move($temp,$path) }
}
function Get-OwnedProcess($Record,[string]$ExpectedPath) {
    if (-not $Record) { return $null }
    $p = Get-Process -Id $Record.pid -ErrorAction SilentlyContinue
    if (-not $p) { return $null }
    if ($p.Path -ine $ExpectedPath -or $p.StartTime.ToUniversalTime().Ticks.ToString() -ne $Record.started_ticks) { throw 'PID riutilizzato o processo diverso: nessun arresto eseguito.' }
    return $p
}
function Assert-Stopped([string]$HomeDir) {
    $path = Join-Path $HomeDir 'Config\running.json'
    if (Test-Path -LiteralPath $path) {
        $r = [IO.File]::ReadAllText($path) | ConvertFrom-Json
        if (Get-OwnedProcess $r.agent (Join-Path $HomeDir 'Agent\jarvis-mcp.exe')) { throw 'Agente attivo: usare prima Arresta.cmd; verificare le attivita in corso.' }
        if ($r.tunnel -and (Get-OwnedProcess $r.tunnel (Join-Path $HomeDir 'Agent\cloudflared.exe'))) { throw 'Tunnel ancora attivo: usare Arresta.cmd.' }
    }
}
function Get-LocalToken([string]$HomeDir) {
    $line = [IO.File]::ReadAllText((Join-Path $HomeDir 'Config\secrets.env')).Trim()
    if ($line -notmatch '^JARVIS_MCP_TOKEN=([A-Za-z0-9+/=]{40,})$') { throw 'Credenziale locale non valida.' }
    return $matches[1]
}
function Invoke-LocalTool([string]$HomeDir,[string]$Name,[hashtable]$Arguments=@{}) {
    $s = Read-Install $HomeDir
    $body = @{jsonrpc='2.0';id=1;method='tools/call';params=@{name=$Name;arguments=$Arguments}} | ConvertTo-Json -Depth 12 -Compress
    $r = Invoke-RestMethod ('http://127.0.0.1:'+$s.port+'/mcp') -Method Post -ContentType 'application/json' -Headers @{Authorization=('Bearer '+(Get-LocalToken $HomeDir));Accept='application/json, text/event-stream'} -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 15
    if ($r.PSObject.Properties['error'] -or $r.result.isError) { throw ('Strumento non disponibile: '+$Name) }
    return $r.result.content[0].text | ConvertFrom-Json
}
function Get-Record($Process) {
    return @{pid=$Process.Id;started_ticks=$Process.StartTime.ToUniversalTime().Ticks.ToString()}
}
function Find-Codex {
    $pkg = @(Get-AppxPackage -Name OpenAI.Codex -ErrorAction SilentlyContinue)
    if ($pkg.Count -ne 1) { return '' }
    $exe = Join-Path $pkg[0].InstallLocation 'app\resources\codex.exe'
    if (-not (Test-Path -LiteralPath $exe)) { return '' }
    $version = & $exe --version 2>$null
    if ($LASTEXITCODE -ne 0 -or "$version" -ne 'codex-cli 0.165.0-alpha.1.2') { return '' }
    return $exe
}
function Start-Child([string]$Exe,[string]$Arguments,[string]$HomeDir,[string]$LogName) {
    $psi=New-Object Diagnostics.ProcessStartInfo
    $psi.FileName=$Exe;$psi.Arguments=$Arguments;$psi.WorkingDirectory=$HomeDir
    $psi.UseShellExecute=$false;$psi.CreateNoWindow=$true
    $psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
    foreach ($key in @($psi.EnvironmentVariables.Keys)) {
        if ($key -match '^(JARVIS_|OPENAI_|CODEX_|CF_|TUNNEL_)') { $psi.EnvironmentVariables.Remove($key) }
    }
    $psi.EnvironmentVariables['JARVIS_HOME']=$HomeDir
    $p=[Diagnostics.Process]::Start($psi)
    $stamp=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'-'+$p.Id
    $out=[IO.File]::Open((Join-Path $HomeDir ('Logs\'+$LogName+'-'+$stamp+'.log')),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    $err=[IO.File]::Open((Join-Path $HomeDir ('Logs\'+$LogName+'-error-'+$stamp+'.log')),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    return @{process=$p;out=$out;err=$err;out_task=$p.StandardOutput.BaseStream.CopyToAsync($out);err_task=$p.StandardError.BaseStream.CopyToAsync($err)}
}
function Stop-OwnedTree($Record,[string]$Path) {
    $p=Get-OwnedProcess $Record $Path
    if (-not $p) { return }
    $info=New-Object Diagnostics.ProcessStartInfo
    $info.FileName=Join-Path $env:SystemRoot 'System32\taskkill.exe'
    $info.Arguments='/PID '+$p.Id+' /T /F';$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $killer=[Diagnostics.Process]::Start($info)
    $out=$killer.StandardOutput.ReadToEndAsync();$err=$killer.StandardError.ReadToEndAsync()
    if (-not $killer.WaitForExit(10000)) { throw 'Arresto albero processi non verificato.' }
    $null=$out.Result;$null=$err.Result
    if (-not $p.WaitForExit(5000)) { throw 'Processo ancora attivo.' }
    $killer.Dispose()
}
function Assert-CloudflareInputs([string]$Team,[string]$Audience,[string]$Hostname,[string]$TunnelID) {
    if ($Team -notmatch '^https://[a-zA-Z0-9-]+\.cloudflareaccess\.com/?$') { throw 'Team domain richiesto: https://nome.cloudflareaccess.com' }
    if ($Audience -notmatch '^[a-fA-F0-9]{64}$') { throw 'AUD Access non valido: attesi 64 caratteri esadecimali.' }
    if ($Hostname -notmatch '^https://[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?\.[a-zA-Z]{2,}/?$') { throw 'Indirizzo richiesto: https://desktop2.dominio, senza percorso o parametri.' }
    if ($TunnelID -notmatch '^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$') { throw 'ID del nuovo tunnel non valido.' }
}
