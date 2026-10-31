# Jarvis Windows MCP - local bootstrap. Run in elevated PowerShell if elevated agent required.
$ErrorActionPreference = 'Stop'
$homeDir = if ($env:JARVIS_HOME) { $env:JARVIS_HOME } else { 'D:\Jarvis' }
$configDir = Join-Path $homeDir 'Config'
foreach ($file in @('agent.env','secrets.env')) {
    $path = Join-Path $configDir $file
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        if ($file -eq 'secrets.env') { throw "Missing local secret file: $path" }
        continue
    }
    foreach ($line in [System.IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8)) {
        $value = $line.Trim()
        if ($value -eq '' -or $value.StartsWith('#')) { continue }
        if ($line -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            throw "Invalid environment line in $file"
        }
        [System.Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
$binary = Join-Path $homeDir 'Agent\jarvis-mcp.exe'
if (-not (Test-Path -LiteralPath $binary -PathType Leaf)) { throw "Missing executable: $binary" }
& $binary
exit $LASTEXITCODE
