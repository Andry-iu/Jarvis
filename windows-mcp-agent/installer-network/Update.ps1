[CmdletBinding()]
param([string]$HomeDir,[string]$ExpectedUserSid)
. "$PSScriptRoot\Common.ps1"
$principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Avviare AGGIORNA.cmd con diritti amministrativi.'}
if($ExpectedUserSid -and $ExpectedUserSid -cne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value){throw 'Usare UAC con lo stesso utente Windows.'}
if(-not $HomeDir){$HomeDir=Read-Host 'Cartella Jarvis gia installata [C:\Jarvis]';if(-not $HomeDir){$HomeDir='C:\Jarvis'}}
$HomeDir=Get-FullPath $HomeDir
$settings=Read-Install $HomeDir
$package=Get-Content (Join-Path $PSScriptRoot 'package.json') -Raw|ConvertFrom-Json
if($package.installer_version -ne '2.1.0' -or $package.agent_version -ne '0.8.1' -or $package.source_commit -notmatch '^[a-f0-9]{40}$'){throw 'Pacchetto aggiornamento inatteso'}
foreach($file in $package.files){if($file.path -notmatch '^[A-Za-z0-9_.-]+$' -or (Get-FileHash (Join-Path $PSScriptRoot $file.path)).Hash -ine $file.sha256){throw 'Pacchetto incompleto o alterato'}}
$info=Invoke-LocalTool $HomeDir 'machine_info'
if($info.version -eq $package.agent_version){Write-Host ('Agente gia alla versione '+$info.version);return}
if($info.version -ne '0.8.0'){throw 'Questo aggiornamento assistito richiede agente 0.8.0; nessuna sostituzione eseguita.'}
$id=[guid]::NewGuid().ToString('N')
$release=Join-Path $HomeDir ('Releases\0.8.1-'+$id)
New-Item -ItemType Directory $release|Out-Null;Protect-Folder $release
foreach($name in @('jarvis-mcp.exe','update-agent.ps1')){
 $entry=@($package.files|Where-Object {$_.path -eq $name})
 if($entry.Count -ne 1){throw 'Manifest incompleto'}
 Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $release $name)
 if((Get-FileHash (Join-Path $release $name)).Hash -ine $entry[0].sha256){throw 'Copia candidato non verificata'}
}
$values=Read-AgentEnv $HomeDir
foreach($key in $values.Keys){[Environment]::SetEnvironmentVariable($key,$values[$key],'Process')}
$env:JARVIS_MCP_TOKEN=Get-LocalToken $HomeDir
$installed=Join-Path $HomeDir 'Agent\jarvis-mcp.exe'
$previous=(Get-FileHash $installed).Hash.ToLowerInvariant()
$batch=Join-Path $release 'update.cmd'
$command='powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+(Join-Path $release 'update-agent.ps1')+'" -HomeDir "'+$HomeDir+'" -Candidate "'+(Join-Path $release 'jarvis-mcp.exe')+'" -ExpectedHash '+$package.agent_sha256+' -ExpectedPreviousHash '+$previous+' -ExpectedPreviousVersion '+$info.version+' -TargetVersion '+$package.agent_version+' -ExpectedMachine '+$info.machine_id+' -SourceCommit '+$package.source_commit
Write-Utf8 $batch ('REM JARVIS-UPDATE-ID: '+$id+"`r`n@echo off`r`n"+$command+"`r`nexit /b %errorlevel%`r`n")
# Same agent-owned, detached update protocol as remote deployments. No second
# updater implementation or update task, and no secrets in batch or tool args.
$bytes=[IO.File]::ReadAllBytes($batch)
$hash=(Get-FileHash $batch).Hash.ToLowerInvariant()
# The old loopback MCP endpoint correctly requires Cloudflare for update tools.
# Use this PC's existing private LAN listener, pinned to its local certificate;
# local administrator bootstrap needs neither Cloudflare nor file transfer.
Add-Type @'
using System;
using System.Net;
using System.Text;
using System.Security.Cryptography;
public static class JarvisLocalUpdate {
 public static string Call(string url,string token,string pin,string body) {
  var req=(HttpWebRequest)WebRequest.Create(url);req.Proxy=null;req.Method="POST";
  req.AllowAutoRedirect=false;req.Timeout=15000;req.ContentType="application/json";
  req.Headers["Authorization"]="Bearer "+token;
  req.ServerCertificateValidationCallback=(sender,cert,chain,errors)=>{
   if(cert==null)return false;
   using(var sha=SHA256.Create())return BitConverter.ToString(sha.ComputeHash(cert.GetRawCertData())).Replace("-","").Equals(pin,StringComparison.OrdinalIgnoreCase);
  };
  var data=Encoding.UTF8.GetBytes(body);req.ContentLength=data.Length;
  using(var stream=req.GetRequestStream())stream.Write(data,0,data.Length);
  using(var response=req.GetResponse())using(var reader=new System.IO.StreamReader(response.GetResponseStream()))return reader.ReadToEnd();
 }
}
'@
[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
$peer=Get-Content (Join-Path $HomeDir 'Config\peer-enrollment.json') -Raw|ConvertFrom-Json
$listener=Get-Content (Join-Path $HomeDir 'Config\peer-listener.json') -Raw|ConvertFrom-Json
$peerPort=($listener.listen -split ':')[-1]
if($peer.machine_id -cne $info.machine_id -or $peerPort -notmatch '^\d{4,5}$'){throw 'Identita listener locale non verificata'}
function Send-Update([string]$method,[hashtable]$arguments){
 $body=@{target_machine_id=$info.machine_id;method=$method;arguments=$arguments;visited=@()}|ConvertTo-Json -Depth 10 -Compress
 $response=[JarvisLocalUpdate]::Call(('https://127.0.0.1:'+$peerPort+'/gateway'),$peer.token,$peer.certificate_sha256,$body)|ConvertFrom-Json
 if($response.isError){throw ('Aggiornamento locale rifiutato: '+$method)}
}
Send-Update 'update_prepare' @{update_id=$id;sha256=$hash;content=[Text.Encoding]::UTF8.GetString($bytes)}
Send-Update 'update_execute' @{update_id=$id;sha256=$hash}
Write-Host 'Aggiornamento avviato con backup e prova di ripristino. Attendere senza chiudere la sessione.'
for($i=0;$i -lt 180;$i++){
 Start-Sleep -Seconds 1
 try{
  $current=Invoke-LocalTool $HomeDir 'machine_info'
  if($current.machine_id -eq $info.machine_id -and $current.version -eq $package.agent_version -and (Get-FileHash $installed).Hash -ieq $package.agent_sha256){
   $log=Join-Path $HomeDir 'Logs\diagnostics\self-update.log'
   $text=[IO.File]::ReadAllText($log)
   if($text.LastIndexOf('PASS common_batch_update_identity_config_maintenance') -gt $text.LastIndexOf('START ')){Write-Host ('Aggiornamento verificato: '+$settings.device_name+' versione '+$current.version);return}
  }
 }catch{}
}
throw 'Esito non ancora verificato. Non rilanciare alla cieca: leggere Logs\diagnostics\self-update.log e ricevuta aggiornamento.'
