param([Parameter(Mandatory=$true)][string]$ExpectedHash,[switch]$CodexInference)
$ErrorActionPreference='Stop'
$log='D:\Jarvis\Logs\diagnostics\v57-runtime-test.log'
$module='D:\Jarvis\MCP-Source\windows-mcp-agent'
$exe=Join-Path $module 'Staging\jarvis-mcp-057.exe'
$testHome='D:\Jarvis\StagingHome057';$stateA=Join-Path $testHome 'StateA';$stateB=Join-Path $testHome 'StateB'
$processA=$null;$processB=$null;$script:machine='';$script:url='http://127.0.0.1:8766/mcp'
function Report([string]$s){$s|Add-Content $log -Encoding UTF8}
function New-RequestId{return [guid]::NewGuid().ToString('N')}
function Rpc([string]$name,[hashtable]$arguments,[string]$url=$script:url){
 $body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name=$name;arguments=$arguments}}|ConvertTo-Json -Depth 20 -Compress
 $r=Invoke-RestMethod $url -Method Post -ContentType 'application/json' -Headers @{Authorization=('Bearer '+$env:JARVIS_MCP_TOKEN);Accept='application/json, text/event-stream'} -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 35
 if($r.error){throw ('RPC '+($r.error|ConvertTo-Json -Compress))};return $r.result
}
function Call([string]$name,[hashtable]$arguments=@{},[switch]$MustFail){
 if($name -ne 'machine_info'){$arguments.machine_id=$script:machine}
 $r=Rpc $name $arguments
 if($MustFail){if(-not $r.isError){throw ('expected_refusal_'+$name)};return $null}
 if($r.isError){throw ('TOOL '+$name+' '+$r.content[0].text)}
 return ($r.content|Where-Object type -eq 'text'|Select-Object -First 1).text|ConvertFrom-Json
}
function Start-TestAgent([string]$state,[int]$port){
 if(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue){throw ('port_in_use_'+$port)}
 $env:JARVIS_STATE_DIR=$state
 $p=Start-Process -FilePath $exe -ArgumentList @('-listen',('127.0.0.1:'+$port)) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testHome ('stdout-'+$port+'.log')) -RedirectStandardError (Join-Path $testHome ('stderr-'+$port+'.log'))
 for($i=0;$i -lt 80;$i++){try{$h=Invoke-RestMethod ('http://127.0.0.1:'+$port+'/health') -TimeoutSec 1;if($h.version -eq '0.5.7'){return $p}}catch{};if($p.HasExited){throw ('candidate_exited_'+$port)};Start-Sleep -Milliseconds 200};throw 'candidate_health_timeout'
}
function Stop-Owned($p){if($p -and -not $p.HasExited){Stop-Process -Id $p.Id -Force;$p.WaitForExit();Start-Sleep -Milliseconds 200}}
function Wait-JobResult([string]$id){for($i=0;$i -lt 180;$i++){$j=Call 'job_status' @{job_id=$id};if($j.status -ne 'running'){return $j};Start-Sleep -Milliseconds 250};throw 'job_wait_timeout'}
try {
 'START '+[DateTime]::UtcNow.ToString('o')|Set-Content $log -Encoding UTF8
 if((Get-FileHash $exe -Algorithm SHA256).Hash -ne $ExpectedHash){throw 'candidate_hash_mismatch'}
 New-Item -ItemType Directory -Force (Join-Path $testHome 'Config')|Out-Null
 if(-not (Test-Path (Join-Path $testHome 'Config\secrets.env'))){'# isolated test configuration'|Set-Content (Join-Path $testHome 'Config\secrets.env') -Encoding UTF8}
 $env:JARVIS_HOME=$testHome;$env:JARVIS_MCP_TOKEN=(New-RequestId);$env:JARVIS_PROJECTS_ROOT='D:\Jarvis\MCP-Source';$env:JARVIS_DESKTOP_ENABLED='1';$env:JARVIS_CODEX_ENABLED='1';$env:JARVIS_TEST_PEERS='1'
 $processA=Start-TestAgent $stateA 8766
 $info=Call 'machine_info';$script:machine=$info.machine_id;Report ('MACHINE '+$script:machine)
 $catalog=Call 'project_catalog';if(-not ($catalog.projects|Where-Object project -eq 'windows-mcp-agent')){throw 'missing_source_project'};Report 'PASS catalog'
 $bad=Rpc 'file_read' @{machine_id='wrong-computer';project='windows-mcp-agent';path='main.go'};if(-not $bad.isError){throw 'machine_guard_failed'};Report 'PASS machine_guard'
 $project='windows-mcp-agent\Staging\runtime-fixture';$fixture=Join-Path 'D:\Jarvis\MCP-Source' $project
 New-Item -ItemType Directory -Force $fixture|Out-Null
 [IO.File]::WriteAllText((Join-Path $fixture 'go.mod'),"module jarvis-runtime-smoke`n`ngo 1.27.0`n",(New-Object Text.UTF8Encoding($false)))
 [IO.File]::WriteAllText((Join-Path $fixture 'main.go'),"package main`nfunc main() {}`n",(New-Object Text.UTF8Encoding($false)))
 [IO.File]::WriteAllText((Join-Path $fixture 'main_test.go'),"package main`nimport `"testing`"`nfunc TestSmoke(t *testing.T) {}`n",(New-Object Text.UTF8Encoding($false)))
 $folder='smoke-'+(New-RequestId);$created=Call 'directory_create' @{project=$project;path=($folder+'/nested');request_id=(New-RequestId)}
 $path=$folder+'/nested/example.txt';$text='literal $() '+[char]0xe8+' "quotes"'
 $req=@{project=$project;path=$path;expected_sha256='missing';content=$text;request_id=(New-RequestId)}
 $write=Call 'file_write' $req;$read=Call 'file_read' @{project=$project;path=$path};if($read.content -cne $text){throw 'text_roundtrip_failed'}
 $replay=Call 'file_write' $req;if($replay.request_id -ne $write.request_id){throw 'replay_receipt_failed'}
 Call 'file_write' @{project=$project;path=$path;expected_sha256='missing';content='do not overwrite';request_id=(New-RequestId)} -MustFail
 $changed=Call 'file_write' @{project=$project;path=$path;expected_sha256=$read.sha256;content='updated';request_id=(New-RequestId)}
 Call 'file_restore' @{restore_id=$changed.request_id;request_id=(New-RequestId)}|Out-Null
 $read=Call 'file_read' @{project=$project;path=$path};if($read.content -cne $text){throw 'restore_failed'};Report 'PASS file_write_read_stale_replay_backup_restore'
 $list=Call 'file_list' @{project=$project;path=$folder;recursive=$true};$moved=Call 'path_move' @{project=$project;path=$folder;destination=($folder+'-moved');expected_sha256=$list.path_sha256;request_id=(New-RequestId)}
 Call 'file_restore' @{restore_id=$moved.request_id;request_id=(New-RequestId)}|Out-Null
 $fileMove=Call 'path_move' @{project=$project;path=$path;destination=($folder+'/nested/renamed.txt');expected_sha256=$read.sha256;request_id=(New-RequestId)}
 Call 'file_restore' @{restore_id=$fileMove.request_id;request_id=(New-RequestId)}|Out-Null;Report 'PASS rename_move_files_and_directories'
 $found=Call 'file_search' @{project=$project;query='literal'};if($found.total_matches -lt 1){throw 'search_failed'}
 foreach($badPath in @('..\..\..\Config\secrets.env','D:\Jarvis\Config\secrets.env','.env','example.txt:ads','CON.txt')){Call 'file_read' @{project=$project;path=$badPath} -MustFail};Report 'PASS protected_paths'
 $job=Call 'command_start' @{project=$project;profile='go_build';request_id=(New-RequestId)};$result=Wait-JobResult $job.job_id;if($result.status -ne 'completed'){throw ('build_failed '+$result.output)};Report ('PASS autonomous_build '+$job.job_id)
 $testJob=Call 'command_start' @{project=$project;profile='go_test';request_id=(New-RequestId)};$result=Wait-JobResult $testJob.job_id;if($result.status -ne 'completed'){throw ('test_failed '+$result.output)};Report ('PASS autonomous_test '+$result.output)
 $capture=Rpc 'screen_capture' @{machine_id=$script:machine;max_width=960;quality=65}
 if($capture.isError){Report ('BLOCKED capture '+$capture.content[0].text)}else{$image=$capture.content|Where-Object type -eq 'image';$meta=($capture.content|Where-Object type -eq 'text').text|ConvertFrom-Json;if(-not $image -or $meta.image_width -ne 960){throw 'capture_schema_failed'};$bytes=[Convert]::FromBase64String($image.data);if($bytes.Length -lt 100 -or $bytes[0] -ne 255 -or $bytes[1] -ne 216){throw 'capture_image_failed'};Report ('PASS direct_capture '+($meta|ConvertTo-Json -Compress -Depth 5))}
 $processB=Start-TestAgent $stateB 8767
 $peerRaw=Rpc 'machine_info' @{} 'http://127.0.0.1:8767/mcp';$peer=($peerRaw.content|Where-Object type -eq 'text').text|ConvertFrom-Json
 ConvertTo-Json -InputObject @(@{device_id='test-peer';machine_id=$peer.machine_id;endpoint='http://127.0.0.1:8767/mcp';token_env='JARVIS_MCP_TOKEN'}) -Depth 5|Set-Content (Join-Path $testHome 'Config\devices.json') -Encoding UTF8
 $devices=Call 'device_list';if(-not ($devices.devices|Where-Object {$_.device_id -eq 'test-peer' -and $_.online})){throw 'peer_not_online'}
 $peerCall=@{operation='project_catalog';machine_id=$peer.machine_id};$remote=Call 'device_call' @{device_id='test-peer';call=$peerCall};if($remote.machine_id -ne $peer.machine_id){throw 'peer_forward_failed'};Report 'PASS authenticated_peer_routing'
 $peerCall.machine_id=$script:machine;Call 'device_call' @{device_id='test-peer';call=$peerCall} -MustFail;Report 'PASS peer_identity_guard'
 Stop-Owned $processB;$processB=$null;$devices=Call 'device_list';if(($devices.devices|Where-Object device_id -eq 'test-peer').online){throw 'offline_peer_reported_online'};Report 'PASS peer_offline'
 $codex=Call 'codex_status';Report ('CODEX '+($codex|ConvertTo-Json -Compress -Depth 8))
 if($CodexInference){if(-not $codex.inference_allowed){throw 'codex_cost_guard_blocked'}
  $thread=Call 'codex_start' @{project=$project;request_id=(New-RequestId)};Report ('CODEX_THREAD '+$thread.thread_id)
  $turn=Call 'codex_turn' @{project=$project;thread_id=$thread.thread_id;text='Reply with exactly JARVIS_MCP_OK. Do not use tools, access files or run commands.';request_id=(New-RequestId)}
  $done=$false;$cursor=0;$reply='';for($i=0;$i -lt 200;$i++){$events=Call 'codex_events' @{project=$project;thread_id=$thread.thread_id;after=$cursor};$cursor=$events.next_after;foreach($ev in $events.events){if($ev.method -eq 'item/agentMessage/delta'){$reply+=$ev.params.delta};if($ev.method -eq 'turn/completed'){$done=$true;Report ('CODEX_COMPLETED '+($ev.params|ConvertTo-Json -Depth 10 -Compress));if($ev.params.turn.status -ne 'completed'){throw 'codex_turn_did_not_complete'}}};if($events.pending.Count -gt 0){throw 'codex_requires_user_approval'};if($done){break};Start-Sleep -Milliseconds 500};if(-not $done){throw 'codex_turn_timeout'};if($reply -notmatch 'JARVIS_MCP_OK'){throw 'codex_reply_mismatch'};Report 'PASS codex_real_turn'
 }
 Stop-Owned $processA;$processA=$null;$processA=Start-TestAgent $stateA 8766
 $after=Call 'machine_info';if($after.machine_id -ne $script:machine){throw 'identity_changed_after_restart'};$persisted=Call 'job_status' @{job_id=$job.job_id};if($persisted.status -ne 'completed'){throw 'job_not_persisted'};Call 'file_write' $req|Out-Null;Report 'PASS identity_jobs_receipts_survive_restart'
 Call 'file_restore' @{restore_id=$write.request_id;request_id=(New-RequestId)}|Out-Null
 Call 'file_restore' @{restore_id=$created.request_id;request_id=(New-RequestId)}|Out-Null
 Report 'PASS'
}catch{Report ('ERROR '+$_.Exception.Message);exit 1}
finally{Stop-Owned $processA;Stop-Owned $processB}
