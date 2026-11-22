param([Parameter(Mandatory=$true)][string]$ExpectedParent,[Parameter(Mandatory=$true)][string]$Message,[switch]$FinalDocsOnly,[switch]$LocalOnly)
$ErrorActionPreference='Stop'
$repo='D:\Jarvis\MCP-Source'
$log='D:\Jarvis\Logs\diagnostics\v70-git-publish.log'
$branch='reconcile-windows-mcp-agent-20261031'
function InvokeGit([string[]]$a){
 $previous=$ErrorActionPreference;$ErrorActionPreference='Continue'
 $output=& git -C $repo -c core.hooksPath=NUL @a 2>&1
 $code=$LASTEXITCODE;$ErrorActionPreference=$previous
 if($code -ne 0){throw ('git_command_failed_'+$a[0]+'_'+$code)}
 return ($output|Out-String).Trim()
}
try{
 'START '+[DateTime]::UtcNow.ToString('o')|Set-Content $log
 foreach($origin in @((InvokeGit -a @('remote','get-url','origin')),(InvokeGit -a @('remote','get-url','--push','origin')))){
  $uri=[Uri]$origin
  if($uri.Scheme -cne 'https' -or $uri.Host -ine 'github.com' -or $uri.UserInfo -or $uri.AbsolutePath.TrimEnd('/') -inotmatch '^/Andry-iu/Jarvis(\.git)?$' -or $uri.Query -or $uri.Fragment){throw 'origin_not_exact_authorized_repository'}
 }
 'VERIFIED_DESTINATION https://github.com/Andry-iu/Jarvis.git'|Add-Content $log
 if(!$LocalOnly){$null=InvokeGit -a @('fetch','--no-recurse-submodules','origin')}
 if((InvokeGit -a @('branch','--show-current')) -cne $branch -or (InvokeGit -a @('rev-parse','HEAD')) -cne $ExpectedParent -or (InvokeGit -a @('rev-parse',('origin/'+$branch))) -cne $ExpectedParent){throw 'git_base_changed'}
 if(InvokeGit -a @('diff','--cached','--name-only')){throw 'preexisting_staged_changes'}
 $files=@('windows-mcp-agent/README.md','windows-mcp-agent/CONTINUITY.md','windows-mcp-agent/CHANGELOG.md','docs/mcp-agent/STATUS.md','docs/mcp-agent/ACTIVITY.md','docs/mcp-agent/DIRECT-0.7.0.md')
 if(!$FinalDocsOnly){
  $files+=@('windows-mcp-agent/main.go','windows-mcp-agent/runtime_api.go','windows-mcp-agent/desktop.go','windows-mcp-agent/desktop_worker.ps1','windows-mcp-agent/desktop_extensions.ps1','windows-mcp-agent/direct_runtime.go','windows-mcp-agent/direct_runtime_test.go','windows-mcp-agent/direct_windows_test.go','windows-mcp-agent/logs.go','windows-mcp-agent/update.go','windows-mcp-agent/maintenance.go','windows-mcp-agent/maintenance_test.go','windows-mcp-agent/process_launch.go','windows-mcp-agent/process_launch_other.go','windows-mcp-agent/process_launch_windows.go','windows-mcp-agent/scripts/test-direct-070.ps1','windows-mcp-agent/scripts/deploy-runtime-070.ps1','windows-mcp-agent/scripts/publish-runtime-070.ps1')
 }
 $null=InvokeGit -a (@('add','--')+$files)
 $null=InvokeGit -a @('-c','core.whitespace=-blank-at-eof','diff','--cached','--check')
 $null=InvokeGit -a @('commit','-m',$Message)
 $sha=InvokeGit -a @('rev-parse','HEAD')
 if($LocalOnly){('LOCAL_COMMIT '+$sha)|Add-Content $log;'PASS local_only_no_network_or_push'|Add-Content $log;return}
 $null=InvokeGit -a @('push','origin',('HEAD:refs/heads/'+$branch))
 $remote=InvokeGit -a @('ls-remote','--exit-code','origin',('refs/heads/'+$branch))
 if(($remote -split '\s+')[0] -cne $sha){throw 'remote_sha_not_verified'}
 ('PUBLISHED '+$sha)|Add-Content $log
 'PASS fast_forward_remote_verified'|Add-Content $log
}catch{('ERROR '+$_.Exception.Message)|Add-Content $log;exit 1}
