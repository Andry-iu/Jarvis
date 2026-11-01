param([Parameter(Mandatory=$true)][string]$Candidate,[Parameter(Mandatory=$true)][string]$ExpectedHash,[Parameter(Mandatory=$true)][string]$ExpectedMachine,[Parameter(Mandatory=$true)][string]$SourceCommit)
$ErrorActionPreference='Stop'
$root=$env:JARVIS_HOME
. (Join-Path $root 'Scripts\Common.ps1')
$s=Read-Install $root
$log=Join-Path $root 'Logs\diagnostics\v70-managed-deploy.log'
$backup=Join-Path $root ('Backups\mcp-0.7.0-'+$ExpectedHash.Substring(0,12))
$installed=Join-Path $root 'Agent\jarvis-mcp.exe'
$oldBackup=Join-Path $backup 'jarvis-mcp-0.6.1.exe'
$oldInstall=Join-Path $backup 'Config\installation.json'
$probe=$null;$supervisor=$null;$promotion=$false
function Report($v){$v|Add-Content $log -Encoding UTF8}
function Hash($p){return (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash}
function WaitHealth($homeDir,[string]$version){
 for($i=0;$i -lt 80;$i++){
  try{$state=Read-Install $homeDir;$v=Invoke-RestMethod ('http://127.0.0.1:'+$state.port+'/health') -TimeoutSec 1;if($v.version -eq $version){return}}catch{}
  Start-Sleep -Milliseconds 200
 }
 throw ('health_timeout_'+$version)
}
function StopProbe{
 if($script:probe){
  $child=$script:probe
  Stop-OwnedTree (Get-Record $child.process) $script:probePath
  foreach($t in @($child.out_task,$child.err_task)){$null=$t.Wait(3000)}
  $child.out.Dispose();$child.err.Dispose();$script:probe=$null
 }
}
function StartSupervisor{
 $start=Join-Path $root 'Scripts\Start.ps1'
 $psi=New-Object Diagnostics.ProcessStartInfo
 $psi.FileName=Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
 $psi.Arguments='-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+$start+'" -HomeDir "'+$root+'"'
 $psi.UseShellExecute=$false;$psi.CreateNoWindow=$true
 return [Diagnostics.Process]::Start($psi)
}
function WaitRunnerReleased{
 for($i=0;$i -lt 80;$i++){
  try{$guard=[IO.File]::Open((Join-Path $root 'Config\runner.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None);$guard.Dispose();return}catch{}
  Start-Sleep -Milliseconds 100
 }
 throw 'old_supervisor_lock_not_released'
}

function StopManagedAgentOnly{
 $record=[IO.File]::ReadAllText((Join-Path $root 'Config\running.json'))|ConvertFrom-Json
 $owned=Get-OwnedProcess $record.agent $installed
 if($owned){Stop-Process -Id $owned.Id -Force;if(!$owned.WaitForExit(10000)){throw 'managed_agent_did_not_stop'}}
 # The updater descends from the agent: never taskkill /T that agent.
 # The existing supervisor closes the tunnel and releases its runner lock.
 WaitRunnerReleased
 if($record.tunnel){Stop-OwnedTree $record.tunnel (Join-Path $root 'Agent\cloudflared.exe')}
}

function ReplaceBinary($source,[string]$expected){
 for($i=0;$i -lt 50;$i++){
  try{Copy-Item -LiteralPath $source -Destination $installed -Force;if((Hash $installed) -ine $expected){throw 'binary_copy_hash_mismatch'};return}catch{if($i -eq 49){throw};Start-Sleep -Milliseconds 200}
 }
}
try{
 'START '+[DateTime]::UtcNow.ToString('o')|Set-Content $log -Encoding UTF8
 if($s.agent_version -ne '0.6.1' -or (Hash $installed) -ine $s.agent_sha256){throw 'working_manifest_or_binary_changed'}
 if((Hash $Candidate) -ine $ExpectedHash -or !(Test-Path $oldBackup) -or !(Test-Path $oldInstall)){throw 'candidate_or_verified_backup_missing'}
 if((Hash $oldBackup) -ine (Hash $installed)){throw 'backup_binary_changed'}
 $identity=Invoke-LocalTool $root 'machine_info'
 if($identity.machine_id -cne $ExpectedMachine -or $identity.version -ne '0.6.1'){throw 'wrong_working_agent'}
 $q=Invoke-LocalTool $root 'queue_list' @{machine_id=$ExpectedMachine;limit=200}
 if(@($q.operations).Count -or !$q.queue_healthy){throw 'queue_not_idle'}
 $legacy=Invoke-LocalTool $root 'agent_status';if($legacy.state -eq 'running'){throw 'legacy_task_running'}
 $durable=@{}
 foreach($f in Get-ChildItem (Join-Path $root 'Config') -File){
  if($f.Name -in @('installation.json','running.json') -or $f.Name -like '*.lock'){continue}
  $durable[$f.Name]=Hash $f.FullName
 }
 Report 'PASS verified_backup_and_idle_identity'
 $testHome=Join-Path $backup ('managed-rehearsal-'+[guid]::NewGuid().ToString('N'))
 New-Item -ItemType Directory -Force (Join-Path $testHome 'Config'),(Join-Path $testHome 'Logs')|Out-Null
 Protect-Folder $testHome
 $testState=Join-Path $testHome 'State'
 Copy-Item -LiteralPath (Join-Path $backup 'state-snapshot') -Destination $testState -Recurse
 $testInstall=[IO.File]::ReadAllText($oldInstall)|ConvertFrom-Json
 $testInstall.home=$testHome;$testInstall.port=8767
 Write-Utf8 (Join-Path $testHome 'Config\installation.json') ($testInstall|ConvertTo-Json -Depth 20)
 $bytes=New-Object byte[] 48;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();$rng.GetBytes($bytes);$rng.Dispose()
 $testToken=[Convert]::ToBase64String($bytes)
 Write-Utf8 (Join-Path $testHome 'Config\secrets.env') ('JARVIS_MCP_TOKEN='+$testToken)
  $settings=Read-AgentEnv $root
 $projects=[string]$settings['JARVIS_PROJECTS_ROOT'];if(!$projects){throw 'authorized_projects_root_missing'}
 $envText='JARVIS_STATE_DIR='+$testState+[Environment]::NewLine+'JARVIS_PROJECTS_ROOT='+$projects+[Environment]::NewLine+'JARVIS_CODEX_ENABLED=0'
 Write-Utf8 (Join-Path $testHome 'Config\agent.env') $envText
 if(Get-NetTCPConnection -LocalPort 8767 -State Listen -ErrorAction SilentlyContinue){throw 'rehearsal_port_busy'}
 foreach($item in @(@{exe=$Candidate;version='0.7.0'},@{exe=$oldBackup;version='0.6.1'})){
  $script:probePath=$item.exe
  $script:probe=Start-Child $item.exe '-listen 127.0.0.1:8767' $testHome ('probe-'+$item.version)
  WaitHealth $testHome $item.version
  $info=Invoke-LocalTool $testHome 'machine_info'
  if($info.machine_id -cne $ExpectedMachine -or $info.version -ne $item.version){throw 'rehearsal_identity_mismatch'}
  StopProbe
  Report ('PASS isolated_state_'+$item.version)
 }
 Report 'ROLLBACK_REHEARSAL_PASS'
 $promotion=$true
 StopManagedAgentOnly
 WaitRunnerReleased
 ReplaceBinary $Candidate $ExpectedHash
 $newInstall=[IO.File]::ReadAllText($oldInstall)|ConvertFrom-Json
 $newInstall.agent_version='0.7.0';$newInstall.agent_sha256=$ExpectedHash.ToLowerInvariant()
 $newInstall|Add-Member -NotePropertyName agent_source_commit -NotePropertyValue $SourceCommit -Force
 $pending=Join-Path $root ('Config\upgrade-'+[guid]::NewGuid().ToString('N')+'.json')
 Write-Utf8 $pending ($newInstall|ConvertTo-Json -Depth 20)
 [IO.File]::Replace($pending,(Join-Path $root 'Config\installation.json'),(Join-Path $backup 'installation-before-promotion.json'))
 $supervisor=StartSupervisor
 WaitHealth $root '0.7.0'
 $info=Invoke-LocalTool $root 'machine_info'
 if($info.machine_id -cne $ExpectedMachine -or $info.version -ne '0.7.0'){throw 'installed_identity_mismatch'}
 $m=Invoke-LocalTool $root 'maintenance_status' @{machine_id=$ExpectedMachine}
 if($m.interval_hours -ne 24 -or $m.error -or !$m.last_check){throw 'maintenance_unhealthy'}
 foreach($name in $durable.Keys){if((Hash (Join-Path $root ('Config\'+$name))) -ine $durable[$name]){throw 'durable_configuration_changed'}}
 $agent=$null;$tunnel=$null
 for($i=0;$i -lt 80;$i++){
  $running=[IO.File]::ReadAllText((Join-Path $root 'Config\running.json'))|ConvertFrom-Json
  $agent=Get-OwnedProcess $running.agent $installed
  if($agent -and $running.tunnel){$tunnel=Get-OwnedProcess $running.tunnel (Join-Path $root 'Agent\cloudflared.exe');if($tunnel){break}}
  if($supervisor.HasExited){throw 'managed_supervisor_exited'}
  Start-Sleep -Milliseconds 200
 }
 if(!$agent -or !$tunnel){throw 'managed_process_record_not_ready'}
 @{version='0.7.0';source_commit=$SourceCommit;sha256=$ExpectedHash;machine_id=$ExpectedMachine;supervisor_pid=$supervisor.Id;agent_pid=$agent.Id;tunnel_pid=$tunnel.Id;backup=$backup;maintenance=$m;checked_at=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json -Depth 10|Set-Content (Join-Path $backup 'installed-receipt.json') -Encoding UTF8
 Report ('INSTALLED_SHA256 '+$ExpectedHash);Report ('SOURCE_COMMIT '+$SourceCommit);Report 'PASS managed_supervisor_agent_tunnel_identity_config_maintenance'
}catch{
 Report ('ERROR '+$_.Exception.Message)
 if($promotion){
  try{
   StopManagedAgentOnly
   WaitRunnerReleased
   ReplaceBinary $oldBackup (Hash $oldBackup)
   Copy-Item -LiteralPath $oldInstall -Destination (Join-Path $root 'Config\installation.json') -Force
   $supervisor=StartSupervisor;WaitHealth $root '0.6.1';Report 'ROLLBACK_RESTORED'
  }catch{Report ('ROLLBACK_ERROR '+$_.Exception.Message)}
 }
 exit 1
}finally{StopProbe}
