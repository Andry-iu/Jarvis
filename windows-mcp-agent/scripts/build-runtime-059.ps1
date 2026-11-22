param([Parameter(Mandatory=$true)][string]$ExpectedCommit)
$ErrorActionPreference='Stop'
$log='D:\Jarvis\Logs\diagnostics\v59-build.log'
try {
 $root='D:\Jarvis\MCP-Source';$module=Join-Path $root 'windows-mcp-agent'
 'START '+[DateTime]::UtcNow.ToString('o')|Set-Content $log -Encoding UTF8
 if((git -C $root rev-parse HEAD) -ne $ExpectedCommit){throw 'source_commit_mismatch'}
 if((git -C $root branch --show-current) -ne 'reconcile-windows-mcp-agent-20261031'){throw 'wrong_branch'}
 if(git -C $root status --porcelain -uno){throw 'tracked_source_changes'}
 if(-not (Test-Path "$module\update.go.bak")){throw 'legacy_backup_missing'}
 if(-not (git -C $root stash list|Select-String 'backup update.go locale 20261031')){throw 'legacy_stash_missing'}
 Set-Location $module
 $env:GOWORK='off';$env:GOTOOLCHAIN='local'
 $ErrorActionPreference='Continue';$out=& 'C:\Program Files\Go\bin\go.exe' test -count=1 ./... 2>&1;$exit=$LASTEXITCODE;$ErrorActionPreference='Stop'
 $out|Out-File $log -Encoding UTF8 -Append
 'TEST_EXIT '+$exit|Add-Content $log -Encoding UTF8;if($exit -ne 0){throw 'go_tests_failed'}
 $ErrorActionPreference='Continue';$out=& 'C:\Program Files\Go\bin\go.exe' vet ./... 2>&1;$exit=$LASTEXITCODE;$ErrorActionPreference='Stop';$out|Out-File $log -Encoding UTF8 -Append
 'VET_EXIT '+$exit|Add-Content $log -Encoding UTF8;if($exit -ne 0){throw 'go_vet_failed'}
 $candidate=Join-Path $module 'Staging\jarvis-mcp-059.exe'
 $ErrorActionPreference='Continue';$out=& 'C:\Program Files\Go\bin\go.exe' build -o $candidate . 2>&1;$exit=$LASTEXITCODE;$ErrorActionPreference='Stop';$out|Out-File $log -Encoding UTF8 -Append
 'BUILD_EXIT '+$exit|Add-Content $log -Encoding UTF8;if($exit -ne 0){throw 'go_build_failed'}
 'COMMIT '+$ExpectedCommit|Add-Content $log -Encoding UTF8
 'SHA256 '+(Get-FileHash $candidate -Algorithm SHA256).Hash|Add-Content $log -Encoding UTF8
 'BYTES '+(Get-Item $candidate).Length|Add-Content $log -Encoding UTF8
 'TRACKED_CLEAN '+(-not [bool](git -C $root status --porcelain -uno))|Add-Content $log -Encoding UTF8
 'PASS'|Add-Content $log -Encoding UTF8
}catch{'ERROR '+$_.Exception.Message|Add-Content $log -Encoding UTF8;exit 1}
