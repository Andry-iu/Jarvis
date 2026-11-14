[CmdletBinding()]
param([string]$DeviceName,[string]$InstallDir,[string]$ProjectsRoot,[ValidateSet('local','cloudflare')][string]$Connection,[int]$Port=8765,[int]$PeerPort=8766,[string]$ExpectedUserSid,[switch]$NonInteractive,[switch]$NoTask,[switch]$NoStart)
. "$PSScriptRoot\Common.ps1"
$principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Avviare INSTALLA.cmd con diritti amministrativi.'}
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
if($ExpectedUserSid -and $sid -cne $ExpectedUserSid){throw 'UAC eseguito con un altro utente: usare lo stesso account Windows.'}
if(-not $DeviceName){if($NonInteractive){throw 'DeviceName obbligatorio'};$DeviceName=Read-Host 'Quale nome vuoi assegnare a questo computer?'}
$DeviceName=$DeviceName.Trim()
if(-not $DeviceName -or $DeviceName.Length -gt 160 -or $DeviceName -match '[\x00-\x1f]'){throw 'Nome non valido: usare 1-160 caratteri senza caratteri di controllo.'}
if(-not $InstallDir){if(-not $NonInteractive){$InstallDir=Read-Host 'Cartella di installazione [C:\Jarvis]'};if(-not $InstallDir){$InstallDir='C:\Jarvis'}}
$InstallDir=Get-FullPath $InstallDir
if(-not $ProjectsRoot){$ProjectsRoot=Join-Path $InstallDir 'Projects'};$ProjectsRoot=Get-FullPath $ProjectsRoot
if(-not $Connection){
 if($NonInteractive){$Connection='local'}else{
  Write-Host '1 - Attraverso un altro agente sulla rete locale (nessun dato Cloudflare)'
  Write-Host '2 - Accesso diretto da ChatGPT tramite Cloudflare e connettore MCP dedicato'
  $choice=Read-Host 'Modalita di collegamento [1]'
  if($choice -eq '2'){$Connection='cloudflare'}elseif($choice -in @('','1')){$Connection='local'}else{throw 'Scelta non valida'}
 }
}
if($Port -lt 1024 -or $Port -gt 65535 -or $PeerPort -lt 1024 -or $PeerPort -gt 65535 -or $PeerPort -eq $Port){throw 'Porte non valide'}
foreach($private in @('Config','State','Agent','Scripts','Logs','Backups')){if(Test-Overlap $ProjectsRoot (Join-Path $InstallDir $private)){throw 'Progetti e stato privato devono essere separati'}}
if($ProjectsRoot -ieq $InstallDir -or $InstallDir.StartsWith($ProjectsRoot+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'La root progetti non deve contenere i file privati'}
if((Test-Path $InstallDir) -and @(Get-ChildItem $InstallDir -Force).Count){throw 'Cartella non vuota: nessuna installazione esistente viene sovrascritta. Aggiornare tramite agente.'}
foreach($p in @($Port,$PeerPort)){if(Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue){throw 'Porta occupata; nessun processo viene fermato'}}
$package=Get-Content (Join-Path $PSScriptRoot 'package.json') -Raw|ConvertFrom-Json
if($package.installer_version -ne '0.8.2' -or $package.agent_version -ne '0.8.2'){throw 'Pacchetto inatteso'}
foreach($file in $package.files){if($file.path -notmatch '^[A-Za-z0-9_.-]+$' -or (Get-FileHash (Join-Path $PSScriptRoot $file.path)).Hash -ine $file.sha256){throw 'Pacchetto incompleto o alterato'}}
$taskName='JarvisAgent-'+[guid]::NewGuid().ToString('N').Substring(0,12);$taskCreated=$false;$firewall=$false
try{
 New-Item -ItemType Directory -Force $InstallDir|Out-Null;Protect-Folder $InstallDir
 foreach($n in @('Agent','Scripts','Config','State','Logs','Backups')){New-Item -ItemType Directory (Join-Path $InstallDir $n)|Out-Null}
 New-Item -ItemType Directory -Force $ProjectsRoot|Out-Null
 foreach($file in $package.files){
  if($file.path -eq 'jarvis-mcp.exe'){$to=Join-Path $InstallDir 'Agent\jarvis-mcp.exe'}
  elseif($file.path -like '*.ps1' -and $file.path -notin @('Install.ps1','Test-NetworkInstaller.ps1')){$to=Join-Path $InstallDir ('Scripts\'+$file.path)}
  elseif($file.path -like '*.cjs'){$to=Join-Path $InstallDir ('Scripts\'+$file.path)}else{continue}
  Copy-Item (Join-Path $PSScriptRoot $file.path) $to
  if((Get-FileHash $to).Hash -ine $file.sha256){throw 'Copia payload non verificata'}
 }
 $settings=@{format=1;installer_version='0.8.2';device_name=$DeviceName;plugin_name=('Jarvis-'+$DeviceName);home=$InstallDir;port=$Port;projects_root=$ProjectsRoot;computer=$env:COMPUTERNAME;user_sid=$sid;task_name=$taskName;task_registered=$false;remote_configured=$false;public_endpoint='';agent_version=$package.agent_version;agent_sha256=$package.agent_sha256;source_commit=$package.source_commit;installed_at=[DateTime]::UtcNow.ToString('o');connection=$Connection}
 Write-Utf8 (Join-Path $InstallDir 'Config\installation.json') ($settings|ConvertTo-Json -Depth 10)
 $bytes=New-Object byte[] 32;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();try{$rng.GetBytes($bytes)}finally{$rng.Dispose()}
 Write-Utf8 (Join-Path $InstallDir 'Config\secrets.env') ('JARVIS_MCP_TOKEN='+[Convert]::ToBase64String($bytes)+"`r`n")
 $values=@{JARVIS_HOME=$InstallDir;JARVIS_PROJECTS_ROOT=$ProjectsRoot;JARVIS_STATE_DIR=(Join-Path $InstallDir 'State\runtime-v1');JARVIS_DESKTOP_ENABLED='1';JARVIS_CODEX_ENABLED='0'}
 $codex=Get-Command codex.exe -ErrorAction SilentlyContinue
 if($codex){$values.JARVIS_CODEX_ENABLED='1';$values.JARVIS_CODEX_EXE=$codex.Source}
 Write-AgentEnv $InstallDir $values
 $init=Start-Child (Join-Path $InstallDir 'Agent\jarvis-mcp.exe') ('-init-peer auto:'+$PeerPort) $InstallDir 'peer-init'
 try{if(-not $init.process.WaitForExit(15000) -or $init.process.ExitCode -ne 0){throw 'Provisioning rete non riuscito'};$null=$init.out_task.Wait(3000);$null=$init.err_task.Wait(3000)}finally{$init.out.Dispose();$init.err.Dispose();$init.process.Dispose()}

 New-NetFirewallRule -Name $taskName -DisplayName ('Jarvis '+$DeviceName) -Direction Inbound -Action Allow -Protocol TCP -LocalPort $PeerPort -RemoteAddress LocalSubnet -Profile Private,Domain -Program (Join-Path $InstallDir 'Agent\jarvis-mcp.exe')|Out-Null;$firewall=$true
 if(-not $NoTask){
  $action=New-ScheduledTaskAction -Execute "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -Argument ('-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "'+(Join-Path $InstallDir 'Scripts\Start.ps1')+'" -HomeDir "'+$InstallDir+'"')
  $trigger=New-ScheduledTaskTrigger -AtLogOn -User ([Security.Principal.WindowsIdentity]::GetCurrent().Name)
  $principal=New-ScheduledTaskPrincipal -UserId $sid -LogonType Interactive -RunLevel Highest
  $options=New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew
  Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $options|Out-Null;$taskCreated=$true;$settings.task_registered=$true
 }
 Write-Utf8 (Join-Path $InstallDir 'Config\installation.json') ($settings|ConvertTo-Json -Depth 10)
 foreach($pair in @(@('Avvia','Start.ps1'),@('Verifica','Check.ps1'),@('Arresta','Stop.ps1'),@('Abbina','Pair-Agent.ps1'),@('Configura-Cloudflare','Configure-Cloudflare.ps1'))){Write-Utf8 (Join-Path $InstallDir ($pair[0]+'.cmd')) ('@echo off'+"`r`n"+'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Scripts\'+$pair[1]+'" -HomeDir "%~dp0."'+"`r`n"+'pause'+"`r`n")}
 if($Connection -eq 'cloudflare'){
  try{& (Join-Path $InstallDir 'Scripts\Configure-Cloudflare.ps1') -HomeDir $InstallDir}catch{Write-Warning 'Cloudflare non completato: agente locale conservato. Riprendere da Configura-Cloudflare.cmd con agente fermo.'}
 }
 Write-Utf8 (Join-Path $InstallDir 'Config\install-complete.json') (@{installer_version='0.8.2';at=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json)
 if(-not $NoStart){if($taskCreated){Start-ScheduledTask $taskName}else{Start-Process powershell.exe -WindowStyle Hidden -ArgumentList ('-NoProfile -ExecutionPolicy Bypass -File "'+(Join-Path $InstallDir 'Scripts\Start.ps1')+'" -HomeDir "'+$InstallDir+'"')|Out-Null}}
 Write-Host ('Installazione completata: '+$DeviceName+'. Controllo e inoltro sempre disponibili, senza ruoli.')
 Write-Host 'Avvio automatico all accesso a Windows dopo ogni riavvio. Il controllo grafico richiede una sessione utente attiva.'
 Write-Host 'Ora chiedi in chat di collegare questo agente tramite un gateway. Conferma sul nuovo PC solo dopo aver confrontato il codice mostrato in chat. Nessun file da copiare.'
}catch{
 if($taskCreated){Unregister-ScheduledTask $taskName -Confirm:$false -ErrorAction SilentlyContinue}
 if($firewall){Remove-NetFirewallRule -Name $taskName -ErrorAction SilentlyContinue}
 if(Test-Path (Join-Path $InstallDir 'Config')){Write-Utf8 (Join-Path $InstallDir 'Config\install-failed.txt') 'Installazione incompleta: file conservati; nessun riavvio automatico o sovrascrittura cieca.'}
 throw
}
