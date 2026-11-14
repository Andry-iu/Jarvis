[CmdletBinding()]
param(
 [Parameter(Mandatory=$true)][string]$HomeDir,
 [Parameter(Mandatory=$true)][string]$SourceCommit,
 [Parameter(Mandatory=$true)][string]$TargetVersion,
 [Parameter(Mandatory=$true)][string]$RequestId,
 [Parameter(Mandatory=$true)][string]$ExpectedMachine,
 [Parameter(Mandatory=$true)][string]$ExpectedPreviousVersion,
 [Parameter(Mandatory=$true)][string]$ExpectedPreviousHash,
 [switch]$PrepareOnly
)
# Identical target-owned runner at every routing depth. No gateway builds/copies binaries.
$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue'
if($SourceCommit -cnotmatch '^[a-f0-9]{40}$' -or $TargetVersion -notmatch '^\d+\.\d+\.\d+$' -or $RequestId -notmatch '^[A-Za-z0-9][A-Za-z0-9_-]{15,63}$' -or $ExpectedMachine -notmatch '^[a-f0-9]{32}$' -or $ExpectedPreviousVersion -notmatch '^\d+\.\d+\.\d+$' -or $ExpectedPreviousHash -notmatch '^[a-fA-F0-9]{64}$'){throw 'invalid_source_update_contract'}
$root=[IO.Path]::GetFullPath($HomeDir).TrimEnd('\')
$statusDir=Join-Path $root ('State\source-updates\'+$RequestId);$statusFile=Join-Path $statusDir 'status.json'
$work=Join-Path $root ('Temp\source-update-'+$RequestId);$release=Join-Path $root ('Releases\'+$TargetVersion+'-'+$RequestId)
$log=Join-Path $root ('Logs\diagnostics\source-update-'+$RequestId+'.log');$buildLog=Join-Path $root ('Logs\diagnostics\source-build-'+$RequestId+'.log')
$lock=$null;$phase='starting';$backup='';$installedHash='';$failure='';$cleanup=$false;$ownsWork=$false;$admitted=$false
$record=@{request_id=$RequestId;machine_id=$ExpectedMachine;source_commit=$SourceCommit;target_version=$TargetVersion;phase=$phase;updated_at='';installed_sha256='';backup='';error='';cleanup_verified=$false}
function Hash([string]$p){(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()}
function Report([string]$text){$text|Add-Content -LiteralPath $log -Encoding UTF8}
function Save-Status([string]$value){
 $script:phase=$value;$record.phase=$value;$record.updated_at=[DateTime]::UtcNow.ToString('o')
 $record.installed_sha256=$script:installedHash;$record.backup=$script:backup;$record.error=$script:failure;$record.cleanup_verified=$script:cleanup
 $pending=$statusFile+'.pending-'+[guid]::NewGuid().ToString('N')
 [IO.File]::WriteAllText($pending,($record|ConvertTo-Json -Depth 5),(New-Object Text.UTF8Encoding($false)))
 if(Test-Path -LiteralPath $statusFile){[IO.File]::Replace($pending,$statusFile,$null)}else{[IO.File]::Move($pending,$statusFile)}
 Report ('PHASE '+$value)
}
function Local-Call([string]$name,[hashtable]$arguments=@{}){
 $port=8765;$manifest=Join-Path $root 'Config\installation.json'
 if(Test-Path -LiteralPath $manifest){$port=[int]((Get-Content $manifest -Raw|ConvertFrom-Json).port)}
 $body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name=$name;arguments=$arguments}}|ConvertTo-Json -Depth 15 -Compress
 $r=Invoke-RestMethod ('http://127.0.0.1:'+$port+'/mcp') -Method Post -Headers @{Authorization=('Bearer '+$env:JARVIS_MCP_TOKEN);Accept='application/json, text/event-stream'} -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 20
 if($r.error -or $r.result.isError){throw ('local_check_failed_'+$name)}
 $r.result.content[0].text|ConvertFrom-Json
}
function Run-Native([string]$exe,[string[]]$nativeArguments){
 $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
 $output=& $exe @nativeArguments 2>&1;$code=$LASTEXITCODE;$ErrorActionPreference=$old
 $output|Out-File -LiteralPath $log -Append -Encoding UTF8
 if($code -ne 0){throw 'native_command_failed'}
}
function Download-Checked([string]$url,[string]$destination,[string]$sha){
 if($sha -notmatch '^[a-fA-F0-9]{64}$'){throw 'download_digest_missing'}
 if(Test-Path -LiteralPath $destination){if((Hash $destination) -eq $sha.ToLowerInvariant()){return};throw 'cached_download_hash_mismatch'}
 $part=Join-Path $work ('download-'+[guid]::NewGuid().ToString('N'))
 Invoke-WebRequest $url -UseBasicParsing -OutFile $part -TimeoutSec 600
 if((Hash $part) -ine $sha){throw 'official_download_hash_mismatch'}
 [IO.File]::Move($part,$destination)
}
function Ensure-Git{
 $existing=Get-Command git.exe -ErrorAction SilentlyContinue
 if($existing){Run-Native $existing.Source @('--version');return $existing.Source}
 $toolRoot=Join-Path $root 'Tools\SourceUpdate';New-Item -ItemType Directory -Force $toolRoot|Out-Null
 $gitRoot=Join-Path $toolRoot 'MinGit-2.56.0.4';$git=Join-Path $gitRoot 'cmd\git.exe';$receipt=Join-Path $toolRoot 'git-tool.json'
 if(Test-Path -LiteralPath $git){
  if(!(Test-Path $receipt) -or (Get-Content $receipt -Raw|ConvertFrom-Json).exe_sha256 -ine (Hash $git)){throw 'managed_git_changed'}
  Run-Native $git @('--version');return $git
 }
 $sha='e3bf78351fe777fba47ddee5950082f8a98bb0f02604fdc7ba3ee2cb68ffa877'
 $url='https://github.com/git-for-windows/git/releases/download/v2.56.0.windows.4/MinGit-2.56.0.4-64-bit.zip'
 $archive=Join-Path $toolRoot 'MinGit-2.56.0.4-64-bit.zip';Download-Checked $url $archive $sha
 $staging=Join-Path $work 'mingit';[IO.Compression.ZipFile]::ExtractToDirectory($archive,$staging)
 if(!(Test-Path (Join-Path $staging 'cmd\git.exe'))){throw 'git_archive_layout_invalid'}
 Run-Native (Join-Path $staging 'cmd\git.exe') @('--version')
 if(Test-Path $gitRoot){throw 'managed_git_destination_exists'}
 Move-Item -LiteralPath $staging -Destination $gitRoot
 @{url=$url;archive_sha256=$sha;exe_sha256=(Hash $git)}|ConvertTo-Json|Set-Content $receipt -Encoding UTF8
 return $git
}
function Ensure-Go{
 $existing=Get-Command go.exe -ErrorAction SilentlyContinue
 if($existing){$v=(& $existing.Source version 2>$null);if($LASTEXITCODE -eq 0 -and $v -ceq 'go version go1.27.0 windows/amd64'){return $existing.Source}}
 # Documented successful profile, side-by-side; never replace the system SDK.
 $toolRoot=Join-Path $root 'Tools\SourceUpdate';New-Item -ItemType Directory -Force $toolRoot|Out-Null
 $sha='f0c0a0d33ba94f4d2c5dbc887334ce678b21813504ddb3aafcb06e60a5a667c4'
 $archive=Join-Path $toolRoot 'go1.27.0.windows-amd64.zip';$oldCache=Join-Path $root 'Tools\Cache\go1.27.0.windows-amd64.zip'
 if(!(Test-Path $archive) -and (Test-Path $oldCache) -and (Hash $oldCache) -eq $sha){Copy-Item -LiteralPath $oldCache -Destination $archive}
 Download-Checked 'https://go.dev/dl/go1.27.0.windows-amd64.zip' $archive $sha
 $goRoot=Join-Path $toolRoot 'Go-1.27.0';$go=Join-Path $goRoot 'go\bin\go.exe';$receipt=Join-Path $toolRoot 'go-tool.json'
 if(Test-Path $go){
  if(!(Test-Path $receipt) -or (Get-Content $receipt -Raw|ConvertFrom-Json).exe_sha256 -ine (Hash $go)){throw 'managed_go_changed'}
 }else{
  $staging=Join-Path $work 'go-sdk';[IO.Compression.ZipFile]::ExtractToDirectory($archive,$staging)
  if(Test-Path $goRoot){throw 'managed_go_destination_exists'}
  Move-Item -LiteralPath $staging -Destination $goRoot
  @{url='https://go.dev/dl/go1.27.0.windows-amd64.zip';archive_sha256=$sha;exe_sha256=(Hash $go)}|ConvertTo-Json|Set-Content $receipt -Encoding UTF8
 }
 if((& $go version) -cne 'go version go1.27.0 windows/amd64' -or $LASTEXITCODE -ne 0){throw 'documented_go1270_required'}
 return $go
}
try{
 New-Item -ItemType Directory -Force $statusDir,(Split-Path $log -Parent)|Out-Null
 $lock=[IO.File]::Open((Join-Path (Split-Path $statusDir -Parent) 'runner.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
 if(Test-Path $statusFile){
  $old=Get-Content $statusFile -Raw|ConvertFrom-Json
  if($old.request_id -cne $RequestId -or $old.machine_id -cne $ExpectedMachine -or $old.source_commit -cne $SourceCommit -or $old.target_version -cne $TargetVersion -or $old.phase -ne 'dispatching'){throw 'source_update_not_replayable'}
 }else{
  # Compatibility bootstrap on older agents uses this exact runner as well.
  $active=Join-Path (Split-Path $statusDir -Parent) 'active.json'
  if(Test-Path $active){
   $a=Get-Content $active -Raw|ConvertFrom-Json
   $prior=Get-Content (Join-Path (Split-Path $statusDir -Parent) ($a.request_id+'\status.json')) -Raw|ConvertFrom-Json
   if($prior.phase -notin @('completed','failed','prepared') -or !$prior.cleanup_verified){throw 'another_update_active_or_uncertain'}
  }
  @{request_id=$RequestId}|ConvertTo-Json|Set-Content $active -Encoding UTF8
 }
 $admitted=$true;Save-Status 'checking'
 $principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
 if(!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'elevated_agent_required'}
 $info=Local-Call 'machine_info'
 if($info.machine_id -cne $ExpectedMachine -or $info.version -cne $ExpectedPreviousVersion){throw 'working_identity_or_version_changed'}
 $installed=Join-Path $root 'Agent\jarvis-mcp.exe';if((Hash $installed) -ine $ExpectedPreviousHash){throw 'working_binary_changed'}
 $queue=Local-Call 'queue_list' @{machine_id=$ExpectedMachine;limit=200}
 if(@($queue.operations).Count -or !$queue.queue_healthy -or (Local-Call 'agent_status').state -eq 'running'){throw 'agent_not_idle'}
 if((Test-Path $work) -or (Test-Path $release)){throw 'source_update_work_already_exists'}
 New-Item -ItemType Directory $work,$release|Out-Null;$ownsWork=$true
 $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
 foreach($private in @($statusDir,$work,$release)){
  & "$env:SystemRoot\System32\icacls.exe" $private /inheritance:r /grant:r ('*'+$sid+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F'|Out-Null
  if($LASTEXITCODE -ne 0){throw 'source_update_acl_failed'}
 }
 Add-Type -AssemblyName System.IO.Compression.FileSystem
 [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
 Save-Status 'checking_git'
 try{
  $probe=Invoke-WebRequest 'https://github.com/Andry-iu/Jarvis.git/info/refs?service=git-upload-pack' -UseBasicParsing -TimeoutSec 30
  if($probe.StatusCode -ne 200 -or $probe.Headers['Content-Type'] -notlike 'application/x-git-upload-pack-advertisement*'){throw 'unexpected_git_response'}
 }catch{throw 'git_unreachable'}
 $env:TEMP=$work;$env:TMP=$work;$env:GIT_TERMINAL_PROMPT='0';$env:GCM_INTERACTIVE='Never';$env:GIT_CONFIG_NOSYSTEM='1';$env:GIT_CONFIG_GLOBAL='NUL'
 Save-Status 'preparing_tools';$git=Ensure-Git;$go=Ensure-Go
 $env:PATH=(Split-Path $git -Parent)+';'+(Split-Path $go -Parent)+';'+$env:PATH
 Save-Status 'downloading_source'
 $source=Join-Path $work 'source';Run-Native $git @('init',$source)
 Run-Native $git @('-C',$source,'config','core.autocrlf','false')
 Run-Native $git @('-C',$source,'sparse-checkout','init','--cone')
 Run-Native $git @('-C',$source,'sparse-checkout','set','windows-mcp-agent','docs/mcp-agent')
 Run-Native $git @('-C',$source,'-c','credential.helper=','fetch','--depth=1','https://github.com/Andry-iu/Jarvis.git',$SourceCommit)
 Run-Native $git @('-C',$source,'checkout','--detach','FETCH_HEAD')
 if((& $git -C $source rev-parse HEAD).Trim() -cne $SourceCommit -or $LASTEXITCODE -ne 0){throw 'downloaded_commit_mismatch'}
 $agent=Join-Path $source 'windows-mcp-agent';$main=[IO.File]::ReadAllText((Join-Path $agent 'main.go'))
 if($main -notmatch ('(?m)^const version = "'+[regex]::Escape($TargetVersion)+'"\s*$')){throw 'source_version_mismatch'}
 Save-Status 'building';$candidate=Join-Path $release 'jarvis-mcp.exe';$env:JARVIS_TEST_PAIRING_UI='0'
 & "$PSHOME\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File (Join-Path $agent 'scripts\build-runtime.ps1') -GoExe $go -GitExe $git -SourceCommit $SourceCommit -Candidate $candidate -Log $buildLog -SourceDirectory $agent
 if($LASTEXITCODE -ne 0){throw 'source_test_vet_build_failed'}
 $validation=Get-Content ($candidate+'.validation.json') -Raw|ConvertFrom-Json
 if($validation.source_commit -cne $SourceCommit -or $validation.sha256 -ine (Hash $candidate)){throw 'build_receipt_mismatch'}
 $candidateHash=Hash $candidate;$updater=Join-Path $release 'update-agent.ps1'
 Copy-Item -LiteralPath (Join-Path $agent 'scripts\update-agent.ps1') -Destination $updater
 if((Hash $updater) -cne (Hash (Join-Path $agent 'scripts\update-agent.ps1'))){throw 'updater_copy_changed'}
 if($PrepareOnly){Save-Status 'prepared';return}
 Save-Status 'promoting'
 & "$PSHOME\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $updater -HomeDir $root -Candidate $candidate -ExpectedHash $candidateHash -ExpectedMachine $ExpectedMachine -ExpectedPreviousVersion $ExpectedPreviousVersion -ExpectedPreviousHash $ExpectedPreviousHash -TargetVersion $TargetVersion -SourceCommit $SourceCommit
 $updaterExit=$LASTEXITCODE
 $canonical=Get-Content (Join-Path $root 'Logs\diagnostics\self-update.log') -Raw;$m=[regex]::Match($canonical,'BACKUP_VERIFIED ([^\r\n]+)')
 if($m.Success){$backup=$m.Groups[1].Value.Trim()}
 if($updaterExit -ne 0){throw 'canonical_update_failed'}
 if(!$m.Success -or $canonical -notmatch 'PASS common_batch_update_identity_config_maintenance'){throw 'canonical_receipt_missing'}
 $backup=$m.Groups[1].Value.Trim();$receipt=Get-Content (Join-Path $backup 'installed-receipt.json') -Raw|ConvertFrom-Json;$live=Local-Call 'machine_info'
 if($receipt.source_commit -cne $SourceCommit -or $receipt.version -cne $TargetVersion -or $receipt.machine_id -cne $ExpectedMachine -or $receipt.sha256 -ine $candidateHash -or $live.machine_id -cne $ExpectedMachine -or $live.version -cne $TargetVersion -or (Hash $installed) -ine $candidateHash){throw 'post_update_verification_failed'}
 $installedHash=$candidateHash;Save-Status 'verifying_cleanup'
}catch{$failure=$_.Exception.Message;Report ('ERROR '+$failure);$phase='failed'}
finally{
 if($lock){
  if($admitted){
   try{
    if($ownsWork -and (Test-Path $work)){Remove-Item -LiteralPath $work -Recurse -Force}
    if($ownsWork -and (Test-Path $work)){throw 'source_work_cleanup_failed'}
    $probePaths=@((Join-Path $release 'jarvis-mcp.exe'));if($backup){$probePaths+=Join-Path $backup 'jarvis-mcp-previous.exe'}
    if(@(Get-CimInstance Win32_Process|Where-Object {$_.ExecutablePath -in $probePaths}).Count){throw 'source_update_probe_remains'}
    $rules=@(Get-NetFirewallApplicationFilter -PolicyStore ActiveStore|Where-Object {$_.Program -in $probePaths}|Get-NetFirewallRule)
    foreach($rule in $rules){Remove-NetFirewallRule -Name $rule.Name}
    if(@(Get-NetFirewallApplicationFilter -PolicyStore ActiveStore|Where-Object {$_.Program -in $probePaths}).Count){throw 'source_update_probe_rule_remains'}
    $ownedRunner=Join-Path $statusDir 'runner.ps1'
    if(Test-Path -LiteralPath $ownedRunner){Remove-Item -LiteralPath $ownedRunner -Force}
    if(Test-Path -LiteralPath $ownedRunner){throw 'owned_runner_cleanup_failed'}
    $cleanup=$true
   }catch{$failure='cleanup_verification_failed';$phase='failed';Report 'ERROR cleanup_verification_failed'}
   if(!$failure){if($PrepareOnly){$phase='prepared'}else{$phase='completed'}}else{$phase='failed'}
   Save-Status $phase
  }
  $lock.Dispose()
  if($failure){exit 1}
 }
}
if($failure){exit 1}
