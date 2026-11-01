[CmdletBinding()]
param([string]$InstallDir,[string]$DeviceName,[string]$ProjectsRoot,[ValidateRange(1024,65535)][int]$Port=8765,[string]$ExpectedUserSid,[string]$SettingsFile,[switch]$RenameResourcesOnly,[switch]$NonInteractive,[switch]$SkipRemote,[switch]$NoTask,[switch]$NoShortcuts)
. "$PSScriptRoot\Prerequisites.ps1"
$principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Avviare INSTALLA.cmd e consentire UAC.'}
if($ExpectedUserSid -and $ExpectedUserSid -ne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value){throw 'UAC eseguito con un altro account: usare lo stesso utente Windows di Codex.'}
if(-not [Environment]::Is64BitOperatingSystem -or $env:PROCESSOR_ARCHITECTURE -ne 'AMD64' -or $PSVersionTable.PSVersion.Major -lt 5){throw 'Richiesti Windows x64 e Windows PowerShell 5.1 a 64 bit.'}
Assert-SetupPackage $PSScriptRoot
$configExplicit=$PSBoundParameters.ContainsKey('SettingsFile')
if(-not $SettingsFile){$SettingsFile=Join-Path $PSScriptRoot 'setup.local.env'}
$setupSettings=Read-SetupSettings $SettingsFile -Required:$configExplicit
try{
    if($setupSettings.present){Write-Host 'Configurazione locale caricata; valori segreti non visualizzati.'}
    if(-not $PSBoundParameters.ContainsKey('NonInteractive')){$NonInteractive=Get-SetupSettingBool $setupSettings 'SETUP_NO_PROMPTS' ([bool]$setupSettings.present)}
    if(-not $PSBoundParameters.ContainsKey('NoTask')){$NoTask=Get-SetupSettingBool $setupSettings 'SETUP_NO_TASK'}
    if(-not $PSBoundParameters.ContainsKey('NoShortcuts')){$NoShortcuts=Get-SetupSettingBool $setupSettings 'SETUP_NO_SHORTCUTS'}
    if(-not $PSBoundParameters.ContainsKey('InstallDir')){$InstallDir=Get-SetupSetting $setupSettings 'INSTALL_DIR'}
    if(-not $PSBoundParameters.ContainsKey('DeviceName')){$DeviceName=Get-SetupSetting $setupSettings 'DEVICE_NAME'}
    if(-not $PSBoundParameters.ContainsKey('ProjectsRoot')){$ProjectsRoot=Get-SetupSetting $setupSettings 'PROJECTS_ROOT'}
    $portExplicit=$PSBoundParameters.ContainsKey('Port') -or $setupSettings.values.ContainsKey('MCP_PORT')
    if(-not $PSBoundParameters.ContainsKey('Port') -and $setupSettings.values.ContainsKey('MCP_PORT')){$Port=[int]$setupSettings.values.MCP_PORT}
    $remoteMode=Get-SetupSetting $setupSettings 'SETUP_REMOTE_MODE' 'auto'
    if($PSBoundParameters.ContainsKey('SkipRemote')){$remoteMode=if($SkipRemote){'skip'}else{'auto'}}
    $allowBrowserLogin=Get-SetupSettingBool $setupSettings 'SETUP_ALLOW_BROWSER_LOGIN' ([bool]$setupSettings.present)
if(-not $InstallDir){
    $default=if(Test-Path D:\){'D:\Jarvis'}else{'C:\Jarvis-Desktop2'}
    if($NonInteractive){$InstallDir=$default}else{$InstallDir=Read-Host ('Cartella Jarvis esistente o nuova ['+$default+']');if(-not $InstallDir){$InstallDir=$default}}
}
$InstallDir=Get-FullPath $InstallDir
$ctx=Open-SetupState $InstallDir (Join-Path $env:LOCALAPPDATA 'Jarvis-MCP\Setup')
try{
    $ctx.state.last_error='';$existing=$null
    if(Test-Path (Join-Path $InstallDir 'Config\installation.json')){$existing=Read-Install $InstallDir}
    elseif(Test-Path (Join-Path $InstallDir 'Jarvis-Install-Intent.json')){
        $existing=Get-Content (Join-Path $InstallDir 'Jarvis-Install-Intent.json') -Raw|ConvertFrom-Json
        if($existing.home -ine $InstallDir -or $existing.computer -ine $env:COMPUTERNAME -or $existing.user_sid -ne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value){throw 'Tentativo appartenente ad altro contesto.'}
    }
    elseif((Test-Path $InstallDir) -and @(Get-ChildItem -LiteralPath $InstallDir -Force).Count){throw 'Cartella non riconosciuta: nessun contenuto viene sovrascritto.'}
    if($existing){
        if($DeviceName -and $DeviceName -ine $existing.device_name){throw 'SETUP_CONFIG_CONFLICT_DEVICE_NAME'}
        if($ProjectsRoot -and (Get-FullPath $ProjectsRoot) -ine $existing.projects_root){throw 'SETUP_CONFIG_CONFLICT_PROJECTS_ROOT'}
        if($portExplicit -and $Port -ne [int]$existing.port){throw 'SETUP_CONFIG_CONFLICT_MCP_PORT'}
        $DeviceName=$existing.device_name;$ProjectsRoot=$existing.projects_root;$Port=[int]$existing.port
    }
    else{
        if(-not $portExplicit){$Port=[int]$ctx.state.port}
        if(-not $DeviceName){$DeviceName=$ctx.state.device_name}
        if(-not $DeviceName){if(-not $NonInteractive){$DeviceName=Read-Host 'Nome del dispositivo [Desktop2]'};if(-not $DeviceName){$DeviceName='Desktop2'}}
        if(-not $ProjectsRoot){$ProjectsRoot=$ctx.state.projects_root}
        if(-not $ProjectsRoot){if(-not $NonInteractive){$ProjectsRoot=Read-Host ('Cartella progetti ['+(Join-Path $InstallDir 'Projects')+']')};if(-not $ProjectsRoot){$ProjectsRoot=Join-Path $InstallDir 'Projects'}}
        if($ctx.state.device_name -and $ctx.state.device_name -ne $DeviceName){throw 'Nome diverso dal registro di ripresa; non cambiarlo implicitamente.'}
    }
    $null=Get-DeviceNames $DeviceName;$ProjectsRoot=Get-FullPath $ProjectsRoot
    if($RenameResourcesOnly){
        if(-not $existing -or -not $existing.remote_configured){throw 'CF_RENAME_REQUIRES_COMPLETED_SETUP'}
        & "$PSScriptRoot\Configure-Cloudflare-Names.ps1" -HomeDir $InstallDir -SetupContext $ctx -SetupSettings $setupSettings -NoPrompts:$NonInteractive
        return
    }
    $ctx.state.device_name=$DeviceName;$ctx.state.projects_root=$ProjectsRoot;$ctx.state.port=$Port;Save-SetupState $ctx 'checking_prerequisites'
    if($NonInteractive -and $remoteMode -ne 'skip' -and (-not $existing -or -not $existing.remote_configured)){
        if($remoteMode -eq 'auto'){
            if(-not (Get-SetupSetting $setupSettings 'CLOUDFLARE_API_TOKEN')){throw 'SETUP_CONFIG_REQUIRED_CLOUDFLARE_API_TOKEN'}
            if(-not (Test-Path (Join-Path $ctx.dir 'cloudflare-auto.json')) -and -not (Get-SetupSetting $setupSettings 'CLOUDFLARE_ACCESS_EMAIL')){throw 'SETUP_CONFIG_REQUIRED_CLOUDFLARE_ACCESS_EMAIL'}
        }else{
            if(-not (Test-Path (Join-Path $InstallDir 'Config\cloudflare-pending.json'))){
                foreach($key in @('CLOUDFLARE_HOSTNAME','CLOUDFLARE_TEAM_DOMAIN','CLOUDFLARE_APP_AUD','CLOUDFLARE_TUNNEL_ID')){if(-not (Get-SetupSetting $setupSettings $key)){throw ('SETUP_CONFIG_REQUIRED_'+$key)}}
            }
            if(-not (Test-Path (Join-Path $InstallDir 'Config\tunnel-token.txt')) -and -not (Get-SetupSetting $setupSettings 'CLOUDFLARE_TUNNEL_TOKEN')){throw 'SETUP_CONFIG_REQUIRED_CLOUDFLARE_TUNNEL_TOKEN'}
        }
    }
    $git=Ensure-SetupTool $ctx 'git';$go=Ensure-SetupTool $ctx 'go';$codex=Ensure-SetupCodex $ctx (Get-SetupSetting $setupSettings 'CODEX_EXECUTABLE')
    Save-SetupState $ctx 'checking_chatgpt_login';Ensure-SetupLogin $codex -NonInteractive:$NonInteractive -AllowBrowserLogin:$allowBrowserLogin
    if(-not $existing){
        Save-SetupState $ctx 'installing_jarvis'
        & "$PSScriptRoot\Install.ps1" -DeviceName $DeviceName -InstallDir $InstallDir -ProjectsRoot $ProjectsRoot -Port $Port -CodexExecutable $codex -NonInteractive -NoStart -NoTask:$NoTask -NoShortcuts:$NoShortcuts
    }elseif((Test-Path (Join-Path $InstallDir 'Config\install-failed.txt')) -and -not (Test-Path (Join-Path $InstallDir 'Config\install-complete.json')) -or ((Test-Path (Join-Path $InstallDir 'Jarvis-Install-Intent.json')) -and -not (Test-Path (Join-Path $InstallDir 'Config\install-complete.json')))){
        Save-SetupState $ctx 'recovering_jarvis'
        & "$PSScriptRoot\Repair.ps1" -HomeDir $InstallDir -CodexExecutable $codex -NonInteractive -NoStart -NoTask:$NoTask -NoShortcuts:$NoShortcuts
    }
    Update-SetupScripts $InstallDir $PSScriptRoot
    $s=Read-Install $InstallDir
    if(-not (Test-Path (Join-Path $InstallDir 'Config\agent.env'))){throw 'Configurazione incompleta non recuperabile automaticamente; dati conservati.'}
    $paths=@((Split-Path $git -Parent),(Split-Path $go -Parent))|Select-Object -Unique
    $toolPath=Join-Path $InstallDir 'Config\toolchain.json';$same=$false
    if(Test-Path $toolPath){$old=Get-Content $toolPath -Raw|ConvertFrom-Json;$same=(($old.paths -join '|') -ieq ($paths -join '|'))}
    if(-not $same){Assert-Stopped $InstallDir;Write-SetupJson $toolPath @{format=1;paths=@($paths)}}
    $prepared='';$preparedAsset=$null
    if(-not $s.remote_configured -and $remoteMode -ne 'skip'){
        $choice=if($remoteMode -eq 'manual'){'m'}else{'s'}
        if(-not $NonInteractive -and -not $setupSettings.present){$choice=Read-Host 'Configurare automaticamente Cloudflare? [S/n; M=manuale]'}
        if($choice -notmatch '^[nN]$'){
            Save-SetupState $ctx 'preparing_cloudflared';$prepared=Get-SetupDownload $ctx 'cloudflared';$preparedAsset=$ctx.state.assets.cloudflared
            Save-SetupState $ctx 'configuring_cloudflare'
            & "$PSScriptRoot\Configure-Cloudflare.ps1" -HomeDir $InstallDir -PreparedExecutable $prepared -PreparedSha256 $preparedAsset.sha256 -PreparedRelease $preparedAsset.version -Automatic:($choice -notmatch '^[mM]$') -SetupContext $ctx -SetupSettings $setupSettings -NoPrompts:$NonInteractive
            $s=Read-Install $InstallDir
        }
    }
    Save-SetupState $ctx 'checking_codex_connection'
    & "$PSScriptRoot\Connect-Codex.ps1" -HomeDir $InstallDir -CodexExecutable $codex -NonInteractive
    $status=Get-Content (Join-Path $InstallDir 'Config\codex-connection.json') -Raw|ConvertFrom-Json
    if(-not $status.codex_connected){throw 'Collegamento Codex non confermato.'}
    $stage=if($s.remote_configured){'local_ready_waiting_chatgpt_test'}else{'local_ready_waiting_cloudflare'}
    $result=[pscustomobject]@{device_name=$s.device_name;plugin_name=$s.plugin_name;home=$s.home;local_ready=$true;codex_connected=$true;remote_configured=[bool]$s.remote_configured;remote_verified=$false;inference_allowed=$status.inference_allowed;blocked_reason=$status.blocked_reason;checked_at=$status.checked_at;resume_with='INSTALLA.cmd';stage=$stage}
    Write-SetupJson (Join-Path $ctx.dir 'summary.json') $result;Save-SetupState $ctx $stage
    $result|Format-List
    Write-Host 'Jarvis e Codex verificati localmente, senza inferenza. Il collegamento da ChatGPT richiede il relativo collaudo.'
    Write-Host ('Registro di ripresa: '+$ctx.dir)
}catch{
    # No transcript: login and local Cloudflare secrets never enter the setup log.
    $ctx.state.last_error=$ctx.state.stage;Save-SetupState $ctx 'waiting_resume'
    Write-Host ('Passaggio non completato. Dati conservati; riprendere con INSTALLA.cmd. Registro: '+$ctx.dir)
    throw
}finally{$ctx.lock.Dispose()}

}finally{Clear-SetupSettings $setupSettings}
