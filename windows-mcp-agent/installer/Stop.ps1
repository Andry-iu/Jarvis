param([string]$HomeDir=(Split-Path $PSScriptRoot -Parent),[switch]$Force)
. "$PSScriptRoot\Common.ps1"
$s=Read-Install $HomeDir;$HomeDir=$s.home
if (-not $Force -and (Read-Host 'Arrestare questo agente e le sue elaborazioni? Digitare ARRESTA') -cne 'ARRESTA') { return }
$path=Join-Path $HomeDir 'Config\running.json'
if (-not (Test-Path -LiteralPath $path)) { Write-Host 'Nessun processo registrato.'; return }
$r=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json
if ($r.tunnel) { Stop-OwnedTree $r.tunnel (Join-Path $HomeDir 'Agent\cloudflared.exe') }
Stop-OwnedTree $r.agent (Join-Path $HomeDir 'Agent\jarvis-mcp.exe')
Write-Host 'Processi registrati arrestati. Stato e archivio conservati; nessuna attivita verra ripetuta automaticamente.'
