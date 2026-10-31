param([Parameter(Mandatory=$true)][string]$ExpectedHash,[Parameter(Mandatory=$true)][string]$SourceCommit,[switch]$RehearsalOnly)
$ErrorActionPreference='Stop'
$jarvisRoot='D:\Jarvis';$module='D:\Jarvis\MCP-Source\windows-mcp-agent';$repo='D:\Jarvis\MCP-Source'
$installed=Join-Path $jarvisRoot 'jarvis-mcp.exe';$candidate=Join-Path $module 'Staging\jarvis-mcp-059.exe'
$backup=Join-Path $jarvisRoot 'Backups\mcp-0.5.9-20261031';$log=Join-Path $jarvisRoot 'Logs\diagnostics\v59-deploy.log'
$previousHash='04F369F365816BC076CBCE3F89589BAC63FE628385FFF8B96316E2E79115C1BE'
$testProcess=$null;$newProcess=$null;$promotionStarted=$false;$completed=$false
function Report([string]$s){$s|Add-Content $log -Encoding UTF8}
function Hash([string]$p){return (Get-FileHash $p -Algorithm SHA256).Hash}
function Copy-Checked([string]$from,[string]$to,[string]$expected){Copy-Item -LiteralPath $from -Destination $to -Force;if((Hash $to) -ne $expected){throw 'copy_checksum_mismatch'}}
function Wait-Health([int]$port,[string]$version){for($i=0;$i -lt 80;$i++){try{$h=Invoke-RestMethod ('http://127.0.0.1:'+$port+'/health') -TimeoutSec 1;if($h.version -eq $version){return}}catch{};Start-Sleep -Milliseconds 200};throw ('health_timeout_'+$version)}
function Stop-Owned($p){if($p -and -not $p.HasExited){Stop-Process -Id $p.Id -Force;$p.WaitForExit();Start-Sleep -Milliseconds 200}}
function Replace-Checked([string]$from,[string]$to,[string]$expected){for($i=0;$i -lt 40;$i++){try{Copy-Checked $from $to $expected;return}catch{if($i -eq 39){throw};Start-Sleep -Milliseconds 200}}}
function Agent-Version([int]$port){$body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name='agent_status';arguments=@{}}}|ConvertTo-Json -Depth 5 -Compress;$r=Invoke-RestMethod ('http://127.0.0.1:'+$port+'/mcp') -Method Post -ContentType 'application/json' -Headers @{Authorization=('Bearer '+$env:JARVIS_MCP_TOKEN);Accept='application/json, text/event-stream'} -Body $body -TimeoutSec 10;if($r.result.isError -or $r.error){throw 'mcp_reconnect_failed'};return ($r.result.content[0].text|ConvertFrom-Json).version}
function Set-AgentConfig([string]$name,[string]$value){$path=Join-Path $jarvisRoot 'Config\agent.env';$text=[IO.File]::ReadAllText($path,[Text.Encoding]::UTF8);$pattern='(?m)^'+[regex]::Escape($name)+'=.*$';$line=$name+'='+$value;if([regex]::IsMatch($text,$pattern)){$text=[regex]::Replace($text,$pattern,[Text.RegularExpressions.MatchEvaluator]{param($m)$line})}else{$text=$text.TrimEnd()+"`r`n"+$line+"`r`n"};[IO.File]::WriteAllText($path,$text,(New-Object Text.UTF8Encoding($false)))}
try{
 'START '+[DateTime]::UtcNow.ToString('o')|Set-Content $log -Encoding UTF8
 $principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent());if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'deployment_requires_existing_elevated_session'}
 if((git -C $repo rev-parse HEAD) -ne $SourceCommit -or (git -C $repo status --porcelain -uno)){throw 'source_not_clean_at_expected_commit'}
 if((git -C $repo rev-parse ('origin/reconcile-windows-mcp-agent-20261031')) -ne $SourceCommit){throw 'local_and_remote_ref_differ'}
 if((Hash $candidate) -ne $ExpectedHash){throw 'candidate_hash_mismatch'}
 if((Hash $installed) -ne $previousHash){throw 'working_binary_changed'}
 $health=Invoke-RestMethod 'http://127.0.0.1:8765/health';if($health.version -ne '0.5.8'){throw 'working_version_changed'}
 $listener=@(Get-NetTCPConnection -LocalPort 8765 -State Listen);if($listener.Count -ne 1){throw 'ambiguous_main_listener'};$oldPID=$listener[0].OwningProcess
 $oldCim=Get-CimInstance Win32_Process -Filter ('ProcessId='+$oldPID);if($oldCim.ExecutablePath -ine $installed){throw 'main_listener_path_mismatch'}
 $pkg=Get-AppxPackage -Name OpenAI.Codex|Select-Object -First 1;if(-not $pkg){throw 'codex_package_missing'};$codexExe=Join-Path $pkg.InstallLocation 'app\resources\codex.exe'
 New-Item -ItemType Directory -Force $backup|Out-Null
 $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;& "$env:SystemRoot\System32\icacls.exe" $backup /inheritance:r /grant:r ('*'+$sid+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F'|Out-Null;if($LASTEXITCODE -ne 0){throw 'backup_acl_failed'}
 $oldBackup=Join-Path $backup 'jarvis-mcp-0.5.8.exe';if(Test-Path $oldBackup){if((Hash $oldBackup) -ne $previousHash){throw 'existing_backup_mismatch'}}else{Copy-Checked $installed $oldBackup $previousHash}
 $configHashes=@{};foreach($name in 'agent.env','secrets.env'){$src=Join-Path $jarvisRoot ('Config\'+$name);$dest=Join-Path $backup $name;$h=Hash $src;$configHashes[$name]=$h;if(Test-Path $dest){if((Hash $dest) -ne $h){throw 'existing_config_backup_mismatch'}}else{Copy-Checked $src $dest $h}}
 $startup=Join-Path $jarvisRoot 'jarvis-mcp-as-admin.bat';if(Test-Path $startup){$h=Hash $startup;$dest=Join-Path $backup 'jarvis-mcp-as-admin.bat';if(Test-Path $dest){if((Hash $dest) -ne $h){throw 'startup_backup_mismatch'}}else{Copy-Checked $startup $dest $h}}
 $proof=Join-Path $jarvisRoot 'Config\codex-cost-proof.json';if(Test-Path $proof){Copy-Checked $proof (Join-Path $backup 'codex-cost-proof.json') (Hash $proof)}
 $state=Join-Path $jarvisRoot 'State\runtime-v1';$saved=Join-Path $backup 'state-snapshot'
 foreach($file in Get-ChildItem $state -Filter '*.json' -File -Recurse){$rel=$file.FullName.Substring($state.Length).TrimStart('\');$dest=Join-Path $saved $rel;New-Item -ItemType Directory -Force (Split-Path $dest -Parent)|Out-Null;Copy-Checked $file.FullName $dest (Hash $file.FullName)}
 Report ('BACKUP_VERIFIED '+$backup)
 if(Get-NetTCPConnection -LocalPort 8767 -State Listen -ErrorAction SilentlyContinue){throw 'rehearsal_port_busy'}
 $env:JARVIS_HOME=$jarvisRoot;$env:JARVIS_STATE_DIR=Join-Path $backup 'rehearsal-state';$env:JARVIS_CODEX_ENABLED='1';$env:JARVIS_CODEX_EXE=$codexExe;$env:JARVIS_DESKTOP_ENABLED='1'
 if(-not $env:JARVIS_MCP_TOKEN){foreach($line in [IO.File]::ReadAllLines((Join-Path $jarvisRoot 'Config\secrets.env'))){if($line -match '^JARVIS_MCP_TOKEN=(.*)$'){$env:JARVIS_MCP_TOKEN=$matches[1]}}};if($env:JARVIS_MCP_TOKEN.Length -lt 24){throw 'local_authentication_unavailable'}
 $rehearsal=Join-Path $backup 'rehearsal.exe';Replace-Checked $candidate $rehearsal $ExpectedHash
 $testProcess=Start-Process $rehearsal -ArgumentList @('-listen','127.0.0.1:8767') -WindowStyle Hidden -PassThru;Wait-Health 8767 '0.5.9';if((Agent-Version 8767) -ne '0.5.9'){throw 'candidate_rehearsal_mcp_failed'};Stop-Owned $testProcess;$testProcess=$null
 Replace-Checked $oldBackup $rehearsal $previousHash
 $testProcess=Start-Process $rehearsal -ArgumentList @('-listen','127.0.0.1:8767') -WindowStyle Hidden -PassThru;Wait-Health 8767 '0.5.8';if((Agent-Version 8767) -ne '0.5.8'){throw 'rollback_rehearsal_mcp_failed'};Stop-Owned $testProcess;$testProcess=$null;Report 'ROLLBACK_REHEARSAL_PASS'
 if($RehearsalOnly){Report 'PASS rehearsal_only';$completed=$true;return}
 $newPath=Join-Path $jarvisRoot 'jarvis-mcp-059.new.exe';Copy-Checked $candidate $newPath $ExpectedHash
 $promotionStarted=$true
 Set-AgentConfig 'JARVIS_CODEX_ENABLED' '1';Set-AgentConfig 'JARVIS_CODEX_EXE' $codexExe;Set-AgentConfig 'JARVIS_STATE_DIR' (Join-Path $jarvisRoot 'State\runtime-v1');Set-AgentConfig 'JARVIS_DESKTOP_ENABLED' '1'
 $env:JARVIS_STATE_DIR=Join-Path $jarvisRoot 'State\runtime-v1'
 $old=Get-Process -Id $oldPID;Stop-Owned $old
 Replace-Checked $newPath $installed $ExpectedHash
 $newProcess=Start-Process $installed -WindowStyle Hidden -PassThru;Wait-Health 8765 '0.5.9'
 if((Agent-Version 8765) -ne '0.5.9'){throw 'installed_mcp_version_mismatch'}
 if((Hash $installed) -ne $ExpectedHash){throw 'installed_hash_mismatch'}
 Report ('INSTALLED_PID '+$newProcess.Id);Report ('INSTALLED_HASH '+$ExpectedHash);Report ('SOURCE_COMMIT '+$SourceCommit);Report 'PASS installed_health_and_mcp';$completed=$true
}catch{
 Report ('ERROR '+$_.Exception.Message)
 if($promotionStarted){try{Stop-Owned $newProcess;foreach($name in 'agent.env','secrets.env'){Copy-Item (Join-Path $backup $name) (Join-Path $jarvisRoot ('Config\'+$name)) -Force};if((Hash $installed) -ne $previousHash){Replace-Checked (Join-Path $backup 'jarvis-mcp-0.5.8.exe') $installed $previousHash};$env:JARVIS_STATE_DIR='';$env:JARVIS_CODEX_ENABLED='';if(-not (Get-NetTCPConnection -LocalPort 8765 -State Listen -ErrorAction SilentlyContinue)){$rollbackProcess=Start-Process $installed -WindowStyle Hidden -PassThru;Report ('ROLLBACK_PID '+$rollbackProcess.Id)};Wait-Health 8765 '0.5.8';Report 'ROLLBACK_RESTORED'}catch{Report ('ROLLBACK_ERROR '+$_.Exception.Message)}}
 exit 1
}finally{Stop-Owned $testProcess;if(-not $completed){Report 'NOT_COMPLETED'}}
