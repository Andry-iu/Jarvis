param(
 [Parameter(Mandatory=$true)][string]$HomeDir,
 [Parameter(Mandatory=$true)][string]$ExpectedMachine,
 [Parameter(Mandatory=$true)][string]$ExpectedHash,
 [string]$ExpectedVersion='0.8.3',
 [string]$SourceDirectory=(Split-Path $PSScriptRoot -Parent),
 [string]$TaskName='',
 [string]$DeviceName='',
 [int]$Port=8765
)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($HomeDir).TrimEnd('\')
$principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'administrator_required'}
$manifestPath=Join-Path $root 'Config\installation.json'
$managed=Test-Path $manifestPath
$target=Join-Path $root 'Agent\jarvis-mcp.exe'
$legacy=Join-Path $root 'jarvis-mcp.exe'
$backup=Join-Path $root ('Backups\layout-'+[guid]::NewGuid().ToString('N'))
$log=Join-Path $root 'Logs\diagnostics\layout-alignment.log'
$oldPath=$null;$stopped=$false;$registered=$false;$taskExisted=$false;$rules=@();$prior=$null;$lock=$null;$created=@()
function Report([string]$text){$text|Add-Content $log -Encoding UTF8}
function Hash([string]$path){(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Call([string]$name,[hashtable]$a=@{}){
 $token=$env:JARVIS_MCP_TOKEN
 if(-not $token){$line=[IO.File]::ReadAllLines((Join-Path $root 'Config\secrets.env'))|Where-Object {$_ -match '^JARVIS_MCP_TOKEN='};if(@($line).Count -ne 1){throw 'credential_unavailable'};$token=$line.Substring('JARVIS_MCP_TOKEN='.Length)}
 $body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name=$name;arguments=$a}}|ConvertTo-Json -Depth 12 -Compress
 $r=Invoke-RestMethod ('http://127.0.0.1:'+$port+'/mcp') -Method Post -ContentType 'application/json' -Headers @{Authorization=('Bearer '+$token);Accept='application/json, text/event-stream'} -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 10
 if($r.PSObject.Properties['error'] -or $r.result.isError){throw ('mcp_failed_'+$name)}
 $r.result.content[0].text|ConvertFrom-Json
}
function WaitRunner{
 if(-not $managed -and -not $registered){return}
 for($i=0;$i -lt 100;$i++){try{$g=[IO.File]::Open((Join-Path $root 'Config\runner.lock'),'OpenOrCreate','ReadWrite','None');$g.Dispose();return}catch{Start-Sleep -Milliseconds 100}}
 throw 'runner_lock_busy'
}
function WaitAgent{
 for($i=0;$i -lt 100;$i++){
  try{
   $info=Call 'machine_info'
   $tcp=@(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
   if($tcp.Count -eq 1){$p=Get-Process -Id $tcp[0].OwningProcess;if($p.Path -ieq $target -and $info.machine_id -ceq $ExpectedMachine -and $info.version -eq $ExpectedVersion -and $info.privileges.elevated -eq $true -and (Hash $target) -ceq $ExpectedHash){return $info}}
  }catch{}
  Start-Sleep -Milliseconds 200
 }
 throw 'aligned_admin_agent_not_verified'
}
try{
 foreach($dir in @('Agent','Scripts','Backups','Logs\diagnostics')){New-Item -ItemType Directory -Force (Join-Path $root $dir)|Out-Null}
 'START layout_alignment'|Set-Content $log -Encoding UTF8
 foreach($path in @($root,(Join-Path $root 'Agent'),(Join-Path $root 'Config'),(Join-Path $root 'Scripts'))){if((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'linked_install_path_refused'}}
 $lock=[IO.File]::Open((Join-Path $root 'Config\update.lock'),'OpenOrCreate','ReadWrite','None')
 if($managed){$prior=Get-Content $manifestPath -Raw|ConvertFrom-Json;$port=[int]$prior.port}
 $info=Call 'machine_info'
 if(-not $DeviceName){$DeviceName=$info.name}
 if($info.machine_id -cne $ExpectedMachine -or $info.version -ne $ExpectedVersion -or $info.privileges.elevated -ne $true){throw 'working_identity_version_admin_changed'}
 $q=Call 'queue_list' @{machine_id=$ExpectedMachine;limit=200}
 if(@($q.operations).Count -or -not $q.queue_healthy -or (Call 'agent_status').state -eq 'running'){throw 'agent_not_idle'}
 $tcp=@(Get-NetTCPConnection -LocalPort $port -State Listen)
 if($tcp.Count -ne 1){throw 'listener_not_unique'}
 $proc=Get-Process -Id $tcp[0].OwningProcess;$oldPath=$proc.Path
 if($oldPath -ine $target -and $oldPath -ine $legacy){throw 'unexpected_agent_path'}
 if((Hash $oldPath) -cne $ExpectedHash){throw 'working_binary_changed'}
 if($oldPath -ine $target -and (Test-Path $target)){throw 'target_exists_no_overwrite'}
 if(-not $TaskName){if($managed){$TaskName=$prior.task_name}else{$TaskName='JarvisAgent-'+$ExpectedMachine.Substring(0,12)}}
 $oldTask=Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue;$taskExisted=$null -ne $oldTask
 New-Item -ItemType Directory $backup|Out-Null
 $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
 & "$env:SystemRoot\System32\icacls.exe" $backup /inheritance:r /grant:r ('*'+$sid+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F'|Out-Null
 if($LASTEXITCODE -ne 0){throw 'backup_acl_failed'}
 Copy-Item -LiteralPath $oldPath -Destination (Join-Path $backup 'jarvis-mcp.exe')
 if((Hash (Join-Path $backup 'jarvis-mcp.exe')) -cne $ExpectedHash){throw 'backup_hash_failed'}
 $configHashes=@{}
 foreach($f in Get-ChildItem (Join-Path $root 'Config') -File){if($f.Name -notin @('update.lock','runner.lock','running.json','installation.json')){$configHashes[$f.Name]=Hash $f.FullName}}
 if($managed){Copy-Item $manifestPath (Join-Path $backup 'installation.json')}
 if($taskExisted){Export-ScheduledTask -TaskName $TaskName|Set-Content (Join-Path $backup 'task.xml') -Encoding Unicode}
 if($oldPath -ine $target){
  foreach($name in @('Common.ps1','Start.ps1','Stop.ps1','update-agent.ps1')){
   $dest=Join-Path $root ('Scripts\'+$name)
   if(Test-Path $dest){throw ('legacy_script_already_exists_'+$name)}
   $src=if($name -eq 'update-agent.ps1'){Join-Path $SourceDirectory 'scripts\update-agent.ps1'}else{Join-Path $SourceDirectory ('installer\'+$name)}
   Copy-Item $src $dest;$created+=$dest
  }
  Copy-Item -LiteralPath $oldPath -Destination $target;$created+=$target
  if((Hash $target) -cne $ExpectedHash){throw 'copy_hash_failed'}
  $settings=@{format=1;installer_version=$ExpectedVersion;agent_version=$ExpectedVersion;agent_sha256=$ExpectedHash;device_name=$DeviceName;plugin_name=('Jarvis-'+$DeviceName);home=$root;port=$port;projects_root=$env:JARVIS_PROJECTS_ROOT;computer=$env:COMPUTERNAME;user_sid=$sid;task_name=$TaskName;task_registered=$true;remote_configured=$false;public_endpoint='';connection='existing_external_gateway';installed_at=[DateTime]::UtcNow.ToString('o')}
  [IO.File]::WriteAllText($manifestPath,($settings|ConvertTo-Json -Depth 8),(New-Object Text.UTF8Encoding($false)));$created+=$manifestPath
  $rules=@(Get-NetFirewallApplicationFilter -Program $oldPath -ErrorAction SilentlyContinue)
  $rules|Select-Object InstanceID,Program|ConvertTo-Json|Set-Content (Join-Path $backup 'firewall-paths.json') -Encoding UTF8
 }
 $action=New-ScheduledTaskAction -Execute "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -Argument ('-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "'+(Join-Path $root 'Scripts\Start.ps1')+'" -HomeDir "'+$root+'"')
 $trigger=New-ScheduledTaskTrigger -AtLogOn -User ([Security.Principal.WindowsIdentity]::GetCurrent().Name)
 $taskPrincipal=New-ScheduledTaskPrincipal -UserId $sid -LogonType Interactive -RunLevel Highest
 $options=New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew
 Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $taskPrincipal -Settings $options -Force|Out-Null;$registered=$true
 if((Get-ScheduledTask -TaskName $TaskName).Principal.RunLevel -ne 'Highest'){throw 'startup_not_highest'}
 Report ('BACKUP_VERIFIED '+$backup)
 Stop-Process -Id $proc.Id -Force
 $stopped=$true
 if(-not $proc.WaitForExit(10000)){throw 'stop_timeout'}
 WaitRunner
 foreach($rule in $rules){$rule|Set-NetFirewallApplicationFilter -Program $target|Out-Null}
 Start-ScheduledTask -TaskName $TaskName
 $after=WaitAgent
 foreach($name in $configHashes.Keys){if((Hash (Join-Path $root ('Config\'+$name))) -cne $configHashes[$name]){throw ('configuration_changed_'+$name)}}
 if($oldPath -ine $target){
  $bat=Join-Path $root 'jarvis-mcp-as-admin.bat'
  if(Test-Path $bat){Copy-Item $bat (Join-Path $backup 'jarvis-mcp-as-admin.bat');$text=[IO.File]::ReadAllText($bat);$text=[regex]::Replace($text,'(?i)(?<!Agent\\)jarvis-mcp\.exe','Agent\jarvis-mcp.exe');[IO.File]::WriteAllText($bat,$text,(New-Object Text.UTF8Encoding($false)))}
  Remove-Item -LiteralPath $legacy
  if(Test-Path $legacy){throw 'legacy_binary_remains'}
 }
 Report (@{machine_id=$after.machine_id;version=$after.version;pid=$after.privileges.pid;elevated=$after.privileges.elevated;path=$target;task_name=$TaskName;run_level='Highest';backup=$backup}|ConvertTo-Json -Compress)
 Report 'PASS AGENT_LAYOUT_STARTUP_ADMIN_VERIFIED'
}catch{
 Report ('FAIL '+$_.Exception.Message)
 if($oldPath -and $oldPath -ine $target){
  try{
   $tcp=@(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
   if($tcp.Count -eq 1){$p=Get-Process -Id $tcp[0].OwningProcess;if($p.Path -ieq $target){Stop-Process -Id $p.Id -Force;$null=$p.WaitForExit(10000)}}
   WaitRunner
   foreach($rule in $rules){$rule|Set-NetFirewallApplicationFilter -Program $oldPath|Out-Null}
   if(Test-Path $manifestPath){Remove-Item $manifestPath}
   if($registered -and -not $taskExisted){Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false}
   if(-not (Test-Path $legacy)){Copy-Item (Join-Path $backup 'jarvis-mcp.exe') $legacy}
   $psi=New-Object Diagnostics.ProcessStartInfo;$psi.FileName=$legacy;$psi.Arguments='-listen 127.0.0.1:'+$port;$psi.WorkingDirectory=$root;$psi.UseShellExecute=$false;$psi.CreateNoWindow=$true;$null=[Diagnostics.Process]::Start($psi)
   foreach($path in $created){if(Test-Path -LiteralPath $path){Remove-Item -LiteralPath $path}}
   Report 'ROLLBACK_OLD_AGENT_STARTED_VERIFY_MCP'
  }catch{Report ('ROLLBACK_FAIL '+$_.Exception.Message)}
 }elseif($stopped){
  try{Start-ScheduledTask -TaskName $TaskName;$null=WaitAgent;Report 'ROLLBACK_SAME_AGENT_RESTARTED'}catch{Report ('ROLLBACK_FAIL '+$_.Exception.Message)}
 }
 throw
}finally{if($lock){$lock.Dispose()}}
