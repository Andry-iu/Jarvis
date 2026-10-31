param([string]$HomeDir=(Split-Path $PSScriptRoot -Parent))
. "$PSScriptRoot\Common.ps1"
$s=Read-Install $HomeDir;Assert-Stopped $HomeDir
$exe=Find-Codex
if (-not $exe) { throw 'Codex non installato o versione non compatibile. Richiesta CLI 0.165.0-alpha.1.2 del pacchetto OpenAI.Codex; nessun download o upgrade automatico.' }
$values=Read-AgentEnv $HomeDir;$values.JARVIS_CODEX_ENABLED='1';$values.JARVIS_CODEX_EXE=$exe
Write-AgentEnv $HomeDir $values
Write-Host 'Ponte Codex abilitato per il prossimo avvio. Accedere a Codex con ChatGPT Business; nessuna API key o attestazione economica copiata. Un saldo sconosciuto puo ancora bloccare i turni.'
