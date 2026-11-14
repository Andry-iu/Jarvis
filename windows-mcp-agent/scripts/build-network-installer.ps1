param([Parameter(Mandatory=$true)][string]$Candidate,[Parameter(Mandatory=$true)][string]$SourceCommit,[Parameter(Mandatory=$true)][string]$OutputDirectory,[string]$SourceDirectory=(Split-Path $PSScriptRoot -Parent))
$ErrorActionPreference='Stop'
if($SourceCommit -notmatch '^[a-f0-9]{40}$'){throw 'SourceCommit completo obbligatorio'}
$package=Join-Path $OutputDirectory 'Jarvis-Agent-Setup-2.1.0'
if(Test-Path $package){throw 'Destinazione pacchetto gia esistente'}
New-Item -ItemType Directory -Force $package|Out-Null
$files=@{}
Get-ChildItem (Join-Path $SourceDirectory 'installer') -File|Where-Object {$_.Extension -in @('.ps1','.cjs') -and $_.Name -notlike 'Test-*'}|ForEach-Object {$files[$_.Name]=$_.FullName}
Get-ChildItem (Join-Path $SourceDirectory 'installer-network') -File|ForEach-Object {$files[$_.Name]=$_.FullName}
$files['update-agent.ps1']=Join-Path $PSScriptRoot 'update-agent.ps1';$files['jarvis-mcp.exe']=$Candidate
foreach($name in $files.Keys){Copy-Item -LiteralPath $files[$name] -Destination (Join-Path $package $name)}
$list=@(Get-ChildItem $package -File|Sort-Object Name|ForEach-Object {@{path=$_.Name;size_bytes=$_.Length;sha256=(Get-FileHash $_.FullName).Hash.ToLowerInvariant()}})
$manifest=@{installer_version='2.1.0';agent_version='0.8.1';agent_sha256=(Get-FileHash $Candidate).Hash.ToLowerInvariant();source_commit=$SourceCommit;files=$list}
[IO.File]::WriteAllText((Join-Path $package 'package.json'),($manifest|ConvertTo-Json -Depth 10),(New-Object Text.UTF8Encoding($false)))
$zip=Join-Path $OutputDirectory 'Jarvis-Agent-Setup-2.1.0.zip'
Compress-Archive -LiteralPath $package -DestinationPath $zip
@{package=$package;zip=$zip;sha256=(Get-FileHash $zip).Hash.ToLowerInvariant();size_bytes=(Get-Item $zip).Length}|ConvertTo-Json -Compress
