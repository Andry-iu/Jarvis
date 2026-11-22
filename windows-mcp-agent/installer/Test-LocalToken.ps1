# Isolated compatibility checks: fixture values only, never read an installed Jarvis configuration.
. "$PSScriptRoot\Common.ps1"
$root = Join-Path ([IO.Path]::GetTempPath()) ('jarvis-token-test-' + [guid]::NewGuid().ToString('N'))
$config = Join-Path $root 'Config'
New-Item -ItemType Directory -Path $config -Force | Out-Null
$path = Join-Path $config 'secrets.env'
$utf8 = New-Object Text.UTF8Encoding($false)
$legacy = 'legacy-token-0123456789abcdef0123'
$modern = [Convert]::ToBase64String((0..31 | ForEach-Object { [byte]$_ }))
$passed = 0
function Assert-Equal([string]$Actual,[string]$Expected,[string]$Label) {
    if ($Actual -cne $Expected) { throw ('FAILED ' + $Label) }
    $script:passed++
}
function Assert-Rejected([string]$Label) {
    $failed = $false
    try { Get-LocalToken $root | Out-Null } catch {
        $failed = $true
        if ($_.Exception.Message.Contains($legacy) -or $_.Exception.Message.Contains($modern)) {
            throw 'Secret value was included in an error.'
        }
    }
    if (-not $failed) { throw ('FAILED expected rejection: ' + $Label) }
    $script:passed++
}
try {
    [IO.File]::WriteAllText($path, 'JARVIS_MCP_TOKEN=' + $modern + "`r`n", $utf8)
    Assert-Equal (Get-LocalToken $root) $modern 'fresh-base64-token'
    $bom = [string][char]0xFEFF
    [IO.File]::WriteAllText($path, $bom + 'JARVIS_MCP_TOKEN=' + $legacy + "`r`n" +
        $bom + 'JARVIS_CF_APP_AUD=' + ('a' * 64) + "`r`n", $utf8)
    Assert-Equal (Get-LocalToken $root) $legacy 'legacy-token-two-bom-lines'
    [IO.File]::WriteAllText($path, "# comment`r`nJARVIS_CF_APP_AUD=" + ('b' * 64) +
        "`r`nJARVIS_MCP_TOKEN=" + $legacy + "`r`n", $utf8)
    Assert-Equal (Get-LocalToken $root) $legacy 'additional-key-and-comments'
    [IO.File]::WriteAllText($path, 'JARVIS_MCP_TOKEN=too-short', $utf8)
    Assert-Rejected 'too-short'
    [IO.File]::WriteAllText($path, 'JARVIS_MCP_TOKEN=' + $legacy + "`r`n" +
        'JARVIS_MCP_TOKEN=' + $legacy, $utf8)
    Assert-Rejected 'duplicate'
    [IO.File]::WriteAllText($path, 'JARVIS_MCP_TOKEN=' + $legacy + "`r`ninvalid-line", $utf8)
    Assert-Rejected 'malformed'
    [IO.File]::WriteAllText($path, 'JARVIS_MCP_TOKEN=' + $legacy + ' ', $utf8)
    Assert-Equal (Get-LocalToken $root) $legacy 'trailing-whitespace-normalized'
    Write-Host ('PASS ' + $passed + ' isolated token compatibility checks')
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
