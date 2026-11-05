param(
 [Parameter(Mandatory=$true)][string]$GoExe,
 [Parameter(Mandatory=$true)][string]$GitExe,
 [Parameter(Mandatory=$true)][string]$SourceCommit,
 [Parameter(Mandatory=$true)][string]$Candidate,
 [Parameter(Mandatory=$true)][string]$Log
)
$ErrorActionPreference='Stop'
$source=Split-Path $PSScriptRoot -Parent
try {
 'START token-meter-native-validation'|Set-Content $Log -Encoding UTF8
 $head=(& $GitExe -C $source rev-parse HEAD).Trim()
 if($LASTEXITCODE -ne 0 -or $head -cne $SourceCommit){throw 'source_commit_mismatch'}
 $dirty=@(& $GitExe -C $source status --porcelain --untracked-files=no)
 if($LASTEXITCODE -ne 0 -or $dirty.Count){throw 'tracked_source_changed'}
 $goVersion=(& $GoExe version).Trim()
 if($LASTEXITCODE -ne 0){throw 'go_unavailable'}
 Push-Location $source
 try {
  $env:GOWORK='off'
  & $GoExe test -count=1 ./... *>> $Log
  if($LASTEXITCODE -ne 0){throw 'go_test_failed'}
  'PASS go_test'|Add-Content $Log
  & $GoExe vet ./... *>> $Log
  if($LASTEXITCODE -ne 0){throw 'go_vet_failed'}
  'PASS go_vet'|Add-Content $Log
  & $GoExe build -o $Candidate . *>> $Log
  if($LASTEXITCODE -ne 0){throw 'go_build_failed'}
  'PASS go_build'|Add-Content $Log
 } finally {Pop-Location}
 $item=Get-Item -LiteralPath $Candidate
 $result=@{source_commit=$head;go_version=$goVersion;sha256=(Get-FileHash $Candidate -Algorithm SHA256).Hash.ToLowerInvariant();size_bytes=$item.Length;candidate=$item.FullName;tested_at=[DateTime]::UtcNow.ToString('o')}
 $result|ConvertTo-Json -Compress|Add-Content $Log
 $result|ConvertTo-Json|Set-Content ($Candidate+'.validation.json') -Encoding UTF8
 'PASS token-meter-native-validation'|Add-Content $Log
} catch {('FAIL '+$_.Exception.Message)|Add-Content $Log;exit 1}
