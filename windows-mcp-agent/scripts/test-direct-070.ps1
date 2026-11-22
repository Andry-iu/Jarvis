param([Parameter(Mandatory=$true)][string]$CandidateFile,[switch]$ResumePrevious)
$ErrorActionPreference='Stop'
$module='D:\Jarvis\MCP-Source\windows-mcp-agent'
$run=[guid]::NewGuid().ToString('N')
$homeDir=Join-Path 'D:\Jarvis' ('DirectTest070-'+$run)
$log='D:\Jarvis\Logs\diagnostics\v70-direct-test.log'
$server=$null;$script:machine=''
function Report($s){$s|Add-Content $log -Encoding UTF8}
function Rid{return [guid]::NewGuid().ToString('N')}
function Call([string]$name,[hashtable]$a=@{}){
 if($name -ne 'machine_info'){$a.machine_id=$script:machine}
 $body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name=$name;arguments=$a}}|ConvertTo-Json -Compress -Depth 20
 $r=Invoke-RestMethod http://127.0.0.1:8766/mcp -Method Post -ContentType 'application/json' -Headers @{Authorization=('Bearer '+$env:JARVIS_MCP_TOKEN);Accept='application/json, text/event-stream'} -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 40
 if($r.error -or $r.result.isError){throw ('TOOL '+$name+' '+$r.result.content[0].text)}
 return $r.result.content[0].text|ConvertFrom-Json
}
try{
 'START '+[DateTime]::UtcNow.ToString('o')|Set-Content $log -Encoding UTF8
 $exe=Join-Path (Join-Path $module 'Staging') $CandidateFile
 Report ('CANDIDATE_SHA256 '+(Get-FileHash $exe -Algorithm SHA256).Hash)
 if(Get-NetTCPConnection -LocalPort 8766 -State Listen -ErrorAction SilentlyContinue){throw 'test_port_busy'}
 New-Item -ItemType Directory -Force (Join-Path $homeDir 'Config')|Out-Null
 '# isolated test'|Set-Content (Join-Path $homeDir 'Config\secrets.env')
 $env:JARVIS_HOME=$homeDir;$env:JARVIS_STATE_DIR=Join-Path $homeDir 'State'
 $env:JARVIS_MCP_TOKEN=Rid;$env:JARVIS_CF_TEAM_DOMAIN='';$env:JARVIS_CF_APP_AUD=''
 $env:JARVIS_PROJECTS_ROOT=$module;$env:JARVIS_DESKTOP_ENABLED='1';$env:JARVIS_CODEX_ENABLED='0'
 $env:JARVIS_LOG_RETENTION_DAYS='';$env:JARVIS_UPDATE_RETENTION_DAYS=''
 $server=Start-Process $exe -ArgumentList @('-listen','127.0.0.1:8766') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $homeDir 'stdout.log') -RedirectStandardError (Join-Path $homeDir 'stderr.log')
 for($i=0;$i -lt 40;$i++){if($server.HasExited){throw ('candidate_exited_'+[IO.File]::ReadAllText((Join-Path $homeDir 'stderr.log')))};try{if((Invoke-RestMethod http://127.0.0.1:8766/health -TimeoutSec 1).version -eq '0.7.0'){break}}catch{};Start-Sleep -Milliseconds 200}
 $info=Call 'machine_info';$script:machine=$info.machine_id
 if($info.version -ne '0.7.0'){throw 'wrong_candidate'}
 $m=Call 'maintenance_status'
 if($m.log_retention_days -ne 30 -or $m.batch_retention_days -ne 30 -or $m.interval_hours -ne 24 -or !$m.last_check){throw 'maintenance_contract_failed'}
 Report 'PASS maintenance_defaults_and_startup'
 $folder=Call 'known_folder' @{folder='desktop'};if(![IO.Directory]::Exists($folder.path)){throw 'known_folder_invalid'};Report 'PASS known_folder'
 $w=$null
 if($ResumePrevious){
  $receipts=@(Get-ChildItem 'D:\Jarvis\DirectTest070-*\State\requests\*.json' -File|ForEach-Object {try{$x=Get-Content $_.FullName -Raw|ConvertFrom-Json;if($x.operation -eq 'window_focus' -and $x.status -eq 'completed' -and $x.result.window.title -match '(Blocco note|Notepad)$'){$x}}catch{}}|Sort-Object at -Descending)
  if(!$receipts.Count){throw 'prior_test_window_receipt_missing'}
  $identity=$receipts[0].result.window
  $all=Call 'window_list'
  $matching=@($all.windows|Where-Object {$_.window.hwnd -eq $identity.hwnd -and $_.window.pid -eq $identity.pid -and $_.window.start_ticks -ceq $identity.start_ticks})
  if($matching.Count -ne 1){throw 'prior_test_window_changed'}
  $w=$matching[0]
  foreach($title in @('Conferma Salva con nome','Salva con nome')){
   $dialogs=@($all.windows|Where-Object {$_.window.pid -eq $identity.pid -and $_.window.hwnd -ne $identity.hwnd -and $_.window.title -eq $title})
   if($dialogs.Count -eq 1){
    if($title -eq 'Conferma Salva con nome'){
     $null=Call 'window_focus' @{window_id=$dialogs[0].window_id;request_id=(Rid)}
     $d=Call 'window_inspect' @{window_id=$dialogs[0].window_id}
     $no=@($d.elements|Where-Object {$_.type -eq 'ControlType.Button' -and $_.name -eq 'No' -and $_.patterns -contains 'invoke'})
     if($no.Count -ne 1){throw 'explicit_no_button_missing'}
     $null=Call 'ui_action' @{snapshot_id=$d.snapshot_id;element_id=$no[0].id;action='invoke';request_id=(Rid)}
     $after=Call 'window_list';if(@($after.windows|Where-Object {$_.window_id -eq $dialogs[0].window_id}).Count){throw 'overwrite_rejection_not_verified'}
    }else{
     $closedDialog=Call 'window_close' @{window_id=$dialogs[0].window_id;request_id=(Rid)}
     if($closedDialog.status -ne 'closed'){throw 'prior_dialog_did_not_close'}
    }
    Report 'PASS cancel_own_previous_save_dialog'
   }elseif($dialogs.Count -gt 1){throw 'prior_dialog_ambiguous'}
  }
 }else{
  $before=@((Call 'window_list').windows|ForEach-Object {$_.window_id})
  $rid=Rid
  $launch=Call 'process_start' @{executable=(Join-Path $env:SystemRoot 'System32\notepad.exe');request_id=$rid}
  $repeat=Call 'process_start' @{executable=(Join-Path $env:SystemRoot 'System32\notepad.exe');request_id=$rid}
  if($launch.elevated -ne $false -or $launch.pid -ne $repeat.pid){throw 'launch_default_or_duplicate_failed'}
  Report ('PASS process_start_standard_and_idempotent '+$launch.privilege_source)
  for($i=0;$i -lt 20;$i++){
   $all=Call 'window_list'
   $new=@($all.windows|Where-Object {$before -notcontains $_.window_id -and $_.window.title -match 'Blocco note|Notepad'})
   if($new.Count -eq 1){$w=$new[0];break}
   Start-Sleep -Milliseconds 250
  }
 }
 if(!$w){throw 'new_notepad_window_not_unique'}
 $focused=Call 'window_focus' @{window_id=$w.window_id;request_id=(Rid)}
 $snapshot=Call 'window_inspect' @{window_id=$w.window_id}
 $edits=@($snapshot.elements|Where-Object {$_.type -eq 'ControlType.Document' -and $_.patterns -contains 'set_text' -and $_.bounds[2] -gt 0 -and $_.bounds[3] -gt 0})
 if(!$edits.Count){$edits=@($snapshot.elements|Where-Object {$_.type -eq 'ControlType.Edit' -and $_.patterns -contains 'set_text' -and $_.bounds[2] -gt 0 -and $_.bounds[3] -gt 0})}
 if($edits.Count -ne 1){Report ('TARGET '+($w|ConvertTo-Json -Compress -Depth 4));Report ('SNAPSHOT_WINDOW '+($snapshot.window|ConvertTo-Json -Compress -Depth 4));Report ('EDIT_METADATA '+(($edits|Select-Object id,type,automation_id)|ConvertTo-Json -Compress));throw ('notepad_editor_count_'+$edits.Count)}
 $typed=Call 'ui_action' @{snapshot_id=$snapshot.snapshot_id;element_id=$edits[0].id;action='set_text';text='ciao';request_id=(Rid)}
 if(!$typed.verified){throw 'text_not_verified'};Report 'PASS direct_write_notepad'
 $target=Join-Path $homeDir 'ciao-direct.txt'
 $saved=Call 'ui_file_dialog' @{window_id=$w.window_id;dialog_mode='save';path=$target;expected_text='ciao';request_id=(Rid)}
 if(!$saved.text_matches -or $saved.status -ne 'saved' -or [IO.File]::ReadAllText($target) -cne 'ciao'){throw 'save_verification_failed'}
 Report 'PASS application_save_dialog'
 $closed=Call 'window_close' @{window_id=$w.window_id;request_id=(Rid)}
 if($closed.status -ne 'closed'){throw ('close_'+$closed.status)};Report 'PASS direct_window_close'
 Report 'PASS ALL'
}catch{Report ('ERROR '+$_.Exception.Message);exit 1}finally{
 if($server -and !$server.HasExited){Stop-Process -Id $server.Id -Force;$server.WaitForExit()}
}