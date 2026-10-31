param([Parameter(Mandatory=$true)][string]$AgentPath,[Parameter(Mandatory=$true)][string]$OutputDirectory,[switch]$RunTests)
$ErrorActionPreference='Stop'
$source=Join-Path (Split-Path $PSScriptRoot -Parent) 'installer'
$manifest=Get-Content -LiteralPath (Join-Path $source 'package.json') -Raw|ConvertFrom-Json
if ((Get-FileHash -LiteralPath $AgentPath).Hash -ine $manifest.agent_sha256) { throw 'Il binario non coincide con la release verificata.' }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Usare una nuova directory di output.' }
New-Item -ItemType Directory -Path $OutputDirectory|Out-Null
$payload=Join-Path $OutputDirectory 'Jarvis-MCP-Setup-1.0.1'
New-Item -ItemType Directory -Path $payload|Out-Null
foreach ($f in $manifest.files) {
    if ($f.path -notmatch '^[A-Za-z0-9_.-]+$') { throw 'Percorso manifest non valido.' }
    $dest=Join-Path $payload $f.path
    if ($f.path -eq 'jarvis-mcp.exe') { Copy-Item -LiteralPath $AgentPath -Destination $dest }
    else {
        $text=[IO.File]::ReadAllText((Join-Path $source $f.path)).Replace([string][char]13,'')
        [IO.File]::WriteAllText($dest,$text,(New-Object Text.UTF8Encoding($false)))
    }
    if ((Get-FileHash -LiteralPath $dest).Hash -ine $f.sha256) { throw ('Hash payload inatteso: '+$f.path) }
}
[IO.File]::WriteAllText((Join-Path $payload 'package.json'),([IO.File]::ReadAllText((Join-Path $source 'package.json')).Replace([string][char]13,'')),(New-Object Text.UTF8Encoding($false)))
if (Test-Path -LiteralPath (Join-Path $source 'COLLAUDO.txt')) {
    [IO.File]::WriteAllText((Join-Path $payload 'COLLAUDO.txt'),([IO.File]::ReadAllText((Join-Path $source 'COLLAUDO.txt')).Replace([string][char]13,'')),(New-Object Text.UTF8Encoding($false)))
}
if ($RunTests) { & (Join-Path $payload 'Test-Installer.ps1') -PackageDir $payload }
$zip=Join-Path $OutputDirectory 'Jarvis-MCP-Setup-1.0.1.zip'
Compress-Archive -Path $payload -DestinationPath $zip -CompressionLevel Optimal
[pscustomobject]@{zip=$zip;sha256=(Get-FileHash $zip).Hash;agent_version=$manifest.agent_version;installer_version=$manifest.installer_version;tests_requested=[bool]$RunTests}
