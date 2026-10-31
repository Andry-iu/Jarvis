param([Parameter(Mandatory=$true)][string]$Agent)
$ErrorActionPreference='Stop'
$l='D:\Jarvis\Logs\diagnostics\desktop-integration-056.log'
$agentProcess=$null;$fixtureProcess=$null
$run=Join-Path ([IO.Path]::GetTempPath()) ('jarvis-desktop-test-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $run|Out-Null
$report=Join-Path $run 'fixture.json'
function Log($s){$s|Add-Content -Encoding UTF8 $l}
function Check($yes,$s){if(!$yes){throw $s};Log ('PASS '+$s)}
function Report{Get-Content $report -Raw -Encoding UTF8|ConvertFrom-Json}
function Command($action,$x=0,$y=0){
 $id=[guid]::NewGuid().ToString('N')
 @{id=$id;action=$action;x=$x;y=$y}|ConvertTo-Json -Compress|Set-Content -Encoding UTF8 ($report+'.command')
 $until=(Get-Date).AddSeconds(5)
 do{Start-Sleep -Milliseconds 100;$r=Report}while($r.last -ne $id -and (Get-Date) -lt $until)
 if($r.last -ne $id){throw 'Fixture command timed out'}
}
function Tool($name,$argumentsObject){
 $body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name=$name;arguments=$argumentsObject}}|ConvertTo-Json -Depth 12 -Compress
 $rpc=Invoke-RestMethod 'http://127.0.0.1:8766/mcp' -Method Post -ContentType 'application/json' -Headers $headers -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 20
 if($rpc.error){throw 'RPC error'}
 return $rpc.result
}
function Operation($op,$desktop=@{}){
 $start=Tool 'task_start' @{operation=$op;desktop=$desktop}
 if($start.isError){return @{state='refused';detail=$start.content[0].text}}
 $until=(Get-Date).AddSeconds(25)
 do{Start-Sleep -Milliseconds 150;$r=Tool 'agent_status' @{};$s=$r.content[0].text|ConvertFrom-Json}while($s.state -eq 'running' -and (Get-Date) -lt $until)
 if($s.state -eq 'completed'){return @{state=$s.state;data=($s.task.result|ConvertFrom-Json)}}
 return @{state=$s.state;detail=$s.task.result}
}
function Inspect{
 $r=Operation 'desktop_inspect'
 if($r.state -ne 'completed'){throw ('Inspect failed: '+$r.detail)}
 return $r.data
}
function Element($s,$aid){
 $handle=(Report).handles.$aid
 $e=@($s.elements|Where-Object {$_.native_handle -eq $handle})
 if($e.Count -ne 1){throw ('Fixture element not unique '+$aid)}
 return $e[0].id
}
function Action($s,$aid,$action,$text=$null){
 $d=@{snapshot_id=$s.snapshot_id;element_id=(Element $s $aid);action=$action}
 if($null -ne $text){$d.text=$text}
 return Operation 'desktop_action' $d
}
function Rejected($r,$label){
 if($r.state -eq 'completed'){throw ('Unsafe action accepted '+$label)}
 Log ('PASS rejected '+$label+' '+$r.detail)
}
try{
 'START isolated UI integration'|Set-Content -Encoding UTF8 $l
 if(Get-NetTCPConnection -State Listen -LocalPort 8766 -ErrorAction SilentlyContinue){throw 'Probe port occupied'}
 $env:JARVIS_HOME='D:\Jarvis';$env:JARVIS_DESKTOP_ENABLED='1'
 $env:JARVIS_MCP_TOKEN=([guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N'))
 $headers=@{Authorization=('Bearer '+$env:JARVIS_MCP_TOKEN);Accept='application/json, text/event-stream'}
 $agentProcess=Start-Process $Agent -ArgumentList '--listen','127.0.0.1:8766' -PassThru -WindowStyle Hidden
 Start-Sleep -Seconds 2
 $fixtureScript=Join-Path $PSScriptRoot 'desktop-fixture.ps1'
 $arguments=@('-NoProfile','-ExecutionPolicy','Bypass','-STA','-File',('"{0}"' -f $fixtureScript),'-Report',('"{0}"' -f $report))
 $fixtureProcess=Start-Process powershell.exe -ArgumentList $arguments -PassThru -WindowStyle Normal
 $until=(Get-Date).AddSeconds(10)
 while(!(Test-Path $report) -and (Get-Date) -lt $until){Start-Sleep -Milliseconds 200}
 if(!(Test-Path $report)){throw 'Fixture did not start'}
 Start-Sleep -Milliseconds 500
 $s=$null
 for($i=0;$i -lt 5;$i++){
  try{$candidate=Inspect;if($candidate.window.hwnd -eq (Report).hwnd){$s=$candidate;break};Log ('PREFLIGHT foreground='+$candidate.window.hwnd+' fixture='+(Report).hwnd)}catch{Log ('PREFLIGHT '+$_.Exception.Message)}
  Start-Sleep -Milliseconds 500
 }
 if(!$s){throw 'Fixture did not become foreground; no UI actions attempted'}
 Check ($s.window.hwnd -eq (Report).hwnd) 'foreground fixture identity'
 Log ('FIXTURE_ELEMENTS='+($s.elements|ConvertTo-Json -Depth 8 -Compress))
 $same=@($s.elements|Where-Object {$_.name -eq 'Conferma'})
 Check ($same.Count -eq 2) 'duplicate button names observed'
 Check (@($s.elements|Where-Object {$_.native_handle -eq (Report).handles.fixturePassword}).Count -eq 0) 'password field excluded'
 $value='Jarvis '+[char]0x00e8+' '+[char]0x03a9+' "quote" $()'
 $r=Action $s 'fixtureInput' 'set_text' $value
 Check ($r.state -eq 'completed' -and $r.data.verified) 'text verified by readback'
 $s=Inspect;$r=Action $s 'fixtureGood' 'invoke'
 Log ('BUTTON_RESULT='+($r|ConvertTo-Json -Depth 8 -Compress))
 Log ('BUTTON_APP_COUNTS='+((Report|Select-Object good,decoy)|ConvertTo-Json -Compress))
 Check ($r.state -eq 'completed' -and $r.data.dispatched) 'button invoke dispatched'
 Start-Sleep -Milliseconds 200
 Check ((Report).good -eq 1 -and (Report).decoy -eq 0 -and (Report).text -ceq $value) 'application result and decoy untouched'
 $r=Action $s 'fixtureGood' 'invoke';Rejected $r 'snapshot replay'
 $s=Inspect;Command 'movecontrol';$r=Action $s 'fixtureGood' 'invoke';Rejected $r 'moved control'
 $s=Inspect;Command 'edit';$r=Action $s 'fixtureInput' 'set_text' 'OVERWRITE';Rejected $r 'user edited field'
 Check ((Report).text -eq 'USER_CHANGED') 'changed text preserved'
 $s=Inspect;Command 'other';$r=Action $s 'fixtureGood' 'invoke';Rejected $r 'same title different foreground HWND';Command 'main'
 $s=Inspect;Command 'disable';$r=Action $s 'fixtureGood' 'invoke';Rejected $r 'disabled after inspection';Command 'enable'
 $s=Inspect;$r=Action $s 'fixtureReadOnly' 'set_text' 'OVERWRITE';Rejected $r 'read only field'
 $s=Inspect
 $r=Action $s 'fixtureToggle' 'invoke'
 Check ($r.state -eq 'completed') 'checkbox invoke dispatched'
 Start-Sleep -Milliseconds 200
 Check ((Report).toggle) 'checkbox application state verified'
 $s=Inspect
 $r=Operation 'desktop_action' @{snapshot_id=$s.snapshot_id;element_id=(Element $s 'fixtureGood');action='invoke';x=10;y=10}
 Rejected $r 'raw coordinates forbidden'
 Add-Type -AssemblyName System.Windows.Forms
 $screens=@([Windows.Forms.Screen]::AllScreens)
 Log ('MONITORS='+$screens.Count)
 foreach($screen in $screens){
 Command 'move' ($screen.WorkingArea.X+80) ($screen.WorkingArea.Y+80)
 $s=Inspect;$r=Action $s 'fixtureGood' 'invoke'
 Check ($r.state -eq 'completed') ('fresh target after monitor move x='+$screen.WorkingArea.X)
 }
 Check ((Report).decoy -eq 0) 'decoy click count remains zero'
 Log 'PASS_DESKTOP_INTEGRATION_056'
}catch{Log ('FAIL '+$_.Exception.Message)}
finally{
 if($fixtureProcess -and !$fixtureProcess.HasExited){Stop-Process -Id $fixtureProcess.Id -Force}
 if($agentProcess -and !$agentProcess.HasExited){Stop-Process -Id $agentProcess.Id -Force}
 Remove-Item $run -Recurse -Force -ErrorAction SilentlyContinue
 Log 'ISOLATED_PROCESSES_STOPPED'
}
