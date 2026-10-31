$ErrorActionPreference='Stop'
$repo='D:\Jarvis\MCP-Source'
$log='D:\Jarvis\Logs\diagnostics\v60-final-check.log'
try{
 if((git -C $repo config --bool core.sparseCheckout) -eq 'true'){
  git -C $repo sparse-checkout add docs/mcp-agent
  if($LASTEXITCODE -ne 0){throw 'sparse_documentation_checkout_failed'}
 }
 $head=git -C $repo rev-parse HEAD
 $remote=git -C $repo rev-parse origin/reconcile-windows-mcp-agent-20261031
 if($head -ne $remote){throw 'head_remote_mismatch'}
 if(git -C $repo status --porcelain -uno){throw 'tracked_changes_present'}
 if(-not (Test-Path (Join-Path $repo 'docs\mcp-agent\VALIDATION-0.6.0-20261031.md'))){throw 'local_continuation_docs_missing'}
 $hash=(Get-FileHash 'D:\Jarvis\jarvis-mcp.exe' -Algorithm SHA256).Hash
 if($hash -ne '68D7D4DB1B63576B81EC67E7DCAB137763F7BE38A6B841BA5A8B46F4B5A1DEFE'){throw 'installed_hash_changed'}
 $listener=@(Get-NetTCPConnection -LocalPort 8765 -State Listen)
 if($listener.Count -ne 1){throw 'ambiguous_listener'}
 $agentProcess=Get-CimInstance Win32_Process -Filter ('ProcessId='+$listener[0].OwningProcess)
 if($agentProcess.ExecutablePath -ine 'D:\Jarvis\jarvis-mcp.exe'){throw 'listener_path_changed'}
 Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
public static class JarvisToken060 {
 [DllImport("kernel32.dll",SetLastError=true)] static extern IntPtr OpenProcess(uint access,bool inherit,int pid);
 [DllImport("advapi32.dll",SetLastError=true)] static extern bool OpenProcessToken(IntPtr process,uint access,out IntPtr token);
 [DllImport("advapi32.dll",SetLastError=true)] static extern bool GetTokenInformation(IntPtr token,int kind,out int value,int size,out int returned);
 [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
 public static bool Elevated(int pid) {
  IntPtr p=OpenProcess(0x1000,false,pid),t=IntPtr.Zero;
  if(p==IntPtr.Zero)throw new Win32Exception(Marshal.GetLastWin32Error());
  try {
   if(!OpenProcessToken(p,8,out t))throw new Win32Exception(Marshal.GetLastWin32Error());
   int value,size;
   if(!GetTokenInformation(t,20,out value,4,out size))throw new Win32Exception(Marshal.GetLastWin32Error());
   return value!=0;
  } finally {if(t!=IntPtr.Zero)CloseHandle(t);CloseHandle(p);}
 }
}
'@
 $elevated=[JarvisToken060]::Elevated($listener[0].OwningProcess)
 if(-not $elevated){throw 'installed_listener_not_elevated'}
 $temporary=@(Get-NetTCPConnection -LocalPort 8766,8767 -State Listen -ErrorAction SilentlyContinue)
 if($temporary.Count -ne 0){throw 'temporary_listener_remains'}
 $backup='D:\Jarvis\Backups\mcp-0.6.0-20261031'
 if((Get-FileHash (Join-Path $backup 'jarvis-mcp-0.5.9.exe')).Hash -ne 'CB7B9B3E0C3A9CA9133E738E0412CD518AF6BEA7E79FEE2C65830776635540C5'){throw 'rollback_binary_hash_changed'}
 $stateFiles=@(Get-ChildItem (Join-Path $backup 'state-snapshot') -Filter '*.json' -File -Recurse)
 if($stateFiles.Count -eq 0){throw 'empty_backup_state'}
 foreach($name in 'agent.env','secrets.env','codex-cost-proof.json'){if(-not (Test-Path (Join-Path $backup $name))){throw 'backup_file_missing'}}
 $headers=@{Authorization=('Bearer '+$env:JARVIS_MCP_TOKEN);Accept='application/json, text/event-stream'}
 $body=@{jsonrpc='2.0';id=1;method='tools/list';params=@{}}|ConvertTo-Json -Compress
 $catalog=Invoke-RestMethod 'http://127.0.0.1:8765/mcp' -Method Post -ContentType 'application/json' -Headers $headers -Body $body -TimeoutSec 10
 $queues=@($catalog.result.tools|Where-Object {$_.name -like 'queue_*'})
 if($queues.Count -ne 5){throw 'queue_catalog_missing'}
 @{
  checked_at=[DateTime]::UtcNow.ToString('o');head=$head;origin=$remote;tracked_clean=$true;
  installed_hash=$hash;listener_pid=$listener[0].OwningProcess;listener_elevated=$elevated;
  backup_state_files=$stateFiles.Count;temporary_listeners=$temporary.Count;local_docs=$true;
  server_tool_count=@($catalog.result.tools).Count;queue_tool_count=$queues.Count;
  legacy_backup_preserved=(Test-Path (Join-Path $repo 'windows-mcp-agent\update.go.bak'));
  status='PASS'
 }|ConvertTo-Json -Compress|Set-Content $log -Encoding UTF8
}catch{('ERROR '+$_.Exception.Message)|Set-Content $log -Encoding UTF8;exit 1}
