param(
 [string]$HomeDir=$env:JARVIS_HOME,
 [Parameter(Mandatory=$true)][string]$Candidate,
 [Parameter(Mandatory=$true)][string]$ExpectedHash,
 [Parameter(Mandatory=$true)][string]$ExpectedMachine,
 [Parameter(Mandatory=$true)][string]$ExpectedPreviousVersion,
 [Parameter(Mandatory=$true)][string]$ExpectedPreviousHash,
 [Parameter(Mandatory=$true)][string]$TargetVersion,
 [Parameter(Mandatory=$true)][string]$SourceCommit,
 [switch]$RehearsalOnly
)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($HomeDir).TrimEnd('\')
$log=Join-Path $root 'Logs\diagnostics\self-update.log'
$backup=Join-Path $root ('Backups\self-update-'+[guid]::NewGuid().ToString('N'))
$lock=$null;$probe=$null;$newProcess=$null;$promotion=$false
$manifestPath=Join-Path $root 'Config\installation.json'
$managed=Test-Path -LiteralPath $manifestPath
$port=8765;$manifest=$null
function Report($s){$s|Add-Content $log -Encoding UTF8}
function Hash($p){return (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash}
function CopyChecked($a,$b){
 $expected=Hash $a
 New-Item -ItemType Directory -Force (Split-Path $b -Parent)|Out-Null
 Copy-Item -LiteralPath $a -Destination $b -Force
 if((Hash $b) -ine $expected -or (Hash $a) -ine $expected){throw 'backup_copy_changed_or_invalid'}
}
function Call([int]$p,[string]$name,[hashtable]$a=@{},[string]$token=$env:JARVIS_MCP_TOKEN){
 $body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name=$name;arguments=$a}}|ConvertTo-Json -Depth 15 -Compress
 $r=Invoke-RestMethod ('http://127.0.0.1:'+$p+'/mcp') -Method Post -ContentType 'application/json' -Headers @{Authorization=('Bearer '+$token);Accept='application/json, text/event-stream'} -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 15
 if($r.error -or $r.result.isError){throw ('mcp_check_failed_'+$name)}
 return $r.result.content[0].text|ConvertFrom-Json
}
function WaitHealth([int]$p,[string]$version,$process){
 for($i=0;$i -lt 60;$i++){
  if($process -and $process.HasExited){throw 'started_process_exited'}
  try{if((Invoke-RestMethod ('http://127.0.0.1:'+$p+'/health') -TimeoutSec 1).version -eq $version){return}}catch{}
  Start-Sleep -Milliseconds 200
 }
 throw ('health_timeout_'+$version)
}
function WaitRunner{
 if(!$managed){return}
 for($i=0;$i -lt 80;$i++){
  try{$f=[IO.File]::Open((Join-Path $root 'Config\runner.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None);$f.Dispose();return}catch{}
  Start-Sleep -Milliseconds 100
 }
 throw 'supervisor_lock_not_released'
}
function StopAgent{
 $listeners=@(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
 if($listeners.Count -gt 1){throw 'listener_not_unique'}
 if($listeners.Count){
  $proc=Get-Process -Id $listeners[0].OwningProcess
  if($proc.Path -ine $installed){throw 'listener_path_changed'}
  Stop-Process -Id $proc.Id -Force
  if(!$proc.WaitForExit(10000)){throw 'agent_stop_timeout'}
 }
 # Stop only this PID. The detached update batch must survive its parent.
 WaitRunner
}
function StartConfigured{
 $psi=New-Object Diagnostics.ProcessStartInfo
 $psi.FileName=$startExe;$psi.Arguments=$startArgs;$psi.WorkingDirectory=$root
 $psi.UseShellExecute=$false;$psi.CreateNoWindow=$true
 return [Diagnostics.Process]::Start($psi)
}
function ReplaceBinary($from,[string]$expected){
 for($i=0;$i -lt 50;$i++){
  try{Copy-Item -LiteralPath $from -Destination $installed -Force;if((Hash $installed) -ine $expected){throw 'installed_hash_mismatch'};return}catch{if($i -eq 49){throw};Start-Sleep -Milliseconds 200}
 }
}
function StopProbe{
 if(!$script:probe){return}
 $child=$script:probe
 if(!$child.process.HasExited){Stop-Process -Id $child.process.Id -Force;$null=$child.process.WaitForExit(10000)}
 $null=$child.out_task.Wait(3000);$null=$child.err_task.Wait(3000)
 $child.out.Dispose();$child.err.Dispose();$script:probe=$null
}
function StartProbe([string]$exe,[string]$probeHome){
 $psi=New-Object Diagnostics.ProcessStartInfo
 $psi.FileName=$exe;$psi.Arguments='-listen 127.0.0.1:8767';$psi.WorkingDirectory=$probeHome
 $psi.UseShellExecute=$false;$psi.CreateNoWindow=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
 foreach($key in @($psi.EnvironmentVariables.Keys)){if($key -match '^(JARVIS_|OPENAI_|CODEX_|CF_|TUNNEL_)'){$psi.EnvironmentVariables.Remove($key)}}
 $psi.EnvironmentVariables['JARVIS_HOME']=$probeHome
 $p=[Diagnostics.Process]::Start($psi)
 $o=[IO.File]::Open((Join-Path $probeHome ('stdout-'+$p.Id+'.log')),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
 $e=[IO.File]::Open((Join-Path $probeHome ('stderr-'+$p.Id+'.log')),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
 return @{process=$p;out=$o;err=$e;out_task=$p.StandardOutput.BaseStream.CopyToAsync($o);err_task=$p.StandardError.BaseStream.CopyToAsync($e)}
}
try{
 'START '+[DateTime]::UtcNow.ToString('o')|Set-Content $log -Encoding UTF8
 if(!$HomeDir -or $SourceCommit -notmatch '^[a-f0-9]{40}$' -or $ExpectedHash -notmatch '^[a-fA-F0-9]{64}$' -or $ExpectedPreviousHash -notmatch '^[a-fA-F0-9]{64}$'){throw 'invalid_update_contract'}
 if((Hash $Candidate) -ine $ExpectedHash){throw 'candidate_hash_mismatch'}
 $lock=[IO.File]::Open((Join-Path $root 'Config\update.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
 if($managed){
  $manifest=[IO.File]::ReadAllText($manifestPath)|ConvertFrom-Json
  $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
  if($manifest.format -ne 1 -or $manifest.home -ine $root -or $manifest.computer -ine $env:COMPUTERNAME -or $manifest.user_sid -cne $sid){throw 'installation_owner_or_path_mismatch'}
  $port=[int]$manifest.port
 }
 $info=Call $port 'machine_info'
 if($info.machine_id -cne $ExpectedMachine -or $info.version -ne $ExpectedPreviousVersion){throw 'working_identity_or_version_changed'}
 $q=Call $port 'queue_list' @{machine_id=$ExpectedMachine;limit=200}
 if(@($q.operations).Count -or !$q.queue_healthy -or (Call $port 'agent_status').state -eq 'running'){throw 'agent_not_idle'}
 $listeners=@(Get-NetTCPConnection -LocalPort $port -State Listen)
 if($listeners.Count -ne 1){throw 'listener_not_unique'}
 $cim=Get-CimInstance Win32_Process -Filter ('ProcessId='+$listeners[0].OwningProcess)
 $installed=$cim.ExecutablePath
 if(!$installed.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetFileName($installed) -ine 'jarvis-mcp.exe' -or (Hash $installed) -ine $ExpectedPreviousHash){throw 'working_binary_changed'}
 $startExe=$installed;$startArgs='-listen 127.0.0.1:'+$port
 if($managed){
  if($manifest.agent_version -ne $ExpectedPreviousVersion -or $manifest.agent_sha256 -ine $ExpectedPreviousHash){throw 'working_manifest_changed'}
  $startExe=Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
  $startScript=Join-Path $root 'Scripts\Start.ps1'
  $startArgs='-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+$startScript+'" -HomeDir "'+$root+'"'
  if(!(Test-Path -LiteralPath $startScript)){throw 'configured_start_script_missing'}
 }
 Report ('PROFILE '+(@{agent=$installed;start_executable=$startExe;start_arguments=$startArgs;installation_manifest=$managed;port=$port}|ConvertTo-Json -Compress))
 New-Item -ItemType Directory -Force $backup|Out-Null
 $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
 & "$env:SystemRoot\System32\icacls.exe" $backup /inheritance:r /grant:r ('*'+$sid+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F'|Out-Null
 if($LASTEXITCODE -ne 0){throw 'backup_acl_failed'}
 $previous=Join-Path $backup 'jarvis-mcp-previous.exe';CopyChecked $installed $previous
 $configHashes=@{}
 foreach($f in Get-ChildItem (Join-Path $root 'Config') -File){
  if($f.Name -like '*.lock'){continue}
  $configHashes[$f.Name]=Hash $f.FullName
  CopyChecked $f.FullName (Join-Path $backup ('Config\'+$f.Name))
 }
 $state=$env:JARVIS_STATE_DIR;if(!$state){$state=Join-Path $root 'State\runtime-v1'}
 $saved=Join-Path $backup 'state-snapshot'
 foreach($f in Get-ChildItem $state -File -Recurse){
  if($f.Name -eq 'agent.lock'){continue}
  CopyChecked $f.FullName (Join-Path $saved $f.FullName.Substring($state.Length).TrimStart('\'))
 }
 $usagePath=Join-Path $root 'State\tool-usage.json'
 if(Test-Path -LiteralPath $usagePath){CopyChecked $usagePath (Join-Path $backup 'tool-usage.json')}
 Report ('BACKUP_VERIFIED '+$backup)
 if(Get-NetTCPConnection -LocalPort 8767 -State Listen -ErrorAction SilentlyContinue){throw 'rehearsal_port_busy'}
 $testHome=Join-Path $root ('Temp\rehearsal-'+[guid]::NewGuid().ToString('N'));New-Item -ItemType Directory -Force (Join-Path $testHome 'Config')|Out-Null
 Copy-Item -LiteralPath $saved -Destination (Join-Path $testHome 'State') -Recurse
 $token=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
 $projects=$env:JARVIS_PROJECTS_ROOT
 if(!$projects){throw 'projects_root_missing'}
 $envText='JARVIS_STATE_DIR='+(Join-Path $testHome 'State')+[Environment]::NewLine+'JARVIS_PROJECTS_ROOT='+$projects+[Environment]::NewLine+'JARVIS_CODEX_ENABLED=0'
 [IO.File]::WriteAllText((Join-Path $testHome 'Config\agent.env'),$envText,(New-Object Text.UTF8Encoding($false)))
 [IO.File]::WriteAllText((Join-Path $testHome 'Config\secrets.env'),('JARVIS_MCP_TOKEN='+$token),(New-Object Text.UTF8Encoding($false)))
 foreach($item in @(@{exe=$Candidate;version=$TargetVersion},@{exe=$previous;version=$ExpectedPreviousVersion})){
  $script:probe=StartProbe $item.exe $testHome
  WaitHealth 8767 $item.version $probe.process
  if((Call 8767 'machine_info' @{} $token).machine_id -cne $ExpectedMachine){throw 'rehearsal_identity_changed'}
  StopProbe
 }
 Report 'ROLLBACK_REHEARSAL_PASS'
 if($RehearsalOnly){Report 'PASS rehearsal_only';return}
 foreach($name in $configHashes.Keys){if($name -ne 'running.json' -and (Hash (Join-Path $root ('Config\'+$name))) -ine $configHashes[$name]){throw 'configuration_changed_before_stop'}}
 $promotion=$true;StopAgent
 ReplaceBinary $Candidate $ExpectedHash
 if($managed){
  $manifest.agent_version=$TargetVersion;$manifest.agent_sha256=$ExpectedHash.ToLowerInvariant()
  $manifest|Add-Member -NotePropertyName agent_source_commit -NotePropertyValue $SourceCommit -Force
  $pending=Join-Path $testHome 'installation.next.json'
  [IO.File]::WriteAllText($pending,($manifest|ConvertTo-Json -Depth 20),(New-Object Text.UTF8Encoding($false)))
  [IO.File]::Replace($pending,$manifestPath,(Join-Path $backup 'manifest-before-promotion.json'))
 }
 $newProcess=StartConfigured
 WaitHealth $port $TargetVersion $newProcess
 $after=Call $port 'machine_info'
 if($after.machine_id -cne $ExpectedMachine -or $after.version -ne $TargetVersion -or (Hash $installed) -ine $ExpectedHash){throw 'installed_identity_or_hash_mismatch'}
 if($managed -and $manifest.remote_configured){
  $ready=$false
  for($i=0;$i -lt 80;$i++){
   $running=[IO.File]::ReadAllText((Join-Path $root 'Config\running.json'))|ConvertFrom-Json
   if($running.tunnel){$p=Get-Process -Id $running.tunnel.pid -ErrorAction SilentlyContinue;if($p -and $p.Path -ieq (Join-Path $root 'Agent\cloudflared.exe') -and $p.StartTime.ToUniversalTime().Ticks.ToString() -ceq $running.tunnel.started_ticks){$ready=$true;break}}
   Start-Sleep -Milliseconds 200
  }
  if(!$ready){throw 'configured_tunnel_not_ready'}
 }
 foreach($name in $configHashes.Keys){
  if($name -in @('installation.json','running.json')){continue}
  if((Hash (Join-Path $root ('Config\'+$name))) -ine $configHashes[$name]){throw 'durable_configuration_changed'}
 }
 $m=Call $port 'maintenance_status' @{machine_id=$ExpectedMachine}
 if($m.error -or $m.interval_hours -ne 24 -or !$m.last_check){throw 'maintenance_unhealthy'}
 @{version=$TargetVersion;source_commit=$SourceCommit;sha256=$ExpectedHash;machine_id=$ExpectedMachine;backup=$backup;start_executable=$startExe;start_arguments=$startArgs;checked_at=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json -Depth 5|Set-Content (Join-Path $backup 'installed-receipt.json') -Encoding UTF8
 Report ('INSTALLED_SHA256 '+$ExpectedHash);Report 'PASS common_batch_update_identity_config_maintenance'
}catch{
 Report ('ERROR '+$_.Exception.Message)
 if($promotion){
  try{StopAgent;ReplaceBinary $previous $ExpectedPreviousHash;if($managed){Copy-Item -LiteralPath (Join-Path $backup 'Config\installation.json') -Destination $manifestPath -Force};$newProcess=StartConfigured;WaitHealth $port $ExpectedPreviousVersion $newProcess;Report 'ROLLBACK_RESTORED'}catch{Report ('ROLLBACK_ERROR '+$_.Exception.Message)}
 }
 exit 1
}finally{
 StopProbe
 if($testHome -and (Test-Path -LiteralPath $testHome)){
  Remove-Item -LiteralPath $testHome -Recurse -Force
  if(Test-Path -LiteralPath $testHome){throw 'rehearsal_cleanup_failed'}
 }
 if($lock){$lock.Dispose()}
}
