param([string]$HomeDir=(Split-Path $PSScriptRoot -Parent),[string]$CodexExecutable='')
. "$PSScriptRoot\Common.ps1"
$s=Read-Install $HomeDir;Assert-Stopped $HomeDir
$exe=Find-Codex $CodexExecutable
if (-not $exe) { throw 'Codex non accessibile, assente o non compatibile. Richiesta CLI ufficiale 0.165.0-alpha.1.2; nessun permesso, download o upgrade viene modificato automaticamente.' }
$values=Read-AgentEnv $HomeDir;$values.JARVIS_CODEX_ENABLED='1';$values.JARVIS_CODEX_EXE=$exe
Write-AgentEnv $HomeDir $values
Write-Host 'Ponte Codex abilitato per il prossimo avvio. Accedere a Codex con ChatGPT Business; nessuna API key o attestazione economica copiata. Un saldo sconosciuto puo ancora bloccare i turni.'
