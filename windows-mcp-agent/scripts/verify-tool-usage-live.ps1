param(
 [Parameter(Mandatory=$true)][string]$ExpectedMachine,
 [Parameter(Mandatory=$true)][string]$ExpectedHash,
 [Parameter(Mandatory=$true)][string]$SourceCommit,
 [string]$HomeDir='D:\Jarvis'
)
$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
$port=8765
$manifestPath=Join-Path $HomeDir 'Config\installation.json'
if(Test-Path -LiteralPath $manifestPath){$port=[int]([IO.File]::ReadAllText($manifestPath)|ConvertFrom-Json).port}
function Assert($condition,[string]$reason){if(!$condition){throw $reason}}
function Call([string]$name,[hashtable]$arguments=@{}){
 $body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name=$name;arguments=$arguments}}|ConvertTo-Json -Depth 20 -Compress
 $r=Invoke-RestMethod ('http://127.0.0.1:'+$port+'/mcp') -Method Post -ContentType 'application/json' -Headers @{Authorization=('Bearer '+$env:JARVIS_MCP_TOKEN);Accept='application/json, text/event-stream'} -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 15
 Assert (!$r.error -and !$r.result.isError) ('live_tool_failed_'+$name)
 $r.result.content[0].text|ConvertFrom-Json
}
function Usage{(Call 'tool_usage' @{machine_id=$ExpectedMachine}).usage}
function Entry($usage,[string]$name){$usage.tools|Where-Object name -eq $name|Select-Object -First 1}
$info=Call 'machine_info'
Assert ($info.machine_id -ceq $ExpectedMachine -and $info.version -eq '0.7.2') 'installed_identity_or_version_changed'
$before=Usage
$base=Entry $before 'machine_info';$wrapper=Entry $before 'task_start'
$null=Call 'machine_info'
$null=Call 'task_start' @{operation='runtime';request=@{operation='machine_info'}}
$after=Usage
$count=Entry $after 'machine_info';$wrapperAfter=Entry $after 'task_start'
Assert ($count.total -eq ($base.total+2) -and $count.succeeded -eq ($base.succeeded+2)) 'live_direct_alias_count_mismatch'
Assert ($wrapperAfter.total -eq $wrapper.total) 'live_wrapper_double_count'
Assert (!$after.persistence_warning) 'live_persistence_warning'
'PASS live_direct_alias_no_double_count'
$statePath=Join-Path $HomeDir 'State\tool-usage.json'
$saved=[IO.File]::ReadAllText($statePath)|ConvertFrom-Json
$listener=@(Get-NetTCPConnection -LocalPort $port -State Listen)
Assert ($listener.Count -eq 1) 'live_listener_not_unique'
$installed=(Get-Process -Id $listener[0].OwningProcess).Path
Assert ((Get-FileHash -LiteralPath $installed).Hash -ieq $ExpectedHash) 'installed_binary_changed'
# Same canonical updater and released binary: actual restart, without resetting state.
$candidate=Join-Path $HomeDir 'Releases\0.7.2\jarvis-mcp.exe'
Assert ($candidate -ine $installed -and (Get-FileHash -LiteralPath $candidate).Hash -ieq $ExpectedHash) 'restart_candidate_invalid'
& (Join-Path $PSScriptRoot 'update-agent.ps1') -HomeDir $HomeDir -Candidate $candidate -ExpectedHash $ExpectedHash -ExpectedMachine $ExpectedMachine -ExpectedPreviousVersion '0.7.2' -ExpectedPreviousHash $ExpectedHash -TargetVersion '0.7.2' -SourceCommit $SourceCommit
Assert $? 'live_restart_failed'
$info=Call 'machine_info'
Assert ($info.machine_id -ceq $ExpectedMachine -and $info.version -eq '0.7.2') 'post_restart_identity_changed'
$restored=Usage
Assert ($restored.started_at -ceq $saved.started_at) 'collection_start_changed'
Assert (!$restored.persistence_warning) 'post_restart_persistence_warning'
foreach($property in $saved.tools.PSObject.Properties){
 $current=Entry $restored $property.Name
 Assert ($current -and $current.total -ge $property.Value.total -and $current.succeeded -ge $property.Value.succeeded -and $current.failed -ge $property.Value.failed) ('counter_decreased_'+$property.Name)
}
'PASS live_actual_restart_persistence'
'COLLECTION_STARTED_AT '+$restored.started_at
'PASS ALL installed_tool_usage'
