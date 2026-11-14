param([Parameter(Mandatory=$true)][string]$Package,[Parameter(Mandatory=$true)][string]$Log)
$ErrorActionPreference='Stop';$temp=Join-Path ([IO.Path]::GetTempPath()) ('jarvis-network-test-'+[guid]::NewGuid().ToString('N'));$children=@();$homes=@();$complete=$false
function Report($text){$text|Add-Content $Log -Encoding UTF8}
try{
 New-Item -ItemType Directory $temp|Out-Null
 foreach($i in @(0,1)){
  $installHome=Join-Path $temp ('Agent'+$i);$homes+=$installHome;$port=18765+2*$i;$peerPort=$port+1
  & (Join-Path $Package 'Install.ps1') -NonInteractive -DeviceName ('Banco Unicode '+[char]232+' '+$i) -InstallDir $installHome -Connection local -Port $port -PeerPort $peerPort -NoStart
  if(-not (Test-Path (Join-Path $installHome 'Config\install-complete.json'))){throw 'install_missing_receipt'}
  . (Join-Path $installHome 'Scripts\Common.ps1')
  $settings=Read-Install $installHome
  $task=Get-ScheduledTask -TaskName $settings.task_name
  if($task.Principal.LogonType -ne 'Interactive' -or $task.Principal.RunLevel -ne 'Highest' -or -not $task.Triggers){throw 'startup_task_not_registered'}
  if($settings.remote_configured -or ([IO.File]::ReadAllText((Join-Path $installHome 'Config\agent.env')) -match 'CLOUDFLARE|JARVIS_CF_')){throw 'local_install_requires_cloudflare'}
  $child=Start-Child (Join-Path $installHome 'Agent\jarvis-mcp.exe') ('-listen 127.0.0.1:'+$port) $installHome 'test-agent';$children+=$child
  $ready=$false;for($j=0;$j -lt 100;$j++){try{$info=Invoke-LocalTool $installHome 'machine_info';if($info.version -eq '0.8.3'){$ready=$true;break}}catch{};Start-Sleep -Milliseconds 100}
  if(-not $ready){throw 'installed_agent_not_ready'}
  Report ('PASS local_install_autostart_no_cloudflare_'+$i)
 }
 $a=[IO.File]::ReadAllText((Join-Path $homes[0] 'Config\peer-enrollment.json'))|ConvertFrom-Json
 $b=[IO.File]::ReadAllText((Join-Path $homes[1] 'Config\peer-enrollment.json'))|ConvertFrom-Json
 if($a.machine_id -eq $b.machine_id -or $a.token -ceq $b.token -or $a.certificate_sha256 -ceq $b.certificate_sha256){throw 'install_identity_or_credentials_cloned'}
 # Loopback endpoint uses the SAME pinned certificate; no global CA changes.
 $b.endpoint='https://127.0.0.1:18768/gateway'
 $enrollment=Join-Path $temp 'enrollment-for-pair.json';[IO.File]::WriteAllText($enrollment,($b|ConvertTo-Json),(New-Object Text.UTF8Encoding($false)))
 & (Join-Path $homes[0] 'Scripts\Pair-Agent.ps1') -HomeDir $homes[0] -EnrollmentFile $enrollment -DeviceId 'target' -DeviceName 'Target'
 . (Join-Path $homes[0] 'Scripts\Common.ps1')
 $info=Invoke-LocalTool $homes[0] 'machine_info'
 $result=Invoke-LocalTool $homes[0] 'device_call' @{machine_id=$info.machine_id;device_id='target';call=@{operation='machine_info';machine_id=$b.machine_id}}
 if($result.machine_id -cne $b.machine_id){throw 'paired_target_mismatch'}
 Report 'PASS unique_identities_private_pairing_live_control'
 # Reinstallation must refuse to overwrite the first installation.
 $refused=$false;try{& (Join-Path $Package 'Install.ps1') -NonInteractive -DeviceName 'Another' -InstallDir $homes[0] -Connection local -Port 18765 -PeerPort 18766 -NoStart}catch{$refused=$true}
 if(-not $refused){throw 'existing_installation_overwritten'}
 Report 'PASS existing_install_preserved';$complete=$true
}finally{
 foreach($child in $children){if(-not $child.process.HasExited){Stop-Process -Id $child.process.Id -Force;$null=$child.process.WaitForExit(10000)};$null=$child.out_task.Wait(3000);$null=$child.err_task.Wait(3000);$child.out.Dispose();$child.err.Dispose()}
 foreach($installHome in $homes){$path=Join-Path $installHome 'Config\installation.json';if(Test-Path $path){$settings=Get-Content $path -Raw|ConvertFrom-Json;Unregister-ScheduledTask -TaskName $settings.task_name -Confirm:$false -ErrorAction SilentlyContinue;Remove-NetFirewallRule -Name $settings.task_name -ErrorAction SilentlyContinue}}
 if(Test-Path $temp){Remove-Item -LiteralPath $temp -Recurse -Force};if(Test-Path $temp){throw 'installer_test_cleanup_failed'}
 Report 'PASS installer_cleanup'
}
if($complete){Report 'PASS NETWORK_INSTALLER_COMPLETE'}
