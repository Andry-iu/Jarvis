param([Parameter(Mandatory=$true)][string]$Candidate,[Parameter(Mandatory=$true)][string]$SourceDirectory,[Parameter(Mandatory=$true)][string]$Log)
$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue'
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('source-update-test-'+[guid]::NewGuid().ToString('N'))
$child=$null;$oldToken=$env:JARVIS_MCP_TOKEN
function Report($s){$s|Add-Content $Log -Encoding UTF8}
try{
 foreach($n in @('Agent','Config','Projects','State','Logs\diagnostics')){New-Item -ItemType Directory -Force (Join-Path $fixture $n)|Out-Null}
 $exe=Join-Path $fixture 'Agent\jarvis-mcp.exe';Copy-Item $Candidate $exe
 $token=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
 [IO.File]::WriteAllText((Join-Path $fixture 'Config\agent.env'),('JARVIS_PROJECTS_ROOT='+(Join-Path $fixture 'Projects')+"`r`nJARVIS_STATE_DIR="+(Join-Path $fixture 'State\runtime-v1')+"`r`nJARVIS_CODEX_ENABLED=0"))
 [IO.File]::WriteAllText((Join-Path $fixture 'Config\secrets.env'),('JARVIS_MCP_TOKEN='+$token))
 [IO.File]::WriteAllText((Join-Path $fixture 'Config\installation.json'),'{"port":19965}')
 $psi=New-Object Diagnostics.ProcessStartInfo;$psi.FileName=$exe;$psi.Arguments='-listen 127.0.0.1:19965';$psi.WorkingDirectory=$fixture;$psi.UseShellExecute=$false;$psi.CreateNoWindow=$true
 $psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
 foreach($key in @($psi.EnvironmentVariables.Keys)){if($key -match '^(JARVIS_|OPENAI_|CODEX_|CF_|TUNNEL_)'){$psi.EnvironmentVariables.Remove($key)}}
 $psi.EnvironmentVariables['JARVIS_HOME']=$fixture
 $child=[Diagnostics.Process]::Start($psi);$out=$child.StandardOutput.ReadToEndAsync();$err=$child.StandardError.ReadToEndAsync()
 $env:JARVIS_MCP_TOKEN=$token;$info=$null
 for($i=0;$i -lt 100;$i++){
  try{
   $body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name='machine_info';arguments=@{}}}|ConvertTo-Json -Depth 8
   $r=Invoke-RestMethod 'http://127.0.0.1:19965/mcp' -Method Post -Headers @{Authorization=('Bearer '+$token);Accept='application/json, text/event-stream'} -ContentType 'application/json' -Body $body -TimeoutSec 2
   $info=$r.result.content[0].text|ConvertFrom-Json;break
  }catch{Start-Sleep -Milliseconds 100}
 }
 if(!$info){throw 'source_update_fixture_not_ready'}
 $sha=(Get-FileHash $exe).Hash.ToLowerInvariant();$id=[guid]::NewGuid().ToString('N')
 $runner=Join-Path $SourceDirectory 'scripts\update-from-git.ps1'
 $harness=Join-Path $fixture 'offline-harness.ps1'
 $literal={param($s) "'"+$s.Replace("'","''")+"'"}
 $command='function Invoke-WebRequest { throw "fixture_git_unreachable" }'+"`r`n"+'& '+(& $literal $runner)+' -HomeDir '+(& $literal $fixture)+' -SourceCommit '+('a'*40)+' -TargetVersion '+$info.version+' -RequestId '+$id+' -ExpectedMachine '+$info.machine_id+' -ExpectedPreviousVersion '+$info.version+' -ExpectedPreviousHash '+$sha
 [IO.File]::WriteAllText($harness,$command)
 & "$PSHOME\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $harness
 if($LASTEXITCODE -eq 0){throw 'offline_probe_unexpected_success'}
 $statusPath=Join-Path $fixture ('State\source-updates\'+$id+'\status.json');$status=Get-Content $statusPath -Raw|ConvertFrom-Json
 if($status.phase -ne 'failed' -or $status.error -ne 'git_unreachable' -or !$status.cleanup_verified -or (Get-FileHash $exe).Hash -ine $sha -or $child.HasExited){throw 'offline_failure_changed_agent_or_wrong_status'}
 Report 'PASS git_unreachable_reported_working_agent_preserved_cleanup_verified'
 $before=(Get-FileHash $statusPath).Hash
 & "$PSHOME\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $harness
 if($LASTEXITCODE -eq 0 -or (Get-FileHash $statusPath).Hash -cne $before){throw 'same_request_replayed_or_receipt_changed'}
 Report 'PASS runner_replay_refused_receipt_preserved'
 $errors=$null;$tokens=$null;$null=[Management.Automation.Language.Parser]::ParseFile($runner,[ref]$tokens,[ref]$errors)
 if($errors.Count){throw 'source_runner_parse_failed'}
 Report 'PASS SOURCE_UPDATE_OFFLINE_CONTRACT'
}finally{
 $env:JARVIS_MCP_TOKEN=$oldToken
 if($child){if(!$child.HasExited){Stop-Process -Id $child.Id -Force;$null=$child.WaitForExit(10000)};$null=$out.Wait(3000);$null=$err.Wait(3000);$child.Dispose()}
 if(Test-Path $fixture){Remove-Item -LiteralPath $fixture -Recurse -Force}
 if(Test-Path $fixture){throw 'offline_fixture_cleanup_failed'}
 Report 'PASS source_update_fixture_cleanup'
}
