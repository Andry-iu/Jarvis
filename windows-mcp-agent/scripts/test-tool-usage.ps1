param(
 [Parameter(Mandatory=$true)][string]$Candidate,
 [Parameter(Mandatory=$true)][string]$ExpectedHash,
 [string]$TempRoot='D:\Jarvis\Temp'
)
$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
$testHome=Join-Path $TempRoot ('tool-usage-suite-'+[guid]::NewGuid().ToString('N'))
$script:owned=$null
$token=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
$listener=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,0)
$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
function Assert($condition,[string]$message){if(!$condition){throw $message}}
function Rpc([string]$method,[hashtable]$params){
 $body=@{jsonrpc='2.0';id=1;method=$method;params=$params}|ConvertTo-Json -Depth 20 -Compress
 Invoke-RestMethod ('http://127.0.0.1:'+$port+'/mcp') -Method Post -ContentType 'application/json' -Headers @{Authorization=('Bearer '+$token);Accept='application/json, text/event-stream'} -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 15
}
function Call([string]$name,[hashtable]$arguments=@{},[switch]$ExpectError){
 $r=Rpc 'tools/call' @{name=$name;arguments=$arguments}
 if($ExpectError){Assert ($r.error -or $r.result.isError) ('expected_error_'+$name);return}
 Assert (!$r.error -and !$r.result.isError) ('tool_failed_'+$name)
 $r.result.content[0].text|ConvertFrom-Json
}
function Row([string]$name){
 $v=Call 'tool_usage' @{machine_id=$script:machine;query=$name}
 Assert (@($v.usage.tools).Count -eq 1) 'filter_not_exact'
 Assert ($v.usage.tools[0].name -ceq $name) 'filter_wrong_name'
 $v.usage.tools[0]
}
function StartOwned{
 $psi=New-Object Diagnostics.ProcessStartInfo
 $psi.FileName=$Candidate;$psi.Arguments='-listen 127.0.0.1:'+$port;$psi.WorkingDirectory=$testHome
 $psi.UseShellExecute=$false;$psi.CreateNoWindow=$true
 $psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
 foreach($key in @($psi.EnvironmentVariables.Keys)){if($key -match '^(JARVIS_|OPENAI_|CODEX_|CF_|TUNNEL_)'){$psi.EnvironmentVariables.Remove($key)}}
 $psi.EnvironmentVariables['JARVIS_HOME']=$testHome
 $script:owned=[Diagnostics.Process]::Start($psi)
 $script:stdout=$owned.StandardOutput.ReadToEndAsync();$script:stderr=$owned.StandardError.ReadToEndAsync()
 $ready=$false
 for($i=0;$i -lt 80;$i++){
  Assert (!$owned.HasExited) 'candidate_exited'
  try{if((Invoke-RestMethod ('http://127.0.0.1:'+$port+'/health') -TimeoutSec 1).version -eq '0.7.2'){$ready=$true;break}}catch{}
  Start-Sleep -Milliseconds 150
 }
 Assert $ready 'candidate_health_timeout'
}
function StopOwned{
 if(!$script:owned){return}
 if(!$owned.HasExited){Stop-Process -Id $owned.Id -Force;Assert ($owned.WaitForExit(10000)) 'candidate_stop_timeout'}
 $null=$stdout.Result;$null=$stderr.Result;$owned.Dispose();$script:owned=$null
}
try{
 Assert ((Get-FileHash -LiteralPath $Candidate -Algorithm SHA256).Hash -ieq $ExpectedHash) 'candidate_hash_mismatch'
 New-Item -ItemType Directory -Path (Join-Path $testHome 'Config'),(Join-Path $testHome 'Projects') -Force|Out-Null
 $utf8=New-Object Text.UTF8Encoding($false)
 $config='JARVIS_PROJECTS_ROOT='+(Join-Path $testHome 'Projects')+"`nJARVIS_CODEX_ENABLED=0`n"
 [IO.File]::WriteAllText((Join-Path $testHome 'Config\agent.env'),$config,$utf8)
 [IO.File]::WriteAllText((Join-Path $testHome 'Config\secrets.env'),('JARVIS_MCP_TOKEN='+$token),$utf8)
 StartOwned
 $info=Call 'machine_info';$script:machine=$info.machine_id
 Assert ($info.version -eq '0.7.2') 'wrong_version'
 $catalog=Rpc 'tools/list' @{}
 Assert (@($catalog.result.tools|Where-Object name -eq 'tool_usage').Count -eq 1) 'catalog_missing_tool_usage'
 'PASS catalog'
 $before=Row 'machine_info'
 $null=Call 'task_start' @{operation='runtime';request=@{operation='machine_info'}}
 $after=Row 'machine_info'
 Assert ($after.total -eq ($before.total+1) -and $after.succeeded -eq ($before.succeeded+1)) 'alias_not_counted_once'
 Assert ((Row 'task_start').total -eq 0) 'alias_wrapper_counted'
 'PASS compatibility_no_double_count'
 $null=Call 'agent_status'
 Assert ((Row 'agent_status').total -eq 1 -and (Row 'agent_status').succeeded -eq 1) 'direct_count_failed'
 'PASS direct_success'
 Call 'file_read' @{machine_id='intentionally-wrong';project='.';path='PRIVATE_SENTINEL_PATH'} -ExpectError
 $failed=Row 'file_read'
 Assert ($failed.total -eq 1 -and $failed.failed -eq 1) 'failed_count_missing'
 'PASS failed_response'
 Assert ((Row 'codex_turn').total -eq 0) 'unused_tool_not_zero'
 Call 'tool_usage' @{machine_id=$machine;query='nonexistent_tool_072'} -ExpectError
 'PASS exact_filter_unused_unknown'
 $all=Call 'tool_usage' @{machine_id=$machine}
 $rows=@($all.usage.tools)
 for($i=1;$i -lt $rows.Count;$i++){Assert ($rows[$i-1].total -ge $rows[$i].total) 'ranking_not_sorted'}
 $self=$rows|Where-Object name -eq 'tool_usage'
 Assert ($self.unfinished -eq 1) 'current_read_not_unfinished'
 Assert (!$all.usage.persistence_warning) 'persistence_warning'
 'PASS ranking_and_current_call'
 StopOwned
 $statePath=Join-Path $testHome 'State\tool-usage.json'
 $raw=[IO.File]::ReadAllText($statePath)
 Assert (!$raw.Contains('PRIVATE_SENTINEL_PATH') -and !$raw.Contains($token)) 'private_data_in_usage'
 $saved=$raw|ConvertFrom-Json
 Assert ($saved.tools.machine_info.total -eq $after.total) 'saved_count_mismatch'
 StartOwned
 $restart=Row 'machine_info'
 Assert ($restart.total -eq $after.total -and $restart.succeeded -eq $after.succeeded) 'restart_lost_counts'
 'PASS restart_persistence_and_no_arguments'
 StopOwned
 [IO.File]::WriteAllText($statePath,'invalid-state-preserve',$utf8)
 StartOwned
 $null=Call 'agent_status'
 Call 'tool_usage' @{machine_id=$machine} -ExpectError
 StopOwned
 Assert ([IO.File]::ReadAllText($statePath) -ceq 'invalid-state-preserve') 'corrupt_state_overwritten'
 'PASS corrupt_state_preserved_operations_continue'
 'PASS ALL tool_usage_suite'
}finally{
 StopOwned
 if(Test-Path -LiteralPath $testHome){Remove-Item -LiteralPath $testHome -Recurse -Force}
 Assert (!(Test-Path -LiteralPath $testHome)) 'suite_cleanup_failed'
 'PASS suite_cleanup'
}
