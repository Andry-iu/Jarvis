# Compatibility entrypoint: the update algorithm lives only in update-agent.ps1.
[CmdletBinding()]
param(
 [string]$HomeDir=$env:JARVIS_HOME,
 [Parameter(Mandatory=$true)][string]$Candidate,
 [Parameter(Mandatory=$true)][string]$ExpectedHash,
 [Parameter(Mandatory=$true)][string]$ExpectedMachine,
 [Parameter(Mandatory=$true)][string]$ExpectedPreviousVersion,
 [Parameter(Mandatory=$true)][string]$ExpectedPreviousHash,
 [Parameter(Mandatory=$true)][string]$TargetVersion,
 [Parameter(Mandatory=$true)][string]$SourceCommit,
 [switch]$RehearsalOnly
)
& (Join-Path $PSScriptRoot 'update-agent.ps1') @PSBoundParameters
exit $LASTEXITCODE
