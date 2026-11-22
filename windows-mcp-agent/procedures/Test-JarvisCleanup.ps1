param([Parameter(Mandatory=$true)][string]$ScriptPath,[Parameter(Mandatory=$true)][string]$TemporaryParent)
Set-StrictMode -Version 2
$ErrorActionPreference='Stop'
$fixture=Join-Path $TemporaryParent ('cleanup-fixture-'+[guid]::NewGuid().ToString('N'))
function Put([string]$Path,[string]$Text){[void][IO.Directory]::CreateDirectory((Split-Path $Path -Parent));[IO.File]::WriteAllText($Path,$Text,(New-Object Text.UTF8Encoding($false)))}
function Assert([bool]$Condition,[string]$Message){if(!$Condition){throw $Message}}
$machine='cleanup-fixture'
$held=$null
try {
 [void][IO.Directory]::CreateDirectory($fixture)
 Put (Join-Path $fixture 'Agent\jarvis-mcp.exe') 'fixture-agent'
 $hash=(Get-FileHash -LiteralPath (Join-Path $fixture 'Agent\jarvis-mcp.exe') -Algorithm SHA256).Hash.ToLowerInvariant()
 $keep=Join-Path $fixture 'Backups\new'
 Put (Join-Path $keep 'jarvis-mcp-previous.exe') 'previous-fixture'
 Put (Join-Path $keep 'installed-receipt.json') (@{machine_id=$machine;sha256=$hash;checked_at=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json)
 Put (Join-Path $fixture 'Projects\project\keep.txt') 'project'
 Put (Join-Path $fixture 'Config\keep.txt') 'config'
 Put (Join-Path $fixture 'Scripts\Start.ps1') '# permanent'
 $updates=Join-Path $fixture 'Scripts\updates'
 $old=[guid]::NewGuid().ToString('N')
 $recent=[guid]::NewGuid().ToString('N')
 $bad=[guid]::NewGuid().ToString('N')
 $prepared=[guid]::NewGuid().ToString('N')
 $invalid=[guid]::NewGuid().ToString('N')
 foreach($id in @($old,$recent,$prepared,$invalid)){Put (Join-Path $updates ($id+'.cmd')) ("REM JARVIS-UPDATE-ID: "+$id+"`r`n@echo off`r`n")}
 Put (Join-Path $updates ($bad+'.cmd')) 'unrecognized legacy file'
 Put (Join-Path $fixture ('State\update-receipts\'+$invalid+'.json')) 'not-json'
 $preparedPath=Join-Path $updates ($prepared+'.cmd')
 Put (Join-Path $fixture ('State\update-receipts\'+$prepared+'.json')) (@{state='prepared';sha256=(Get-FileHash -LiteralPath $preparedPath -Algorithm SHA256).Hash.ToLowerInvariant()}|ConvertTo-Json)
 foreach($id in @($old,$bad,$prepared,$invalid)){(Get-Item -LiteralPath (Join-Path $updates ($id+'.cmd'))).LastWriteTimeUtc=[DateTime]::UtcNow.AddHours(-1)}
 $previewId=[guid]::NewGuid().ToString('N')
 $p=& $ScriptPath -HomeDir $fixture -ExpectedMachine $machine -RequestId $previewId -Scope Scripts
 Assert ($p.status -eq 'preview') 'preview failed'
 Assert (@($p.candidates|Where-Object kind -eq 'orphaned_batch').Count -eq 1) 'only old recognized orphan may be selected'
 Assert (Test-Path -LiteralPath (Join-Path $updates ($old+'.cmd'))) 'preview deleted data'
 $executeId=[guid]::NewGuid().ToString('N')
 $r=& $ScriptPath -HomeDir $fixture -ExpectedMachine $machine -RequestId $executeId -Scope Scripts -Execute
 Assert ($r.status -eq 'completed') 'execute failed'
 Assert (!(Test-Path -LiteralPath (Join-Path $updates ($old+'.cmd')))) 'old recognized orphan not removed'
 foreach($id in @($recent,$bad,$prepared,$invalid)){Assert (Test-Path -LiteralPath (Join-Path $updates ($id+'.cmd'))) 'protected batch removed'}
 foreach($path in @('Projects\project\keep.txt','Config\keep.txt','Scripts\Start.ps1','Backups\new\jarvis-mcp-previous.exe')){Assert (Test-Path -LiteralPath (Join-Path $fixture $path)) 'permanent data removed'}
 $again=& $ScriptPath -HomeDir $fixture -ExpectedMachine $machine -RequestId $executeId -Scope Scripts -Execute
 Assert ($again.finished_at -eq $r.finished_at) 'request replayed'
 $releases=Join-Path $fixture 'Releases'
 foreach($rel in @('old','recent','locked','source')){Put (Join-Path $releases ($rel+'\agent.exe')) 'release-fixture'}
 Put (Join-Path $releases 'source\.git') 'gitdir: elsewhere'
 foreach($rel in @('old','locked','source')){foreach($file in @(Get-ChildItem -LiteralPath (Join-Path $releases $rel) -File -Force)){$file.LastWriteTimeUtc=[DateTime]::UtcNow.AddHours(-1)}}
 $held=[IO.File]::Open((Join-Path $releases 'locked\agent.exe'),[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
 Put (Join-Path $fixture 'State\source-updates\active.json') '{"request_id":"fixture"}'
 Put (Join-Path $fixture 'State\source-updates\fixture\status.json') '{"phase":"prepared","cleanup_verified":true}'
 $sourcePrepared=& $ScriptPath -HomeDir $fixture -ExpectedMachine $machine -RequestId ([guid]::NewGuid().ToString('N')) -Scope Releases -Execute
 Assert ($sourcePrepared.status -eq 'failed' -and (Test-Path -LiteralPath (Join-Path $releases 'old\agent.exe'))) 'prepared source update not protected'
 Remove-Item -LiteralPath (Join-Path $fixture 'State\source-updates') -Recurse -Force
 Put (Join-Path $fixture 'State\runtime-v1\jobs\fixture.json') '{"status":"running"}'
 $busy=& $ScriptPath -HomeDir $fixture -ExpectedMachine $machine -RequestId ([guid]::NewGuid().ToString('N')) -Scope Releases -Execute
 Assert ($busy.status -eq 'completed' -and @($busy.removed).Count -eq 0) 'active job did not protect releases'
 Remove-Item -LiteralPath (Join-Path $fixture 'State\runtime-v1\jobs\fixture.json') -Force
 $preview=& $ScriptPath -HomeDir $fixture -ExpectedMachine $machine -RequestId ([guid]::NewGuid().ToString('N')) -Scope Releases
 Assert ($preview.status -eq 'preview' -and @($preview.candidates).Count -eq 1) 'release preview selected protected material'
 Assert (Test-Path -LiteralPath (Join-Path $releases 'old\agent.exe')) 'release preview deleted data'
 $prune=& $ScriptPath -HomeDir $fixture -ExpectedMachine $machine -RequestId ([guid]::NewGuid().ToString('N')) -Scope Releases -Execute
 Assert ($prune.status -eq 'completed' -and @($prune.removed).Count -eq 1) 'old release not removed'
 Assert (!(Test-Path -LiteralPath (Join-Path $releases 'old'))) 'old release remains'
 foreach($rel in @('recent','locked','source')){Assert (Test-Path -LiteralPath (Join-Path $releases $rel)) 'protected release removed'}
 $held.Dispose();$held=$null
 (Get-Item -LiteralPath (Join-Path $releases 'recent\agent.exe')).LastWriteTimeUtc=[DateTime]::UtcNow.AddHours(2)
 $override=& $ScriptPath -HomeDir $fixture -ExpectedMachine $machine -RequestId ([guid]::NewGuid().ToString('N')) -Scope Releases -KeepRecentMinutes 0 -Execute
 Assert ($override.status -eq 'completed') 'explicit recency override failed'
 Assert (!(Test-Path -LiteralPath (Join-Path $releases 'recent'))) 'future archive timestamp ignored explicit zero retention'
 Assert (Test-Path -LiteralPath (Join-Path $releases 'source\.git')) 'override removed source tree'
 Remove-Item -LiteralPath $releases -Recurse -Force
 [void][IO.Directory]::CreateDirectory($releases)
 $empty=& $ScriptPath -HomeDir $fixture -ExpectedMachine $machine -RequestId ([guid]::NewGuid().ToString('N')) -Scope Releases -Execute
 Assert ($empty.status -eq 'completed' -and !(Test-Path -LiteralPath $releases)) 'empty releases root remains'
 Assert (Test-Path -LiteralPath (Join-Path $fixture 'Agent\jarvis-mcp.exe')) 'installed agent removed'
 Assert (Test-Path -LiteralPath (Join-Path $fixture 'Backups\new\jarvis-mcp-previous.exe')) 'rollback backup removed'
 [pscustomobject]@{passed=$true;checks=@('legacy_marker','preview','recent_orphan','prepared_batch','malformed_marker','invalid_receipt','protected_data','anti_replay','release_prepared_source_update','release_busy_job','release_preview','release_removal','release_recent','release_locked','release_git_worktree','release_future_timestamp_override','release_empty_root','agent_backup_retained');removed=@($r.removed).Count+@($prune.removed).Count+@($override.removed).Count}
} finally {if($held){$held.Dispose()};if(Test-Path -LiteralPath $fixture){Remove-Item -LiteralPath $fixture -Recurse -Force}}
