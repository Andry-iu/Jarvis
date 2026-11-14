param(
 [Parameter(Mandatory=$true)][string]$HomeDir,
 [Parameter(Mandatory=$true)][string]$ExpectedMachine,
 [Parameter(Mandatory=$true)][ValidatePattern('^[a-f0-9]{32}$')][string]$RequestId,
 [ValidateSet('Backups','Logs','Temp','Scripts')][string[]]$Scope=@('Backups','Logs','Temp','Scripts'),
 [ValidateRange(0,1440)][int]$KeepRecentMinutes=5,
 [switch]$Execute
)
# Standalone procedure. No project, credential, runtime or source-tree mutation.
Set-StrictMode -Version 2
$ErrorActionPreference='Stop'
if(-not [IO.Path]::IsPathRooted($HomeDir)){throw 'absolute_home_required'}
$root=[IO.Path]::GetFullPath($HomeDir).TrimEnd('\')
if($root -eq [IO.Path]::GetPathRoot($root).TrimEnd('\')){throw 'drive_root_refused'}
$agent=Join-Path $root 'Agent\jarvis-mcp.exe'
if(!(Test-Path -LiteralPath $agent -PathType Leaf)){throw 'installed_agent_required'}
function Hash([string]$p){(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()}
function Children([string]$p){if(Test-Path -LiteralPath $p){@(Get-ChildItem -LiteralPath $p -Force)}}
function NoLinks([string]$p){
 $q=New-Object 'Collections.Generic.Stack[string]';$q.Push($p)
 while($q.Count){$x=Get-Item -LiteralPath $q.Pop() -Force
  if($x.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'linked_path_preserved'}
  if($x.PSIsContainer){foreach($c in @(Children $x.FullName)){$q.Push($c.FullName)}}
 }
}
# Check ancestors without traversing the unrelated Projects or source trees.
$ancestor=$root
while($ancestor){$a=Get-Item -LiteralPath $ancestor -Force;if($a.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'linked_home_refused'};$next=Split-Path $ancestor -Parent;if($next -eq $ancestor){break};$ancestor=$next}
$receipts=Join-Path $root 'State\cleanup-receipts'
[void][IO.Directory]::CreateDirectory($receipts);NoLinks $receipts
$receiptPath=Join-Path $receipts ($RequestId+'.json')
if(Test-Path -LiteralPath $receiptPath){$previous=Get-Content -LiteralPath $receiptPath -Raw|ConvertFrom-Json;if($previous.machine_id -ne $ExpectedMachine -or $previous.execute -ne [bool]$Execute -or (@($previous.scope)-join ',') -ne ($Scope-join ',') -or $previous.keep_recent_minutes -ne $KeepRecentMinutes){throw 'request_id_arguments_changed'};return $previous}
$lockPath=Join-Path $receipts 'cleanup.lock'
$lock=[IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$updateLock=$null
$report=[ordered]@{schema_version=1;request_id=$RequestId;machine_id=$ExpectedMachine;home=$root;execute=[bool]$Execute;scope=$Scope;keep_recent_minutes=$KeepRecentMinutes;status='running';started_at=[DateTime]::UtcNow.ToString('o');finished_at=$null;kept_backup=$null;candidates=@();removed=@();preserved=@();errors=@();bytes_removed=0L}
function SaveReport {
 $pending=$receiptPath+'.new'
 [IO.File]::WriteAllText($pending,($report|ConvertTo-Json -Depth 12),(New-Object Text.UTF8Encoding($false)))
 if(Test-Path -LiteralPath $receiptPath){$previous=$receiptPath+'.previous';[IO.File]::Replace($pending,$receiptPath,$previous);Remove-Item -LiteralPath $previous -Force}else{[IO.File]::Move($pending,$receiptPath)}
}
function Preserve([string]$path,[string]$reason){$report.preserved+=@{path=$path;reason=$reason}}
function Referenced([string]$path){
 foreach($p in $script:processes){if(($p.ExecutablePath -and $p.ExecutablePath.StartsWith($path,[StringComparison]::OrdinalIgnoreCase)) -or ($p.CommandLine -and $p.CommandLine.IndexOf($path,[StringComparison]::OrdinalIgnoreCase) -ge 0)){return $true}}
 foreach($t in $script:tasks){foreach($a in $t.Actions){$exe=if($a.PSObject.Properties['Execute']){[string]$a.Execute}else{''};$args=if($a.PSObject.Properties['Arguments']){[string]$a.Arguments}else{''};if(($exe+' '+$args).IndexOf($path,[StringComparison]::OrdinalIgnoreCase) -ge 0){return $true}}}
 foreach($s in $script:services){if($s.PathName -and $s.PathName.IndexOf($path,[StringComparison]::OrdinalIgnoreCase) -ge 0){return $true}}
 return $false
}
function Candidate([string]$path,[string]$kind){
 try {
  $full=[IO.Path]::GetFullPath($path)
  $relative=$full.Substring($root.Length).TrimStart('\')
  if(!$full.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase) -or $relative -notmatch '^(Backups|Logs|Temp|Scratch|Staging|Scripts\\(updates|Temporary))\\'){throw 'outside_cleanup_scope'}
  NoLinks $full
  if(Referenced $full){Preserve $full 'process_task_or_service_reference';return}
  $item=Get-Item -LiteralPath $full -Force
  $files=if($item.PSIsContainer){@(Get-ChildItem -LiteralPath $full -File -Recurse -Force)}else{@($item)}
  if($kind -eq 'temporary' -and @(Get-ChildItem -LiteralPath $full -Directory -Recurse -Force -ErrorAction SilentlyContinue|Where-Object Name -eq '.git').Count){Preserve $full 'source_tree_requires_review';return}
  $bytes=0L
  foreach($f in $files){
   if($kind -ne 'backup' -and $f.LastWriteTimeUtc -gt $script:cutoff){Preserve $full 'recent_work';return}
   $handle=[IO.File]::Open($f.FullName,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None);$handle.Dispose()
   $bytes+=$f.Length
  }
  $report.candidates+=@{path=$full;kind=$kind;bytes=$bytes}
 }catch{Preserve $path $_.Exception.Message;if($_.Exception.Message -ne 'linked_path_preserved' -and $_.Exception -isnot [IO.IOException] -and $_.Exception.InnerException -isnot [IO.IOException]){$report.errors+=@{path=$path;reason=$_.Exception.Message}}}
}
try {
 SaveReport
 $updateLock=[IO.File]::Open((Join-Path $root 'Config\update.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
 $initialHash=Hash $agent
 $script:processes=@(Get-CimInstance Win32_Process)
 $script:tasks=@(Get-ScheduledTask)
 $script:services=@(Get-CimInstance Win32_Service)
 $script:cutoff=[DateTime]::UtcNow.AddMinutes(-$KeepRecentMinutes)
 # Refuse a concurrent or uncertain source update; do not infer completion from time.
 $active=Join-Path $root 'State\source-updates\active.json'
 if(Test-Path -LiteralPath $active){
  $a=Get-Content -LiteralPath $active -Raw|ConvertFrom-Json
  $s=Get-Content -LiteralPath (Join-Path $root ('State\source-updates\'+$a.request_id+'\status.json')) -Raw|ConvertFrom-Json
  if($s.phase -notin @('completed','failed','prepared') -or !$s.cleanup_verified){throw 'source_update_active_or_uncertain'}
 }
 # Runtime jobs are metadata only; an active job keeps its log/temp area intact.
 $busy=$false
 foreach($file in @(Children (Join-Path $root 'State\runtime-v1\jobs'))){if(!$file.PSIsContainer -and $file.Extension -eq '.json'){try{$j=Get-Content -LiteralPath $file.FullName -Raw|ConvertFrom-Json;foreach($key in @('status','state')){if($j.PSObject.Properties[$key] -and $j.$key -in @('queued','running','starting','cancelling')){$busy=$true}}}catch{$busy=$true}}}
 $backups=Join-Path $root 'Backups';$all=@(Children $backups)
 $valid=@()
 foreach($d in $all){
  if(!$d.PSIsContainer){continue}
  $r=Join-Path $d.FullName 'installed-receipt.json'
  if(Test-Path -LiteralPath $r){try{
   NoLinks $d.FullName
   $v=Get-Content -LiteralPath $r -Raw|ConvertFrom-Json
   if($v.machine_id -eq $ExpectedMachine -and $v.sha256 -eq $initialHash -and (Test-Path -LiteralPath (Join-Path $d.FullName 'jarvis-mcp-previous.exe'))){$valid+=@{dir=$d;receipt=$v;at=[DateTime]$v.checked_at}}
  }catch{Preserve $d.FullName 'unverified_backup'}}
 }
 if(!$valid.Count){throw 'no_verified_backup_for_installed_agent'}
 $keeper=@($valid|Sort-Object at -Descending)[0]
 $report.kept_backup=$keeper.dir.FullName
 $keepHash=Hash (Join-Path $keeper.dir.FullName 'jarvis-mcp-previous.exe')
 Preserve $keeper.dir.FullName 'latest_verified_backup'
 if($Scope -contains 'Backups'){
  if(@($all|Where-Object{$_.CreationTimeUtc -gt $keeper.dir.CreationTimeUtc}).Count){throw 'newer_unverified_backup_requires_review'}
  foreach($d in $all){if($d.FullName -ne $keeper.dir.FullName){Candidate $d.FullName 'backup'}}
 }
 if($Scope -contains 'Temp'){
  foreach($name in @('Temp','Scratch','Staging')){foreach($d in @(Children (Join-Path $root $name))){if($busy){Preserve $d.FullName 'runtime_work_active'}else{Candidate $d.FullName 'temporary'}}}
 }
 if($Scope -contains 'Logs'){
  $logs=Join-Path $root 'Logs'
  if(Test-Path -LiteralPath $logs){NoLinks $logs;foreach($f in @(Get-ChildItem -LiteralPath $logs -File -Recurse -Force)){if($busy){Preserve $f.FullName 'runtime_work_active'}else{Candidate $f.FullName 'log'}}}
 }
 if($Scope -contains 'Scripts'){
  foreach($f in @(Children (Join-Path $root 'Scripts\updates'))){
   if($f.PSIsContainer -or $f.Name -notmatch '^[a-fA-F0-9]{32}\.(cmd|bat)$'){Preserve $f.FullName 'unrecognized_script';continue}
   $r=Join-Path $root ('State\update-receipts\'+$f.BaseName+'.json')
   if(!(Test-Path -LiteralPath $r)){
    if($busy){Preserve $f.FullName 'runtime_work_active';continue}
    # Legacy batches predating durable receipts remain identifiable by the exact
    # immutable update marker. Missing receipts never authorize a replay.
    $reader=[IO.File]::OpenText($f.FullName)
    try{$marker=$reader.ReadLine()}finally{$reader.Dispose()}
    if($marker -cne ('REM JARVIS-UPDATE-ID: '+$f.BaseName)){Preserve $f.FullName 'unrecognized_orphan_batch';continue}
    Candidate $f.FullName 'orphaned_batch'
    continue
   }
   try{$v=Get-Content -LiteralPath $r -Raw|ConvertFrom-Json;if($v.state -notin @('batch_exited','batch_failed','launch_failed') -or (Hash $f.FullName) -ne $v.sha256){Preserve $f.FullName 'nonterminal_or_changed_batch';continue};Candidate $f.FullName 'terminal_batch'}catch{Preserve $f.FullName 'missing_or_invalid_batch_receipt'}
  }
  foreach($f in @(Children (Join-Path $root 'Scripts\Temporary'))){if($busy){Preserve $f.FullName 'runtime_work_active'}else{Candidate $f.FullName 'temporary_script'}}
 }
 SaveReport
 if($Execute){
  foreach($entry in $report.candidates){
   try {
    # Refresh references just before deletion, never stop or kill a process.
    $script:processes=@(Get-CimInstance Win32_Process)
    NoLinks $entry.path
    if(Referenced $entry.path){Preserve $entry.path 'became_active';continue}
    Remove-Item -LiteralPath $entry.path -Recurse -Force
    if(Test-Path -LiteralPath $entry.path){throw 'removal_not_verified'}
    $report.removed+=$entry;$report.bytes_removed+=$entry.bytes;SaveReport
   }catch{$report.errors+=@{path=$entry.path;reason=$_.Exception.Message}}
  }
 }
 if((Hash $agent) -ne $initialHash -or (Hash (Join-Path $keeper.dir.FullName 'jarvis-mcp-previous.exe')) -ne $keepHash){throw 'agent_or_retained_backup_changed'}
 $report.status=if($report.errors.Count){'partial'}elseif($Execute){'completed'}else{'preview'}
}catch{$report.errors+=@{path=$root;reason=$_.Exception.Message};$report.status='failed'}
finally{
 $report.finished_at=[DateTime]::UtcNow.ToString('o')
 try{SaveReport}finally{if($updateLock){$updateLock.Dispose()};$lock.Dispose()}
}
[pscustomobject]$report
