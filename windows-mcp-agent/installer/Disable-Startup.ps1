param([string]$HomeDir=(Split-Path $PSScriptRoot -Parent))
. "$PSScriptRoot\Common.ps1"
$s=Read-Install $HomeDir
if (-not $s.task_registered) { Write-Host 'Avvio automatico non registrato.';return }
$task=Get-ScheduledTask -TaskName $s.task_name -ErrorAction SilentlyContinue
if ($task) {
    $expected='-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "'+(Join-Path $s.home 'Scripts\Start.ps1')+'" -HomeDir "'+$s.home+'"'
    if (@($task.Actions).Count -ne 1 -or $task.Actions[0].Arguments -cne $expected) { throw 'Task modificato: nessuna rimozione automatica.' }
    Disable-ScheduledTask -TaskName $s.task_name|Out-Null
}
Write-Host 'Avvio automatico disabilitato. Processi correnti, configurazione, archivio e backup conservati.'
