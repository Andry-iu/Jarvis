param([Parameter(Mandatory=$true)][string]$ExpectedHash,[Parameter(Mandatory=$true)][string]$CandidateFile)
$ErrorActionPreference='Stop'
$module='D:\Jarvis\MCP-Source\windows-mcp-agent';$homeDir='D:\Jarvis\StagingHome060';$state=Join-Path $homeDir 'State'
$exe=Join-Path (Join-Path $module 'Staging') $CandidateFile;$log='D:\Jarvis\Logs\diagnostics\v60-queue-test.log';$owned=$null;$script:machine=''
$project='windows-mcp-agent/Staging/queue-fixture060'
function Report([string]$s){$s|Add-Content $log -Encoding UTF8}
function Rid{return [guid]::NewGuid().ToString('N')}
function Call([string]$name,[hashtable]$arguments=@{}){
 if($name -ne 'machine_info'){$arguments.machine_id=$script:machine}
 $body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name=$name;arguments=$arguments}}|ConvertTo-Json -Compress -Depth 15
 $r=Invoke-RestMethod http://127.0.0.1:8766/mcp -Method Post -ContentType 'application/json' -Headers @{Authorization=('Bearer '+$env:JARVIS_MCP_TOKEN);Accept='application/json, text/event-stream'} -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 35
 if($r.error -or $r.result.isError){throw ('TOOL '+$name+' '+$r.result.content[0].text)}
 return $r.result.content[0].text|ConvertFrom-Json
}
function Start-Owned{
 if(Get-NetTCPConnection -LocalPort 8766 -State Listen -ErrorAction SilentlyContinue){throw 'test_port_busy'}
 $p=Start-Process $exe -ArgumentList @('-listen','127.0.0.1:8766') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $homeDir 'stdout.log') -RedirectStandardError (Join-Path $homeDir 'stderr.log')
 for($i=0;$i -lt 80;$i++){try{if((Invoke-RestMethod http://127.0.0.1:8766/health -TimeoutSec 1).version -eq '0.6.0'){return $p}}catch{};if($p.HasExited){throw 'candidate_exited'};Start-Sleep -Milliseconds 200};throw 'health_timeout'
}
function Stop-Owned($p){if($p -and -not $p.HasExited){& "$env:SystemRoot\System32\taskkill.exe" /PID $p.Id /T /F 2>&1|Out-Null;$p.WaitForExit();Start-Sleep -Milliseconds 200}}
function Entry([string]$id){$q=Call 'queue_list' @{project=$project;limit=200};return $q.operations|Where-Object {$_.queue_id -eq $id}|Select-Object -First 1}
function Wait-State([string]$id,[string]$status){for($i=0;$i -lt 160;$i++){$e=Entry $id;if($e.status -eq $status){return $e};if($e.status -in @('failed','uncertain','cancelled','timed_out')){throw ('unexpected_queue_state_'+$e.status+'_'+$e.error)};Start-Sleep -Milliseconds 100};throw ('queue_wait_timeout_'+$status)}
try{
 'START '+[DateTime]::UtcNow.ToString('o')|Set-Content $log -Encoding UTF8
 if((Get-FileHash $exe -Algorithm SHA256).Hash -ne $ExpectedHash){throw 'candidate_hash_mismatch'}
 New-Item -ItemType Directory -Force (Join-Path $homeDir 'Config')|Out-Null
 if(-not (Test-Path (Join-Path $homeDir 'Config\secrets.env'))){'# isolated test'|Set-Content (Join-Path $homeDir 'Config\secrets.env') -Encoding UTF8}
 $fixture=Join-Path $module 'Staging\queue-fixture060';New-Item -ItemType Directory -Force $fixture|Out-Null
 [IO.File]::WriteAllText((Join-Path $fixture 'go.mod'),"module queue-fixture`n`ngo 1.27.0`n",(New-Object Text.UTF8Encoding($false)))
 $test='package queuefixture; import("testing";"time";"os"); func TestWork(t *testing.T){os.WriteFile("running.txt",[]byte("RUNNING"),0600);time.Sleep(4*time.Second)}'
 [IO.File]::WriteAllText((Join-Path $fixture 'work_test.go'),$test,(New-Object Text.UTF8Encoding($false)))
 $env:JARVIS_HOME=$homeDir;$env:JARVIS_STATE_DIR=$state;$env:JARVIS_MCP_TOKEN=Rid;$env:JARVIS_PROJECTS_ROOT='D:\Jarvis\MCP-Source';$env:JARVIS_CODEX_ENABLED='1'
 $env:JARVIS_CF_TEAM_DOMAIN='';$env:JARVIS_CF_APP_AUD=''
 $pkg=Get-AppxPackage -Name OpenAI.Codex|Select-Object -First 1;$env:JARVIS_CODEX_EXE=Join-Path $pkg.InstallLocation 'app\resources\codex.exe'
 $owned=Start-Owned;$script:machine=(Call 'machine_info').machine_id;$identity=$script:machine
 $a=Call 'command_start' @{project=$project;profile='go_test';request_id=(Rid);timeout_seconds=30};$a=Wait-State $a.queue_id 'running'
 $b=Call 'queue_add' @{project=$project;request_id=(Rid);label='B';call=@{operation='command_start';profile='go_test'}}
 $c=Call 'queue_add' @{project=$project;request_id=(Rid);label='C';call=@{operation='command_start';profile='go_test'}}
 $moved=Call 'queue_move' @{queue_id=$c.queue_id;before_id=$b.queue_id;request_id=(Rid)}
 $waiting=@($moved.operations|Where-Object {$_.status -eq 'queued'});if($waiting.Count -ne 2 -or $waiting[0].queue_id -ne $c.queue_id){throw 'reorder_not_observed'};Report 'PASS reorder_waiting'
 Call 'queue_cancel' @{queue_id=$b.queue_id;request_id=(Rid)}|Out-Null;if((Entry $b.queue_id).status -ne 'cancelled'){throw 'waiting_cancel_failed'};Report 'PASS cancel_waiting'
 $watch=[Diagnostics.Stopwatch]::StartNew();$read=Call 'file_read' @{project=$project;path='work_test.go'};$watch.Stop()
 if($read.content -ne $test -or (Entry $a.queue_id).status -ne 'running' -or $watch.Elapsed.TotalSeconds -gt 2){throw 'read_did_not_overlap_processing'}
 $readOther=Call 'file_read' @{project='.';path='NOTICE'};if(-not $readOther.sha256){throw 'independent_file_read_failed'};Report 'PASS concurrent_reads'
 $quota=Call 'codex_status';if(-not $quota.usage.windows.primary){throw 'quota_unavailable'};Report ('PASS readonly_quota remaining='+$quota.usage.windows.primary.remaining_percent+' credit_status='+$quota.usage.credits.status)
 $doneA=Wait-State $a.queue_id 'completed';$doneC=Wait-State $c.queue_id 'completed'
 $jobA=Call 'job_status' @{job_id=$doneA.job_id};$jobC=Call 'job_status' @{job_id=$doneC.job_id}
 if($jobA.exit_code -ne 0 -or $jobC.exit_code -ne 0 -or ([DateTime]$jobC.created_at) -lt ([DateTime]$jobA.finished_at)){throw 'same_project_overlap'};Report 'PASS serialized_execution'
 $d=Call 'command_start' @{project=$project;profile='go_test';request_id=(Rid);timeout_seconds=30};$d=Wait-State $d.queue_id 'running'
 $e=Call 'queue_add' @{project=$project;request_id=(Rid);label='E';call=@{operation='command_start';profile='go_test'}}
 Stop-Owned $owned;$owned=$null;$owned=Start-Owned;$script:machine=(Call 'machine_info').machine_id
 if($script:machine -ne $identity){throw 'identity_changed'};$old=Entry $d.queue_id;$pending=Entry $e.queue_id
 if($old.status -ne 'uncertain' -or $pending.status -ne 'queued' -or -not $pending.paused_reason){throw 'restart_replayed_or_lost_queue'};Report 'PASS restart_no_replay'
 Call 'queue_cancel' @{queue_id=$e.queue_id;request_id=(Rid)}|Out-Null
 Call 'queue_resume' @{project=$project;request_id=(Rid)}|Out-Null
 if((Entry $d.queue_id).status -ne 'interrupted'){throw 'uncertain_command_not_reconciled'};Report 'PASS explicit_resume_after_inspection'
 Report 'PASS'
}catch{Report ('ERROR '+$_.Exception.Message);exit 1}finally{Stop-Owned $owned}
