param(
 [Parameter(Mandatory=$true)][string]$Candidate,
 [Parameter(Mandatory=$true)][string]$TestExecutable,
 [Parameter(Mandatory=$true)][string]$ProductionToken,
 [string]$TempRoot=$env:TEMP
)
$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue'
$suite=Join-Path $TempRoot ('admin-e2e-'+[guid]::NewGuid().ToString('N'))
$owned=@();$ports=@{}
function Assert($condition,[string]$reason){if(!$condition){throw $reason}}
function Port{ $l=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,0);$l.Start();$p=$l.LocalEndpoint.Port;$l.Stop();return $p }
function Rpc([int]$port,[string]$token,[string]$name,[hashtable]$arguments=@{},[switch]$ExpectError){
 $body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name=$name;arguments=$arguments}}|ConvertTo-Json -Depth 20 -Compress
 $r=Invoke-RestMethod ('http://127.0.0.1:'+$port+'/mcp') -Method Post -ContentType 'application/json' -Headers @{Authorization=('Bearer '+$token);Accept='application/json, text/event-stream'} -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 10
 if($ExpectError){Assert ($r.error -or $r.result.isError) ('expected_error_'+$name);return}
 Assert (!$r.error -and !$r.result.isError) ('tool_failed_'+$name)
 $r.result.content[0].text|ConvertFrom-Json
}
function Prepare([string]$name,[string]$exe){
 $homeDir=Join-Path $suite $name
 New-Item -ItemType Directory -Force (Join-Path $homeDir 'Agent'),(Join-Path $homeDir 'Config'),(Join-Path $homeDir 'Projects')|Out-Null
 $target=Join-Path $homeDir 'Agent\jarvis-mcp.exe';Copy-Item -LiteralPath $exe -Destination $target
 $token=[guid]::NewGuid().ToString('N');$utf8=New-Object Text.UTF8Encoding($false)
 $values=@(('JARVIS_HOME='+$homeDir),('JARVIS_STATE_DIR='+(Join-Path $homeDir 'State\runtime-v1')),('JARVIS_PROJECTS_ROOT='+(Join-Path $homeDir 'Projects')),'JARVIS_CODEX_ENABLED=0','JARVIS_DESKTOP_ENABLED=1','JARVIS_CF_TEAM_DOMAIN=','JARVIS_CF_APP_AUD=')
 [IO.File]::WriteAllText((Join-Path $homeDir 'Config\agent.env'),($values -join [Environment]::NewLine),$utf8)
 [IO.File]::WriteAllText((Join-Path $homeDir 'Config\secrets.env'),('JARVIS_MCP_TOKEN='+$token),$utf8)
 @{home=$homeDir;exe=$target;token=$token;port=(Port)}
}
function WaitInfo($x){
 for($i=0;$i -lt 100;$i++){
  try{$info=Rpc $x.port $x.token 'machine_info';if($info.version -eq '0.7.3'){return $info}}catch{}
  Start-Sleep -Milliseconds 150
 }
 throw 'isolated_agent_health_timeout'
}
function StartOwned([string]$exe,[string[]]$arguments,[string]$dir){
 $psi=New-Object Diagnostics.ProcessStartInfo;$psi.FileName=$exe;$psi.Arguments=($arguments|ForEach-Object {'"'+$_+'"'}) -join ' ';$psi.WorkingDirectory=$dir
 $psi.UseShellExecute=$false;$psi.CreateNoWindow=$true
 foreach($key in @($psi.EnvironmentVariables.Keys)){if($key -match '^(JARVIS_|OPENAI_|CODEX_|CF_|TUNNEL_)'){$psi.EnvironmentVariables.Remove($key)}}
 $psi.EnvironmentVariables['JARVIS_HOME']=$dir
 [Diagnostics.Process]::Start($psi)
}
try{
 New-Item -ItemType Directory -Path $suite|Out-Null
 $admin=Prepare 'admin' $Candidate
 $p=StartOwned $admin.exe @('-listen',('127.0.0.1:'+$admin.port)) $admin.home;$owned+=$p.Id
 $info=WaitInfo $admin
 Assert ($info.privileges.elevated -eq $true -and $info.privileges.pid -eq $p.Id) 'admin_token_status_wrong'
 $state=Rpc $admin.port $admin.token 'agent_status';Assert ($state.privileges.elevated -eq $true) 'legacy_status_missing_privileges'
 $req=[guid]::NewGuid().ToString('N')
 $v=Rpc $admin.port $admin.token 'agent_restart_admin' @{machine_id=$info.machine_id;request_id=$req}
 Assert ($v.state -eq 'already_elevated' -and !$v.restarted) 'admin_noop_failed'
 $v2=Rpc $admin.port $admin.token 'task_start' @{operation='runtime';request=@{operation='agent_restart_admin';machine_id=$info.machine_id;request_id=$req}}
 Assert ($v2.state -eq 'already_elevated') 'compatibility_receipt_failed'
 Assert ((Rpc $admin.port $admin.token 'machine_info').privileges.pid -eq $p.Id) 'admin_restarted_unnecessarily'
 'PASS elevated_status_noop_compatibility_receipt'
 $standard=Prepare 'standard' $TestExecutable
 $production=Rpc 8765 $ProductionToken 'machine_info'
 $notify=Join-Path $standard.home 'elevation-notify.txt'
 $launch=Rpc 8765 $ProductionToken 'process_start' @{machine_id=$production.machine_id;request_id=[guid]::NewGuid().ToString('N');executable=$standard.exe;args=@('-listen',('127.0.0.1:'+$standard.port),'-test-elevation-notify',$notify);working_directory=$standard.home}
 Assert ($launch.elevated -eq $false) 'standard_fixture_not_standard';$owned+=$launch.pid
 $info=WaitInfo $standard;$oldPid=$info.privileges.pid
 Assert ($info.privileges.elevated -eq $false) 'standard_token_reported_admin'
 $diag=Rpc $standard.port $standard.token 'task_start' @{name='busy-restart-check';duration_seconds=2}
 Rpc $standard.port $standard.token 'agent_restart_admin' @{machine_id=$info.machine_id;request_id=[guid]::NewGuid().ToString('N')} -ExpectError
 for($i=0;$i -lt 40;$i++){if((Rpc $standard.port $standard.token 'agent_status').state -ne 'running'){break};Start-Sleep -Milliseconds 100}
 Assert (!(Test-Path $notify)) 'busy_request_launched_elevation'
 $request=[guid]::NewGuid().ToString('N')
 $v=Rpc $standard.port $standard.token 'agent_restart_admin' @{machine_id=$info.machine_id;request_id=$request}
 Assert ($v.state -eq 'awaiting_elevation' -and !$v.restarted) 'restart_ack_incorrect'
 Rpc $standard.port $standard.token 'task_start' @{name='must-not-start';duration_seconds=1} -ExpectError
 Assert ((Rpc $standard.port $standard.token 'machine_info').privileges.pid -eq $oldPid) 'original_stopped_before_elevated_helper'
 'PASS standard_status_busy_refusal_pending_gate_original_preserved'
 for($i=0;$i -lt 40 -and !(Test-Path $notify);$i++){Start-Sleep -Milliseconds 50}
 Assert ((Get-Content $notify -Raw) -ceq $request) 'elevation_notification_missing'
 # The test-only launcher pauses before UAC. This already-elevated test runner
 # supplies the helper; all real token/identity/handoff/restart checks are native.
 $helper=StartOwned $standard.exe @('-restart-admin-helper',$request,'-restart-home',$standard.home) $standard.home;$owned+=$helper.Id
 $verified=$false
 for($i=0;$i -lt 150;$i++){
  try{$now=Rpc $standard.port $standard.token 'machine_info';$status=Rpc $standard.port $standard.token 'agent_restart_status' @{machine_id=$now.machine_id;request_id=$request};if($status.phase -eq 'completed'){$verified=$true;break}}catch{}
  Start-Sleep -Milliseconds 200
 }
 Assert $verified 'admin_handoff_not_verified';$owned+=$now.privileges.pid
 Assert ($now.machine_id -ceq $info.machine_id -and $now.privileges.elevated -eq $true -and $now.privileges.pid -ne $oldPid) 'new_admin_identity_or_token_wrong'
 Assert ($status.verified_elevated -eq $true) 'status_not_verified'
 $before=$now.privileges.pid
 $repeat=Rpc $standard.port $standard.token 'agent_restart_admin' @{machine_id=$now.machine_id;request_id=$request}
 Assert ($repeat.state -eq 'awaiting_elevation') 'original_receipt_not_preserved'
 Assert ((Rpc $standard.port $standard.token 'machine_info').privileges.pid -eq $before) 'duplicate_restart_occurred'
 $usage=Rpc $standard.port $standard.token 'tool_usage' @{machine_id=$now.machine_id;query='agent_restart_admin'}
 Assert ($usage.usage.tools[0].total -ge 2) 'restart_counters_lost'
 'PASS real_standard_to_admin_helper_handoff_identity_receipt_counters'
 'LIMIT native_UAC_dialog_not_automated'
 'PASS ALL agent_admin_suite'
}finally{
 foreach($process in @(Get-CimInstance Win32_Process | Where-Object {$_.ExecutablePath -and $_.ExecutablePath.StartsWith($suite+'\',[StringComparison]::OrdinalIgnoreCase)})){
  Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
 }
 for($i=0;$i -lt 50;$i++){try{if(Test-Path $suite){Remove-Item -LiteralPath $suite -Recurse -Force};break}catch{if($i -eq 49){throw};Start-Sleep -Milliseconds 100}}
 Assert (!(Test-Path $suite)) 'admin_suite_cleanup_failed'
 'PASS admin_suite_cleanup'
}
