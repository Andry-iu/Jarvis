param(
 [Parameter(Mandatory=$true)][string]$Candidate,
 [Parameter(Mandatory=$true)][string]$TestHome,
 [Parameter(Mandatory=$true)][string]$Log
)
$ErrorActionPreference='Stop'
$process=$null;$stdout=$null;$stderr=$null;$copyOut=$null;$copyErr=$null
$created=$false
function Call([string]$name,[hashtable]$arguments){
 $body=@{jsonrpc='2.0';id=1;method='tools/call';params=@{name=$name;arguments=$arguments}}|ConvertTo-Json -Depth 12 -Compress
 $reply=Invoke-RestMethod 'http://127.0.0.1:8767/mcp' -Method Post -ContentType 'application/json' -Headers @{Authorization=('Bearer '+$token);Accept='application/json, text/event-stream'} -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 10
 if($reply.error -or $reply.result.isError){throw ('mcp_check_failed_'+$name)}
 return $reply.result.content[0].text|ConvertFrom-Json
}
try{
 'START token-meter-isolated-mcp'|Set-Content $Log -Encoding UTF8
 if(Test-Path $TestHome){throw 'test_home_exists'}
 if(Get-NetTCPConnection -LocalPort 8767 -State Listen -ErrorAction SilentlyContinue){throw 'test_port_busy'}
 New-Item -ItemType Directory -Path ($TestHome+'\Config'),($TestHome+'\Projects\sample')|Out-Null
 $created=$true
 $token=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
 $encoding=New-Object Text.UTF8Encoding($false)
 [IO.File]::WriteAllText(($TestHome+'\Config\agent.env'),('JARVIS_STATE_DIR='+$TestHome+'\State\runtime-v1'+[Environment]::NewLine+'JARVIS_PROJECTS_ROOT='+$TestHome+'\Projects'+[Environment]::NewLine+'JARVIS_CODEX_ENABLED=0'),$encoding)
 [IO.File]::WriteAllText(($TestHome+'\Config\secrets.env'),('JARVIS_MCP_TOKEN='+$token),$encoding)
 $dialogue=$TestHome+'\Projects\sample\dialogue.txt'
 [IO.File]::WriteAllText($dialogue,'Conversazione da conservare nel collaudo isolato.',$encoding)
 $hash=(Get-FileHash $dialogue -Algorithm SHA256).Hash
 $psi=New-Object Diagnostics.ProcessStartInfo
 $psi.FileName=$Candidate;$psi.Arguments='-listen 127.0.0.1:8767';$psi.WorkingDirectory=$TestHome
 $psi.UseShellExecute=$false;$psi.CreateNoWindow=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
 foreach($key in @($psi.EnvironmentVariables.Keys)){if($key -match '^(JARVIS_|OPENAI_|CODEX_|CF_|TUNNEL_)'){$psi.EnvironmentVariables.Remove($key)}}
 $psi.EnvironmentVariables['JARVIS_HOME']=$TestHome
 $process=[Diagnostics.Process]::Start($psi)
 $stdout=[IO.File]::Create($TestHome+'\stdout.log');$stderr=[IO.File]::Create($TestHome+'\stderr.log')
 $copyOut=$process.StandardOutput.BaseStream.CopyToAsync($stdout);$copyErr=$process.StandardError.BaseStream.CopyToAsync($stderr)
 $ready=$false
 for($i=0;$i -lt 80;$i++){
  if($process.HasExited){throw 'candidate_exited'}
  try{if((Invoke-RestMethod 'http://127.0.0.1:8767/health' -TimeoutSec 1).version -eq '0.7.4'){$ready=$true;break}}catch{}
  Start-Sleep -Milliseconds 200
 }
 if(!$ready){throw 'candidate_health_timeout'}
 $info=Call 'machine_info' @{}
 if($info.version -ne '0.7.4' -or $info.capabilities -notcontains 'codex_token_usage'){throw 'candidate_capability_missing'}
 $a=@{machine_id=$info.machine_id;project='sample'}
 $report=Call 'codex_token_usage' $a
 if($report.category -ne 'development' -or $report.total_matching -ne 0 -or $null -ne $report.summary.observed_tokens -or $report.historical_backfill -ne $false){throw 'empty_default_report_incorrect'}
 $request=@{operation='codex_token_usage';machine_id=$info.machine_id;project='sample';usage_category='dialogue'}
 $dialogueReport=Call 'task_start' @{operation='runtime';request=$request}
 if($dialogueReport.category -ne 'dialogue' -or $dialogueReport.total_matching -ne 0){throw 'compatibility_report_failed'}
 if((Get-FileHash $dialogue -Algorithm SHA256).Hash -ine $hash){throw 'dialogue_changed'}
 'PASS health_version_capability'|Add-Content $Log
 'PASS direct_report_no_inference_unknown_is_null'|Add-Content $Log
 'PASS compatibility_dialogue_filter'|Add-Content $Log
 'PASS conversation_preserved'|Add-Content $Log
 'PASS token-meter-isolated-mcp'|Add-Content $Log
}catch{('FAIL '+$_.Exception.Message)|Add-Content $Log;exit 1}
finally{
 if($process -and !$process.HasExited){Stop-Process -Id $process.Id -Force;$null=$process.WaitForExit(10000)}
 if($copyOut){$null=$copyOut.Wait(3000)};if($copyErr){$null=$copyErr.Wait(3000)}
 if($stdout){$stdout.Dispose()};if($stderr){$stderr.Dispose()}
 if($created -and (!$process -or $process.HasExited)){Remove-Item -LiteralPath $TestHome -Recurse -Force}
}
