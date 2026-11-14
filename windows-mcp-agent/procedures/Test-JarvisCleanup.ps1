param([Parameter(Mandatory=$true)][string]$ScriptPath,[Parameter(Mandatory=$true)][string]$TemporaryParent)
Set-StrictMode -Version 2
$ErrorActionPreference='Stop'
$fixture=Join-Path $TemporaryParent ('cleanup-fixture-'+[guid]::NewGuid().ToString('N'))
function Put([string]$Path,[string]$Text){[void][IO.Directory]::CreateDirectory((Split-Path $Path -Parent));[IO.File]::WriteAllText($Path,$Text,(New-Object Text.UTF8Encoding($false)))}
function Assert([bool]$Condition,[string]$Message){if(!$Condition){throw $Message}}
$machine='cleanup-fixture'
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
 [pscustomobject]@{passed=$true;checks=@('legacy_marker','preview','recent_orphan','prepared_batch','malformed_marker','invalid_receipt','protected_data','anti_replay');removed=@($r.removed).Count}
} finally {if(Test-Path -LiteralPath $fixture){Remove-Item -LiteralPath $fixture -Recurse -Force}}
