param([string]$HomeDir=$env:JARVIS_HOME)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($HomeDir).TrimEnd('\')
$temp=Join-Path $root 'Temp'
$log=Join-Path $root 'Logs\diagnostics\owned-test-cleanup.log'
$processes=@(Get-CimInstance Win32_Process)
$tasks=@(Get-ScheduledTask)
$services=@(Get-CimInstance Win32_Service)
$removed=0;$bytes=0L;$removedRules=0;$removedBatches=0;$remaining=@()
function Report([string]$s){$s|Add-Content $log -Encoding UTF8}
function IsReferenced([string]$path){
 foreach($p in $processes){if($p.CommandLine -and $p.CommandLine.IndexOf($path,[StringComparison]::OrdinalIgnoreCase) -ge 0){return $true}}
 foreach($t in $tasks){foreach($a in $t.Actions){if(($a.Execute+' '+$a.Arguments).IndexOf($path,[StringComparison]::OrdinalIgnoreCase) -ge 0){return $true}}}
 foreach($s in $services){if($s.PathName -and $s.PathName.IndexOf($path,[StringComparison]::OrdinalIgnoreCase) -ge 0){return $true}}
 return $false
}
function AssertNoLinks([string]$path){
 $stack=New-Object 'Collections.Generic.Stack[string]';$stack.Push($path)
 while($stack.Count){$item=Get-Item -LiteralPath $stack.Pop() -Force;if($item.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'linked_temporary_refused'};if($item.PSIsContainer){foreach($child in Get-ChildItem -LiteralPath $item.FullName -Force){$stack.Push($child.FullName)}}}
}
New-Item -ItemType Directory -Force (Split-Path $log -Parent)|Out-Null
'START owned_test_cleanup'|Set-Content $log -Encoding UTF8
if(Test-Path $temp){
 foreach($dir in Get-ChildItem -LiteralPath $temp -Directory -Force){
  # Only names produced by Jarvis test/rehearsal harnesses; never arbitrary projects.
  if($dir.Name -notmatch '^(admin-[a-f0-9]{32}|gateway-[a-f0-9]{32}|pair08[123]-build-[a-f0-9]{32}|layout-test-[a-f0-9]{32}|rehearsal-[a-f0-9]{32}|token-meter-074-c662fae)$'){
   # A currently executing, GUID-named dispatch directory removes itself on exit.
   if($dir.Name -match '^[a-f0-9]{32}$' -and (IsReferenced $dir.FullName)){Report ('ACTIVE_BATCH_SELF_CLEANUP '+$dir.FullName);continue}
   $remaining+=$dir.FullName;continue
  }
  if(IsReferenced $dir.FullName){$remaining+=$dir.FullName;Report ('ACTIVE_WORK_PRESERVED '+$dir.FullName);continue}
  AssertNoLinks $dir.FullName
  $size=(Get-ChildItem -LiteralPath $dir.FullName -File -Recurse -Force|Measure-Object Length -Sum).Sum
  Remove-Item -LiteralPath $dir.FullName -Recurse -Force
  if(Test-Path -LiteralPath $dir.FullName){throw 'temporary_removal_not_verified'}
  $removed++;$bytes+=[long]$size;Report ('REMOVED '+$dir.FullName)
 }
}
# Remove only query-user rules for inactive, no-longer-existing Jarvis test binaries.
foreach($rule in Get-NetFirewallRule -PolicyStore PersistentStore|Where-Object {$_.Name -like '*Query User*'}){
 $program=($rule|Get-NetFirewallApplicationFilter).Program
 if(-not $program -or -not $program.StartsWith($temp+'\',[StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetFileName($program) -ine 'jarvis-mcp.exe'){continue}
 if((Test-Path -LiteralPath $program) -or (IsReferenced $program)){continue}
 $name=$rule.Name
 Remove-NetFirewallRule -Name $name
 if(Get-NetFirewallRule -Name $name -ErrorAction SilentlyContinue){throw 'temporary_firewall_rule_remains'}
 $removedRules++
}
# Receipts remain permanent; only verified terminal one-shot batch files are disposable.
$batchDir=Join-Path $root 'Scripts\updates'
if(Test-Path $batchDir){foreach($file in Get-ChildItem -LiteralPath $batchDir -Filter '*.cmd' -File){
 if($file.BaseName -notmatch '^[a-f0-9]{32}$' -or (IsReferenced $file.FullName)){continue}
 $receipt=Join-Path $root ('State\update-receipts\'+$file.BaseName+'.json')
 if(-not (Test-Path $receipt)){continue}
 $r=Get-Content $receipt -Raw|ConvertFrom-Json
 if($r.state -notin @('batch_exited','batch_failed')){continue}
 if((Get-FileHash $file.FullName).Hash -ine $r.sha256){continue}
 Remove-Item -LiteralPath $file.FullName
 if(Test-Path $file.FullName){throw 'terminal_batch_remains'}
 $removedBatches++
}}
Report (@{directories_removed=$removed;bytes_removed=$bytes;firewall_rules_removed=$removedRules;terminal_batches_removed=$removedBatches;unremoved=$remaining}|ConvertTo-Json -Depth 5 -Compress)
if($remaining.Count){Report 'PARTIAL OWNED_TEST_CLEANUP';throw 'unresolved_temporary_directories'}
Report 'PASS OWNED_TEST_CLEANUP_VERIFIED'
