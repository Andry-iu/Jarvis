param([Parameter(Mandatory=$true)][string]$AgentHome,[Parameter(Mandatory=$true)][string]$StateDirectory)
$ErrorActionPreference='Stop'
$broker=$null
function Read-Rpc($process,[int]$id,[string]$method,$parameters){
 $process.StandardInput.WriteLine((@{id=$id;method=$method;params=$parameters}|ConvertTo-Json -Compress -Depth 10));$process.StandardInput.Flush()
 $deadline=[DateTime]::UtcNow.AddSeconds(15)
 while([DateTime]::UtcNow -lt $deadline){$read=$process.StandardOutput.ReadLineAsync();if(-not $read.Wait(15000)){throw 'read_only_codex_rpc_timeout'};$line=$read.Result;if($null -eq $line){throw 'read_only_codex_rpc_closed'};$message=$line|ConvertFrom-Json;if($message.id -eq $id){if($message.error){throw 'read_only_codex_rpc_error'};return $message.result}}
 throw 'read_only_codex_rpc_timeout'
}
try{
 foreach($path in @($AgentHome,$StateDirectory)){if(-not [IO.Path]::IsPathRooted($path) -or -not $path.StartsWith('D:\Jarvis\',[StringComparison]::OrdinalIgnoreCase) -and $path -ine 'D:\Jarvis'){throw 'unexpected_local_proof_path'}}
 $identity=[IO.File]::ReadAllText((Join-Path $StateDirectory 'machine.json'),[Text.Encoding]::UTF8)|ConvertFrom-Json
 if($identity.machine_id -notmatch '^[a-f0-9]{32}$'){throw 'machine_identity_unavailable'}
 $pkg=Get-AppxPackage -Name OpenAI.Codex|Select-Object -First 1;if(-not $pkg){throw 'codex_package_unavailable'}
 $info=New-Object Diagnostics.ProcessStartInfo;$info.FileName=Join-Path $pkg.InstallLocation 'app\resources\codex.exe';$info.Arguments='app-server --listen stdio://';$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
 foreach($key in @($info.EnvironmentVariables.Keys)){if($key -like 'JARVIS_*' -or $key -in @('OPENAI_API_KEY','CODEX_API_KEY')){$info.EnvironmentVariables.Remove($key)}}
 $broker=New-Object Diagnostics.Process;$broker.StartInfo=$info;if(-not $broker.Start()){throw 'readonly_broker_start_failed'};$stderr=$broker.StandardError.ReadToEndAsync()
 Read-Rpc $broker 1 'initialize' @{clientInfo=@{name='jarvis_readonly_cost_evidence';version='0.5.8'}}|Out-Null
 $broker.StandardInput.WriteLine('{"method":"initialized"}');$broker.StandardInput.Flush()
 $account=Read-Rpc $broker 2 'account/read' @{refreshToken=$false}
 $rates=Read-Rpc $broker 3 'account/rateLimits/read' @{};$quota=$rates.rateLimits;if($rates.rateLimitsByLimitId.codex){$quota=$rates.rateLimitsByLimitId.codex}
 if($account.account.type -ne 'chatgpt' -or $account.account.planType -ne 'self_serve_business_prolite' -or -not $account.account.email){throw 'account_does_not_match_verified_subscription'}
 if($quota.limitId -ne 'codex' -or $null -eq $quota.credits.hasCredits -or $quota.credits.hasCredits -or $null -eq $quota.credits.unlimited -or $quota.credits.unlimited -or ($null -ne $quota.credits.balance -and $quota.credits.balance -ne '0')){throw 'current_credit_flags_do_not_match_zero_credit_evidence'}
 if($null -eq $quota.primary.usedPercent -or $quota.primary.usedPercent -ge 90 -or $quota.spendControlReached -or $null -ne $quota.rateLimitReachedType){throw 'current_quota_guard_failed'}
 # User supplied the authenticated usage screenshot in this conversation:
 # 2026-10-31 16:55 local, 88% remaining, zero credits, auto recharge disabled.
 # This records that evidence; it does not change billing or send a model turn.
 $confirmed=[DateTimeOffset]::Parse('2026-10-31T15:55:00Z');$expires=$confirmed.AddDays(7)
 if([DateTimeOffset]::UtcNow -ge $expires){throw 'user_billing_evidence_expired'}
 $binding=$account.account.email.Trim().ToLowerInvariant()+[char]0+$account.account.planType
 $sha=[Security.Cryptography.SHA256]::Create();try{$fingerprint=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($binding)))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
 $proof=@{version=1;machine_id=$identity.machine_id;account_fingerprint=$fingerprint;source='user-confirmed-usage-screenshot';zero_credits=$true;automatic_recharge_disabled=$true;confirmed_at=$confirmed.ToString('o');valid_until=$expires.ToString('o')}
 $config=Join-Path $AgentHome 'Config';if(-not (Test-Path $config)){throw 'local_config_directory_missing'}
 [IO.File]::WriteAllText((Join-Path $config 'codex-cost-proof.json'),($proof|ConvertTo-Json -Compress),(New-Object Text.UTF8Encoding($false)))
 'Local billing evidence recorded; no inference, purchase, login or payment-setting change.'
}finally{if($broker){try{$broker.StandardInput.Close();if(-not $broker.WaitForExit(2000)){$broker.Kill()}}catch{};$broker.Dispose()}}
