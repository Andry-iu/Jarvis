$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../desktop_extensions.ps1')
if (-not ('JarvisWindowNative' -as [type])) { throw 'Desktop native helper not compiled' }

# Test the worker's wait loop without sending input to the desktop.
function Refuse($code) { throw $code }
function RequireForeground($h) {
    if (!$script:isForeground) { throw 'foreground_changed' }
}
function TestSequenceCondition($h, $targetPid, $step) {
    $script:probes++
    return $script:probes -ge $script:readyAt
}
function Start-Sleep {
    param([int]$Milliseconds)
    $script:pauses++
    if ($Milliseconds -lt 1 -or $Milliseconds -gt 20) { throw 'invalid poll interval' }
    if ($script:loseForeground) { $script:isForeground = $false }
    Microsoft.PowerShell.Utility\Start-Sleep -Milliseconds $Milliseconds
}
function AssertFailure($expected) {
    try {
        WaitSequenceCondition ([IntPtr]1) 42 @{condition = 'selected'}
    } catch {
        if ($_.Exception.Message -ceq $expected) { return }
        throw
    }
    throw "Expected $expected"
}

$script:isForeground = $true
$script:loseForeground = $false
$script:probes = 0
$script:pauses = 0
$script:readyAt = 1
WaitSequenceCondition ([IntPtr]1) 42 @{condition = 'editable_focus'}
if ($script:probes -ne 1 -or $script:pauses -ne 0) { throw 'Ready condition must return without waiting' }

$script:probes = 0
$script:readyAt = 2
WaitSequenceCondition ([IntPtr]1) 42 @{condition = 'selected'}
if ($script:probes -ne 2 -or $script:pauses -ne 1) { throw 'Pending condition must wait for a new probe' }

$script:probes = 0
$script:readyAt = [int]::MaxValue
AssertFailure 'sequence_condition_timeout_selected'
if ($script:probes -lt 1) { throw 'Timeout never checked the condition' }

$script:probes = 0
$script:loseForeground = $true
AssertFailure 'foreground_changed'
if ($script:probes -ne 1) { throw 'Probed after loss of foreground' }

Write-Output 'UI sequence readiness wait tests passed'
