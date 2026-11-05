param(
 [Parameter(Mandatory=$true)][string]$GoExe,
 [Parameter(Mandatory=$true)][string]$GitExe,
 [Parameter(Mandatory=$true)][string]$SourceCommit,
 [Parameter(Mandatory=$true)][string]$Candidate,
 [Parameter(Mandatory=$true)][string]$Log,
 [string]$SourceDirectory=(Split-Path $PSScriptRoot -Parent)
)
# Same successful profile as VALIDATION-0.7.3-20261103.md (Desktop2),
# with the output/error handling already used in build-runtime-059.ps1.
$ErrorActionPreference='Stop'
$cache=$null
function RunGo([string[]]$GoArguments){
 $ErrorActionPreference='Continue'
 $output=& $GoExe @GoArguments 2>&1
 $code=$LASTEXITCODE
 $ErrorActionPreference='Stop'
 $output|Out-File $Log -Encoding UTF8 -Append
 if($code -ne 0){throw ('go_failed_'+$GoArguments[0])}
 ('PASS go_'+$GoArguments[0])|Add-Content $Log -Encoding UTF8
}
try {
 'START documented-073-build-profile'|Set-Content $Log -Encoding UTF8
 $head=(& $GitExe -C $SourceDirectory rev-parse HEAD).Trim()
 if($LASTEXITCODE -ne 0 -or $head -cne $SourceCommit){throw 'source_commit_mismatch'}
 $dirty=@(& $GitExe -C $SourceDirectory status --porcelain --untracked-files=no)
 if($LASTEXITCODE -ne 0 -or $dirty.Count){throw 'tracked_source_changed'}
 $goVersion=(& $GoExe version).Trim()
 if($LASTEXITCODE -ne 0 -or $goVersion -ne 'go version go1.27.0 windows/amd64'){throw 'documented_go1270_required'}
 $cache=Join-Path (Split-Path $Candidate -Parent) ('go-cache-'+[guid]::NewGuid().ToString('N'))
 New-Item -ItemType Directory $cache|Out-Null
 $env:GOROOT=Split-Path (Split-Path $GoExe -Parent) -Parent
 $env:GOCACHE=$cache;$env:GOMAXPROCS='1';$env:GOGC='25'
 $env:GOWORK='off';$env:GOTOOLCHAIN='local'
 Push-Location $SourceDirectory
 try{
  RunGo @('test','-p','1','-count=1','./...')
  RunGo @('vet','-p','1','./...')
  RunGo @('build','-p','1','-o',$Candidate,'.')
 }finally{Pop-Location}
 $item=Get-Item -LiteralPath $Candidate
 $result=@{source_commit=$head;go_version=$goVersion;profile='Go1.27.0/cache-isolated/GOMAXPROCS=1/GOGC=25/-p1';sha256=(Get-FileHash $Candidate -Algorithm SHA256).Hash.ToLowerInvariant();size_bytes=$item.Length;candidate=$item.FullName;tested_at=[DateTime]::UtcNow.ToString('o')}
 $result|ConvertTo-Json -Compress|Add-Content $Log -Encoding UTF8
 $result|ConvertTo-Json|Set-Content ($Candidate+'.validation.json') -Encoding UTF8
 'PASS documented-073-build-profile'|Add-Content $Log -Encoding UTF8
}catch{('FAIL '+$_.Exception.Message)|Add-Content $Log -Encoding UTF8;exit 1}
finally{
 if($cache -and (Test-Path -LiteralPath $cache)){Remove-Item -LiteralPath $cache -Recurse -Force}
}
