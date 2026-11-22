param([Parameter(Mandatory=$true)][string]$ExpectedHash,[switch]$FileProbeOnly)
$ErrorActionPreference='Stop'
$log='D:\Jarvis\Logs\diagnostics\v58-codex-test.log'
$module='D:\Jarvis\MCP-Source\windows-mcp-agent';$testHome='D:\Jarvis\StagingHome058';$state=Join-Path $testHome 'StateA'
$exe=Join-Path $module 'Staging\jarvis-mcp-058.exe';$owned=$null;$script:machine='';$project='windows-mcp-agent\Staging\runtime-fixture'
function Report([string]$s){$s|Add-Content $log -Encoding UTF8}
function Rid{return [guid]::NewGuid().ToString('N')}
function Call([string]$name,[hashtable]$arguments=@{}){
 if($name -ne 'machine_info'){$arguments.machine_id=$script:machine}
 $body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name=$name;arguments=$arguments}}|ConvertTo-Json -Compress -Depth 15
 $r=Invoke-RestMethod http://127.0.0.1:8766/mcp -Method Post -ContentType 'application/json' -Headers @{Authorization=('Bearer '+$env:JARVIS_MCP_TOKEN);Accept='application/json, text/event-stream'} -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 35
 if($r.error -or $r.result.isError){throw ('TOOL '+$name+' '+$r.result.content[0].text)}
 return $r.result.content[0].text|ConvertFrom-Json
}
function Start-Owned {
 if(Get-NetTCPConnection -LocalPort 8766 -State Listen -ErrorAction SilentlyContinue){throw 'test_port_busy'}
 $p=Start-Process $exe -ArgumentList @('-listen','127.0.0.1:8766') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testHome 'stdout.log') -RedirectStandardError (Join-Path $testHome 'stderr.log')
 for($i=0;$i -lt 80;$i++){try{if((Invoke-RestMethod http://127.0.0.1:8766/health -TimeoutSec 1).version -eq '0.5.8'){return $p}}catch{};if($p.HasExited){throw 'candidate_exited'};Start-Sleep -Milliseconds 200};throw 'candidate_health_timeout'
}
function Stop-Owned($p){if($p -and -not $p.HasExited){& "$env:SystemRoot\System32\taskkill.exe" /PID $p.Id /T /F 2>&1|Out-Null;$p.WaitForExit();Start-Sleep -Milliseconds 200}}
function Wait-Turn([string]$thread,[string]$turn,[switch]$Interrupted){
 $cursor=0;$reply='';for($i=0;$i -lt 240;$i++){
 $ev=Call 'codex_events' @{project=$project;thread_id=$thread;after=$cursor};$cursor=$ev.next_after
 if($ev.pending.Count -gt 0){Report ('PENDING '+($ev.pending|ConvertTo-Json -Compress -Depth 10));throw 'approval_requires_review'}
 foreach($event in $ev.events){if($event.params.turnId -eq $turn -and $event.method -eq 'item/agentMessage/delta'){$reply+=$event.params.delta};if($event.method -eq 'turn/completed' -and $event.params.turn.id -eq $turn){$status=$event.params.turn.status;Report ('TURN_FINAL '+$turn+' '+$status);if($status -ne 'completed' -and -not ($Interrupted -and $status -eq 'interrupted')){Report ('TURN_ERROR '+($event.params.turn.error|ConvertTo-Json -Compress -Depth 6));throw 'turn_failed'};return @{status=$status;reply=$reply}}}
 Start-Sleep -Milliseconds 250};throw 'turn_completion_timeout'
}
try{
 'START '+[DateTime]::UtcNow.ToString('o')|Set-Content $log -Encoding UTF8
 if((Get-FileHash $exe).Hash -ne $ExpectedHash){throw 'candidate_hash_mismatch'}
 New-Item -ItemType Directory -Force (Join-Path $testHome 'Config')|Out-Null
 if(-not (Test-Path (Join-Path $testHome 'Config\secrets.env'))){'# isolated configuration'|Set-Content (Join-Path $testHome 'Config\secrets.env') -Encoding UTF8}
 $env:JARVIS_HOME=$testHome;$env:JARVIS_STATE_DIR=$state;$env:JARVIS_MCP_TOKEN=Rid;$env:JARVIS_PROJECTS_ROOT='D:\Jarvis\MCP-Source';$env:JARVIS_CODEX_ENABLED='1'
 $pkg=Get-AppxPackage -Name OpenAI.Codex|Select-Object -First 1;$env:JARVIS_CODEX_EXE=Join-Path $pkg.InstallLocation 'app\resources\codex.exe'
 $owned=Start-Owned;$script:machine=(Call 'machine_info').machine_id
 & powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$module\scripts\write-codex-cost-proof.ps1" -AgentHome $testHome -StateDirectory $state|Out-Null
 if($LASTEXITCODE -ne 0){throw 'independent_billing_proof_failed'}
 $status=Call 'codex_status';Report ('COST_STATUS '+($status|ConvertTo-Json -Compress -Depth 8));if(-not $status.inference_allowed){throw 'cost_guard_blocked'}
 $thread=Call 'codex_start' @{project=$project;request_id=(Rid)};Report ('THREAD '+$thread.thread_id)
 if($FileProbeOnly){
  $file='codex-probe-'+(Rid)+'.txt'
  Call 'file_write' @{project=$project;path=$file;expected_sha256='missing';content='JARVIS_BEFORE';request_id=(Rid)}|Out-Null
  $prompt='Use apply_patch to replace the contents of the existing file '+$file+' in the current project with exactly JARVIS_CODEX_FILE_OK followed by a newline. Modify only that file. Do not use shell commands, network or any other tools. Then reply JARVIS_FILE_DONE.'
  $turn=Call 'codex_turn' @{project=$project;thread_id=$thread.thread_id;text=$prompt;request_id=(Rid)}
  $done=Wait-Turn $thread.thread_id $turn.turn.id
  $read=Call 'file_read' @{project=$project;path=$file};if($read.content.Trim() -cne 'JARVIS_CODEX_FILE_OK'){throw 'codex_file_write_not_verified'}
  Report ('PASS codex_real_file_change '+$file+' sha256='+$read.sha256);Report 'PASS';return
 }
 $turn=Call 'codex_turn' @{project=$project;thread_id=$thread.thread_id;text='Reply with exactly JARVIS_MCP_OK. Do not use tools or access files.';request_id=(Rid)}
 $done=Wait-Turn $thread.thread_id $turn.turn.id;if($done.reply -notmatch 'JARVIS_MCP_OK'){throw 'first_reply_mismatch'};Report 'PASS real_inference'
 $history=Call 'codex_read' @{project=$project;thread_id=$thread.thread_id};if($history.turns.Count -lt 1){throw 'history_missing'};Report 'PASS read_history'
 $turn=Call 'codex_turn' @{project=$project;thread_id=$thread.thread_id;text='Print the integers from 1 to 5000, one per line; do not use tools.';request_id=(Rid)}
 $steer=Call 'codex_steer' @{project=$project;thread_id=$thread.thread_id;turn_id=$turn.turn.id;text='Stop the number list and reply only JARVIS_STEER_OK.';request_id=(Rid)};Report 'PASS steer_accepted'
 $interrupt=Call 'codex_interrupt' @{project=$project;thread_id=$thread.thread_id;turn_id=$turn.turn.id;request_id=(Rid)}
 $done=Wait-Turn $thread.thread_id $turn.turn.id -Interrupted;if($done.status -ne 'interrupted'){throw 'interrupt_race_completed_first'};Report 'PASS interrupt_confirmed'
 Stop-Owned $owned;$owned=$null;$owned=Start-Owned
 $resumed=Call 'codex_resume' @{project=$project;thread_id=$thread.thread_id;request_id=(Rid)}
 $history=Call 'codex_read' @{project=$project;thread_id=$thread.thread_id};if($history.turns.Count -lt 2){throw 'resumed_history_missing'};Report 'PASS resume_after_agent_restart'
 $turn=Call 'codex_turn' @{project=$project;thread_id=$thread.thread_id;text='Reply with exactly JARVIS_RESUMED_OK. Do not use tools.';request_id=(Rid)}
 $done=Wait-Turn $thread.thread_id $turn.turn.id;if($done.reply -notmatch 'JARVIS_RESUMED_OK'){throw 'resumed_reply_mismatch'};Report 'PASS resumed_real_turn'
 Report 'PASS'
}catch{Report ('ERROR '+$_.Exception.Message);exit 1}
finally{Stop-Owned $owned}
