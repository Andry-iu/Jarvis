param([Parameter(Mandatory=$true)][string]$SourceDirectory,[Parameter(Mandatory=$true)][string]$Package)
$ErrorActionPreference='Stop'
$temp=Join-Path 'D:\Jarvis\Temp' ('layout-test-'+[guid]::NewGuid().ToString('N'))
$fixtureHome=Join-Path $temp 'Installed';$task='';$child=$null
$oldToken=$env:JARVIS_MCP_TOKEN
function LocalCall([string]$name){
 $token=[IO.File]::ReadAllText((Join-Path $fixtureHome 'Config\secrets.env')).Trim().Substring('JARVIS_MCP_TOKEN='.Length)
 $body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name=$name;arguments=@{}}}|ConvertTo-Json -Compress -Depth 8
 $r=Invoke-RestMethod 'http://127.0.0.1:19875/mcp' -Method Post -ContentType 'application/json' -Headers @{Authorization=('Bearer '+$token);Accept='application/json, text/event-stream'} -Body $body -TimeoutSec 3
 if($r.error -or $r.result.isError){throw 'fixture_mcp_error'}
 return $r.result.content[0].text|ConvertFrom-Json
}
try{
 New-Item -ItemType Directory -Force $temp|Out-Null
 & (Join-Path $Package 'Install.ps1') -NonInteractive -DeviceName 'Layout fixture' -InstallDir $fixtureHome -Connection local -Port 19875 -PeerPort 19876 -NoTask -NoStart
 # This fixture is strictly loopback. Do not create LAN listeners or public prompts.
 Remove-Item (Join-Path $fixtureHome 'Config\peer-listener.json')
 $manifest=Get-Content (Join-Path $fixtureHome 'Config\installation.json') -Raw|ConvertFrom-Json
 Remove-NetFirewallRule -Name $manifest.task_name -ErrorAction SilentlyContinue
 Remove-Item (Join-Path $fixtureHome 'Config\installation.json')
 foreach($name in @('Common.ps1','Start.ps1','Stop.ps1','update-agent.ps1')){Remove-Item (Join-Path $fixtureHome ('Scripts\'+$name)) -ErrorAction SilentlyContinue}
 Move-Item (Join-Path $fixtureHome 'Agent\jarvis-mcp.exe') (Join-Path $fixtureHome 'jarvis-mcp.exe')
 $psi=New-Object Diagnostics.ProcessStartInfo;$psi.FileName=Join-Path $fixtureHome 'jarvis-mcp.exe';$psi.Arguments='-listen 127.0.0.1:19875';$psi.WorkingDirectory=$fixtureHome;$psi.UseShellExecute=$false;$psi.CreateNoWindow=$true
 foreach($key in @($psi.EnvironmentVariables.Keys)){if($key -match '^(JARVIS_|OPENAI_|CODEX_|CF_|TUNNEL_)'){$psi.EnvironmentVariables.Remove($key)}}
 $psi.EnvironmentVariables['JARVIS_HOME']=$fixtureHome
 $child=[Diagnostics.Process]::Start($psi)
 $before=$null
 for($i=0;$i -lt 80;$i++){try{$before=LocalCall 'machine_info';break}catch{Start-Sleep -Milliseconds 100}}
 if(-not $before){throw 'legacy_fixture_not_ready'}
 $env:JARVIS_MCP_TOKEN=[IO.File]::ReadAllText((Join-Path $fixtureHome 'Config\secrets.env')).Trim().Substring('JARVIS_MCP_TOKEN='.Length)
 $task='JarvisLayoutFixture-'+[guid]::NewGuid().ToString('N').Substring(0,12)
 $script=Join-Path $SourceDirectory 'scripts\align-agent-layout.ps1'
 & $script -HomeDir $fixtureHome -ExpectedMachine $before.machine_id -ExpectedHash ((Get-FileHash (Join-Path $fixtureHome 'jarvis-mcp.exe')).Hash.ToLowerInvariant()) -SourceDirectory $SourceDirectory -TaskName $task -DeviceName 'Layout fixture' -Port 19875
 $after=LocalCall 'machine_info'
 if($after.machine_id -cne $before.machine_id -or $after.privileges.elevated -ne $true -or (Test-Path (Join-Path $fixtureHome 'jarvis-mcp.exe'))){throw 'migration_not_verified'}
 $tcp=@(Get-NetTCPConnection -LocalPort 19875 -State Listen)
 if((Get-Process -Id $tcp[0].OwningProcess).Path -ine (Join-Path $fixtureHome 'Agent\jarvis-mcp.exe')){throw 'new_binary_path_invalid'}
 if((Get-ScheduledTask -TaskName $task).Principal.RunLevel -ne 'Highest'){throw 'startup_not_admin'}
 'PASS fixture_legacy_to_Agent_startup_Highest_identity_admin'
 # Re-running the same procedure on an already correct layout restarts safely.
 $first=$after.privileges.pid
 & $script -HomeDir $fixtureHome -ExpectedMachine $before.machine_id -ExpectedHash ((Get-FileHash (Join-Path $fixtureHome 'Agent\jarvis-mcp.exe')).Hash.ToLowerInvariant()) -SourceDirectory $SourceDirectory -TaskName $task
 $again=LocalCall 'machine_info'
 if($again.privileges.pid -eq $first -or $again.privileges.elevated -ne $true){throw 'aligned_restart_not_verified'}
 'PASS fixture_existing_Agent_restart_identity_admin'
}finally{
 $env:JARVIS_MCP_TOKEN=$oldToken
 if(Test-Path (Join-Path $fixtureHome 'Config\installation.json')){
  try{& (Join-Path $fixtureHome 'Scripts\Stop.ps1') -HomeDir $fixtureHome -Force}catch{Write-Output ('cleanup_stop_'+$_.Exception.Message)}
 }
 if($child -and -not $child.HasExited){Stop-Process -Id $child.Id -Force;$null=$child.WaitForExit(10000)}
 if($task){Unregister-ScheduledTask -TaskName $task -Confirm:$false -ErrorAction SilentlyContinue}
 for($i=0;$i -lt 100;$i++){try{$g=[IO.File]::Open((Join-Path $fixtureHome 'Config\runner.lock'),'OpenOrCreate','ReadWrite','None');$g.Dispose();break}catch{Start-Sleep -Milliseconds 100}}
 if(Test-Path $temp){Remove-Item -LiteralPath $temp -Recurse -Force}
 if(Test-Path $temp){throw 'fixture_cleanup_failed'}
 'PASS layout_fixture_cleanup'
}
