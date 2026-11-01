param([Parameter(Mandatory=$true)][string]$AgentPath,[Parameter(Mandatory=$true)][string]$OutputDirectory,[switch]$RunTests,[switch]$NoArchive)
$ErrorActionPreference='Stop'
$source=Join-Path (Split-Path $PSScriptRoot -Parent) 'installer'
$manifest=Get-Content -LiteralPath (Join-Path $source 'package.json') -Raw|ConvertFrom-Json
if ((Get-FileHash -LiteralPath $AgentPath).Hash -ine $manifest.agent_sha256) { throw 'Il binario non coincide con la release verificata.' }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Usare una nuova directory di output.' }
New-Item -ItemType Directory -Path $OutputDirectory|Out-Null
$payload=Join-Path $OutputDirectory 'Jarvis-MCP-Setup-1.3.0'
New-Item -ItemType Directory -Path $payload|Out-Null
foreach ($f in $manifest.files) {
    if (($f.path -like '*.env' -and $f.path -ne 'setup.example.env') -or $f.path -match '^setup\.local\.env(\.|$)' -or $f.path -like '*.secret.env*') { throw 'SETUP_CONFIG_SECRET_IN_MANIFEST' }
    if ($f.path -notmatch '^[A-Za-z0-9_.-]+$') { throw 'Percorso manifest non valido.' }
    $dest=Join-Path $payload $f.path
    if ($f.path -eq 'jarvis-mcp.exe') { Copy-Item -LiteralPath $AgentPath -Destination $dest }
    else {
        $inputPath=if($f.path -eq 'update-agent.ps1'){Join-Path $PSScriptRoot 'update-agent.ps1'}else{Join-Path $source $f.path}
        $text=[IO.File]::ReadAllText($inputPath).Replace([string][char]13,'')
        [IO.File]::WriteAllText($dest,$text,(New-Object Text.UTF8Encoding($false)))
    }
    if ((Get-FileHash -LiteralPath $dest).Hash -ine $f.sha256) { throw ('Hash payload inatteso: '+$f.path) }
}
[IO.File]::WriteAllText((Join-Path $payload 'package.json'),([IO.File]::ReadAllText((Join-Path $source 'package.json')).Replace([string][char]13,'')),(New-Object Text.UTF8Encoding($false)))
if (Test-Path -LiteralPath (Join-Path $source 'COLLAUDO.txt')) {
    [IO.File]::WriteAllText((Join-Path $payload 'COLLAUDO.txt'),([IO.File]::ReadAllText((Join-Path $source 'COLLAUDO.txt')).Replace([string][char]13,'')),(New-Object Text.UTF8Encoding($false)))
}
if ($RunTests) { & (Join-Path $payload 'Test-SetupSettings.ps1') -PackageDir $payload; & (Join-Path $payload 'Test-Installer.ps1') -PackageDir $payload }
if($NoArchive){[pscustomobject]@{payload=$payload;agent_version=$manifest.agent_version;installer_version=$manifest.installer_version;tests_requested=[bool]$RunTests};return}
$zip=Join-Path $OutputDirectory 'Jarvis-MCP-Setup-1.3.0.zip'
Compress-Archive -Path $payload -DestinationPath $zip -CompressionLevel Optimal
[pscustomobject]@{zip=$zip;sha256=(Get-FileHash $zip).Hash;agent_version=$manifest.agent_version;installer_version=$manifest.installer_version;tests_requested=[bool]$RunTests}
