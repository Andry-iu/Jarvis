param([Parameter(Mandatory=$true)][string]$Package,[Parameter(Mandatory=$true)][string]$PreviousCandidate,[ValidateSet('0.8.0','0.8.1','0.8.2')][string]$PreviousVersion='0.8.0',[Parameter(Mandatory=$true)][string]$Log)
$ErrorActionPreference='Stop'
$temp=Join-Path ([IO.Path]::GetTempPath()) ('jarvis-update-test-'+[guid]::NewGuid().ToString('N'))
$testHome=Join-Path $temp 'Installed';$oldPackage=Join-Path $temp 'OldPackage'
function Report($text){$text|Add-Content $Log -Encoding UTF8}
try{
 New-Item -ItemType Directory -Force $oldPackage|Out-Null
 Copy-Item -Path (Join-Path $Package '*') -Destination $oldPackage
 Copy-Item -LiteralPath $PreviousCandidate -Destination (Join-Path $oldPackage 'jarvis-mcp.exe') -Force
 $install=Join-Path $oldPackage 'Install.ps1'
 $text=[IO.File]::ReadAllText($install).Replace('0.8.3',$PreviousVersion)
 [IO.File]::WriteAllText($install,$text,(New-Object Text.UTF8Encoding($false)))
 $old=Get-Content (Join-Path $oldPackage 'package.json') -Raw|ConvertFrom-Json
 $old.installer_version=$PreviousVersion;$old.agent_version=$PreviousVersion;$old.agent_sha256=(Get-FileHash (Join-Path $oldPackage 'jarvis-mcp.exe')).Hash.ToLowerInvariant()
 $old.files=@(Get-ChildItem $oldPackage -File|Where-Object {$_.Name -ne 'package.json'}|ForEach-Object {@{path=$_.Name;sha256=(Get-FileHash $_.FullName).Hash.ToLowerInvariant();size_bytes=$_.Length}})
 [IO.File]::WriteAllText((Join-Path $oldPackage 'package.json'),($old|ConvertTo-Json -Depth 12),(New-Object Text.UTF8Encoding($false)))
 & $install -NonInteractive -DeviceName 'Bootstrap update test' -InstallDir $testHome -Connection local -Port 19865 -PeerPort 19866
 . (Join-Path $testHome 'Scripts\Common.ps1')
 $before=$null
 for($i=0;$i -lt 100;$i++){try{$before=Invoke-LocalTool $testHome 'machine_info';break}catch{Start-Sleep -Milliseconds 200}}
 if(-not $before -or $before.version -ne $PreviousVersion){throw 'old_fixture_not_ready'}
 $settings=Read-Install $testHome
 $identity=(Get-FileHash (Join-Path $testHome 'State\runtime-v1\machine.json')).Hash
 $credential=(Get-FileHash (Join-Path $testHome 'Config\peer-token.txt')).Hash
 & (Join-Path $Package 'Update.ps1') -HomeDir $testHome
 $after=Invoke-LocalTool $testHome 'machine_info'
 $now=Read-Install $testHome
 if($after.version -ne '0.8.3' -or $after.machine_id -cne $before.machine_id){throw 'updated_identity_version_invalid'}
 if($settings.task_name -cne $now.task_name -or $settings.device_name -cne $now.device_name){throw 'startup_or_name_changed'}
 if((Get-FileHash (Join-Path $testHome 'State\runtime-v1\machine.json')).Hash -cne $identity -or (Get-FileHash (Join-Path $testHome 'Config\peer-token.txt')).Hash -cne $credential){throw 'identity_or_credentials_changed'}
 $receipt=[IO.File]::ReadAllText((Join-Path $testHome 'Logs\diagnostics\self-update.log'))
 if($receipt -notmatch 'BACKUP_VERIFIED' -or $receipt -notmatch 'ROLLBACK_REHEARSAL_PASS' -or $receipt -notmatch 'PASS common_batch_update_identity_config_maintenance'){throw 'canonical_update_receipts_missing'}
 Report 'PASS local_admin_bootstrap_to_083_no_cloudflare_identity_credentials_startup_backup_rollback'
 # Parse the embedded native UI helper as PowerShell on the real Windows host.
 $parseErrors=$null;$tokens=$null
 $prompt=Join-Path (Split-Path $Package -Parent) 'pairing_prompt.ps1'
 if(Test-Path $prompt){$null=[Management.Automation.Language.Parser]::ParseFile($prompt,[ref]$tokens,[ref]$parseErrors);if($parseErrors.Count){throw 'native_prompt_parse_failed'}}
 Report 'PASS UPDATE_INSTALLER_COMPLETE'
}finally{
 $receiptPath=Join-Path $testHome 'Logs\diagnostics\self-update.log'
 if(Test-Path $receiptPath){Get-Content $receiptPath -Tail 40|ForEach-Object {Report $_}}
 if(Test-Path (Join-Path $testHome 'Config\installation.json')){
  try{& (Join-Path $testHome 'Scripts\Stop.ps1') -HomeDir $testHome -Force}catch{Report ('cleanup_stop_failed '+$_.Exception.Message);throw}
  $settings=Get-Content (Join-Path $testHome 'Config\installation.json') -Raw|ConvertFrom-Json
  Unregister-ScheduledTask $settings.task_name -Confirm:$false -ErrorAction SilentlyContinue
  Remove-NetFirewallRule -Name $settings.task_name -ErrorAction SilentlyContinue
 }
 for($i=0;$i -lt 100;$i++){if(-not (Get-NetTCPConnection -LocalPort 19865 -State Listen -ErrorAction SilentlyContinue)){break};Start-Sleep -Milliseconds 100}
 if(Test-Path $temp){Remove-Item -LiteralPath $temp -Recurse -Force}
 if(Test-Path $temp){throw 'update_test_cleanup_failed'}
 Report 'PASS update_installer_cleanup'
}
