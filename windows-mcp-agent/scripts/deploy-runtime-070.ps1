param([Parameter(Mandatory=$true)][string]$Candidate,[Parameter(Mandatory=$true)][string]$ExpectedHash,[Parameter(Mandatory=$true)][string]$ExpectedMachine,[string]$SourceCommit,[switch]$RehearsalOnly)
$ErrorActionPreference='Stop'
$root=$env:JARVIS_HOME
if(!$root -or ![IO.Path]::IsPathRooted($root)){throw 'explicit_agent_home_required'}
$log=Join-Path $root 'Logs\diagnostics\v70-deploy.log'
$backup=Join-Path $root ('Backups\mcp-0.7.0-'+$ExpectedHash.Substring(0,12))
$probe=$null;$new=$null;$promotion=$false
function Report($s){$s|Add-Content $log -Encoding UTF8}
function Hash($p){return (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash}
function CopyChecked($a,$b){$h=Hash $a;Copy-Item -LiteralPath $a -Destination $b -Force;if((Hash $b) -cne $h){throw 'backup_copy_hash_mismatch'}}
function Call([int]$port,[string]$name,[hashtable]$a=@{}){
 $body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name=$name;arguments=$a}}|ConvertTo-Json -Depth 15 -Compress
 $r=Invoke-RestMethod ('http://127.0.0.1:'+$port+'/mcp') -Method Post -ContentType 'application/json' -Headers @{Authorization=('Bearer '+$env:JARVIS_MCP_TOKEN);Accept='application/json, text/event-stream'} -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 15
 if($r.error -or $r.result.isError){throw ('mcp_check_failed_'+$name)}
 return $r.result.content[0].text|ConvertFrom-Json
}
function Health([int]$port,[string]$version,$p){
 for($i=0;$i -lt 60;$i++){if($p.HasExited){throw 'agent_exited'};try{if((Invoke-RestMethod ('http://127.0.0.1:'+$port+'/health') -TimeoutSec 1).version -eq $version){return}}catch{};Start-Sleep -Milliseconds 200}
 throw 'health_timeout'
}
function StopOwned($p){if($p -and !$p.HasExited){Stop-Process -Id $p.Id -Force;if(!$p.WaitForExit(10000)){throw 'process_stop_timeout'}}}
try{
 'START '+[DateTime]::UtcNow.ToString('o')|Set-Content $log -Encoding UTF8
 if(Test-Path (Join-Path $root 'Config\installation.json')){throw 'managed_installation_requires_deploy_managed_runtime_070'}
 if($ExpectedHash -notmatch '^[a-fA-F0-9]{64}$' -or (!$RehearsalOnly -and $SourceCommit -notmatch '^[a-f0-9]{40}$')){throw 'invalid_release_identity'}
 if((Hash $Candidate) -ine $ExpectedHash){throw 'candidate_hash_mismatch'}
 $info=Call 8765 'machine_info'
 if($info.machine_id -cne $ExpectedMachine -or $info.version -ne '0.6.1'){throw 'working_agent_identity_or_version_changed'}
 $q=Call 8765 'queue_list' @{machine_id=$ExpectedMachine;limit=200}
 if(@($q.operations).Count -ne 0 -or !$q.queue_healthy){throw 'agent_queue_not_idle'}
 $legacy=Call 8765 'agent_status';if($legacy.state -eq 'running'){throw 'legacy_task_running'}
 $listeners=@(Get-NetTCPConnection -LocalPort 8765 -State Listen)
 if($listeners.Count -ne 1){throw 'listener_not_unique'}
 $cim=Get-CimInstance Win32_Process -Filter ('ProcessId='+$listeners[0].OwningProcess)
 $installed=$cim.ExecutablePath
 if(!$installed.StartsWith($root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetFileName($installed) -ine 'jarvis-mcp.exe'){throw 'listener_binary_outside_home'}
 $old=Get-Process -Id $cim.ProcessId
 $oldHash=Hash $installed
 New-Item -ItemType Directory -Force $backup|Out-Null
 $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
 & "$env:SystemRoot\System32\icacls.exe" $backup /inheritance:r /grant:r ('*'+$sid+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F'|Out-Null
 if($LASTEXITCODE -ne 0){throw 'backup_acl_failed'}
 $oldBackup=Join-Path $backup 'jarvis-mcp-0.6.1.exe'
 if(Test-Path $oldBackup){if((Hash $oldBackup) -ine $oldHash){throw 'prior_backup_mismatch'}}else{CopyChecked $installed $oldBackup}
 $config=Join-Path $root 'Config';$backupConfig=Join-Path $backup 'Config'
 New-Item -ItemType Directory -Force $backupConfig|Out-Null
 $configHashes=@{}
 foreach($f in Get-ChildItem $config -File){
  if($f.Name -like '*.lock'){continue} # Ephemeral runner locks are not configuration backups.
  $configHashes[$f.Name]=Hash $f.FullName
  $destination=Join-Path $backupConfig $f.Name
  if(Test-Path $destination){if((Hash $destination) -ine $configHashes[$f.Name]){throw 'config_backup_changed'}}else{CopyChecked $f.FullName $destination}
 }
 $state=if($env:JARVIS_STATE_DIR){$env:JARVIS_STATE_DIR}else{Join-Path $root 'State\runtime-v1'}
 $saved=Join-Path $backup 'state-snapshot'
 foreach($f in Get-ChildItem $state -File -Recurse){
  if($f.Name -eq 'agent.lock'){continue}
  $rel=$f.FullName.Substring($state.Length).TrimStart('\');$destination=Join-Path $saved $rel
  New-Item -ItemType Directory -Force (Split-Path $destination -Parent)|Out-Null
  CopyChecked $f.FullName $destination
 }
 Report 'BACKUP_VERIFIED'
 if(Get-NetTCPConnection -LocalPort 8767 -State Listen -ErrorAction SilentlyContinue){throw 'rehearsal_port_busy'}
 $testHome=Join-Path $backup 'rehearsal-home';New-Item -ItemType Directory -Force (Join-Path $testHome 'Config')|Out-Null
 '# isolated migration test'|Set-Content (Join-Path $testHome 'Config\secrets.env')
 $testState=Join-Path $testHome ('State-'+[guid]::NewGuid().ToString('N'))
 Copy-Item -LiteralPath $saved -Destination $testState -Recurse
 $oldEnv=@{};foreach($k in 'JARVIS_HOME','JARVIS_STATE_DIR','JARVIS_CF_TEAM_DOMAIN','JARVIS_CF_APP_AUD','JARVIS_CODEX_ENABLED'){$oldEnv[$k]=[Environment]::GetEnvironmentVariable($k,'Process')}
 try{
  $env:JARVIS_HOME=$testHome;$env:JARVIS_STATE_DIR=$testState;$env:JARVIS_CF_TEAM_DOMAIN='';$env:JARVIS_CF_APP_AUD='';$env:JARVIS_CODEX_ENABLED='0'
  $probe=Start-Process $Candidate -ArgumentList @('-listen','127.0.0.1:8767') -WindowStyle Hidden -PassThru
  Health 8767 '0.7.0' $probe
  if((Call 8767 'machine_info').machine_id -cne $ExpectedMachine){throw 'candidate_migration_identity_changed'}
  StopOwned $probe;$probe=$null
  $probe=Start-Process $oldBackup -ArgumentList @('-listen','127.0.0.1:8767') -WindowStyle Hidden -PassThru
  Health 8767 '0.6.1' $probe
  if((Call 8767 'machine_info').machine_id -cne $ExpectedMachine){throw 'rollback_migration_identity_changed'}
  StopOwned $probe;$probe=$null
 }finally{foreach($k in $oldEnv.Keys){[Environment]::SetEnvironmentVariable($k,$oldEnv[$k],'Process')}}
 Report 'ROLLBACK_REHEARSAL_PASS'
 if($RehearsalOnly){Report 'PASS rehearsal_only';return}
 $next=Join-Path (Split-Path $installed -Parent) 'jarvis-mcp-070.new.exe';CopyChecked $Candidate $next
 $promotion=$true;StopOwned $old
 for($i=0;$i -lt 40;$i++){try{CopyChecked $next $installed;break}catch{if($i -eq 39){throw};Start-Sleep -Milliseconds 200}}
 $new=Start-Process $installed -WindowStyle Hidden -PassThru
 Health 8765 '0.7.0' $new
 $now=Call 8765 'machine_info'
 if($now.machine_id -cne $ExpectedMachine -or $now.version -ne '0.7.0' -or (Hash $installed) -ine $ExpectedHash){throw 'installed_identity_or_hash_mismatch'}
 foreach($k in $configHashes.Keys){if((Hash (Join-Path $config $k)) -ine $configHashes[$k]){throw 'configuration_changed'}}
 $maintenance=Call 8765 'maintenance_status' @{machine_id=$ExpectedMachine}
 if($maintenance.interval_hours -ne 24 -or !$maintenance.last_check -or $maintenance.error){throw 'maintenance_unhealthy'}
 @{version='0.7.0';source_commit=$SourceCommit;sha256=$ExpectedHash;machine_id=$ExpectedMachine;binary=$installed;backup=$backup;pid=$new.Id;maintenance=$maintenance;checked_at=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json -Depth 6|Set-Content (Join-Path $backup 'installed-receipt.json') -Encoding UTF8
 Report ('INSTALLED_SHA256 '+$ExpectedHash);Report ('SOURCE_COMMIT '+$SourceCommit);Report 'PASS installed_health_identity_config_maintenance'
}catch{
 Report ('ERROR '+$_.Exception.Message)
 if($promotion){
  try{StopOwned $new;CopyChecked $oldBackup $installed;$new=Start-Process $installed -WindowStyle Hidden -PassThru;Health 8765 '0.6.1' $new;Report 'ROLLBACK_RESTORED'}catch{Report ('ROLLBACK_ERROR '+$_.Exception.Message)}
 }
 exit 1
}finally{StopOwned $probe}
