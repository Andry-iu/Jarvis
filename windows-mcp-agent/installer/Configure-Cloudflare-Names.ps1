param([Parameter(Mandatory=$true)][string]$HomeDir,[Parameter(Mandatory=$true)]$SetupContext,$SetupSettings,[switch]$NoPrompts,[switch]$InspectOnly)
. "$PSScriptRoot\Prerequisites.ps1"
. "$PSScriptRoot\Cloudflare-Client.ps1"
$s=Read-Install $HomeDir
if($s.home -ine $SetupContext.state.home -or $s.computer -ine $SetupContext.state.computer -or $s.user_sid -ne $SetupContext.state.user_sid){throw 'CF_RESUME_IDENTITY_OR_INPUT_CHANGED'}
$journal=Join-Path $SetupContext.dir 'cloudflare-auto.json'
if(-not (Test-Path $journal)){throw 'CF_RENAME_REQUIRES_JOURNAL'}
$previous=Get-Content $journal -Raw|ConvertFrom-Json
$cf=Get-Content (Join-Path $HomeDir 'Config\cloudflared.json') -Raw|ConvertFrom-Json
$envValues=Read-AgentEnv $HomeDir
if($cf.tunnel_id -ine $previous.steps.tunnel.id -or $s.public_endpoint -ine ('https://'+$previous.desired.hostname+'/mcp') -or $envValues['JARVIS_CF_TEAM_DOMAIN'] -ine $previous.desired.team){throw 'CF_RENAME_LOCAL_CONFIG_MISMATCH'}
$expectedAud=$envValues['JARVIS_CF_APP_AUD']
if($expectedAud -notmatch '^[a-fA-F0-9]{64}$'){throw 'CF_RENAME_LOCAL_CONFIG_MISMATCH'}
$client=Get-CloudflareSetupHelper $SetupContext
Clear-InactiveCloudflareLock $SetupContext
$secure=$null;$apiToken=$null;$request=$null
try{
    $apiToken=Get-SetupSetting $SetupSettings 'CLOUDFLARE_API_TOKEN'
    if(-not $apiToken){
        if($NoPrompts){throw 'SETUP_CONFIG_REQUIRED_CLOUDFLARE_API_TOKEN'}
        $secure=Read-Host 'Token API Cloudflare, input nascosto' -AsSecureString
        $apiToken=[Net.NetworkCredential]::new('', $secure).Password.Trim()
    }
    $discovery=Invoke-CloudflareHelper $client.node $client.helper @{command='discover';token=$apiToken}
    $null=Resolve-SetupCloudflareInputs $SetupSettings @($discovery.zones) $previous $s.device_name -NoPrompts
    $names=Get-CloudflareResourceNames $SetupSettings $s.device_name
    $request=@{expected_tunnel_id=$cf.tunnel_id;expected_aud=$expectedAud;device_name=$s.device_name;command=$(if($InspectOnly){'inspect-names'}else{'rename-resources'});token=$apiToken;state_dir=$SetupContext.dir;identity=@{home=$s.home;computer=$s.computer;user_sid=$s.user_sid};tunnel_name=$names.tunnel;policy_name=$names.policy;application_name=$names.application}
    $result=Invoke-CloudflareHelper $client.node $client.helper $request
    Write-SetupJson (Join-Path $SetupContext.dir 'cloudflare-names-result.json') $result
    Write-Host ('Nomi verificati: tunnel='+$result.tunnel_name+'; applicazione='+$result.application_name+'; policy='+$result.policy_name)
    if($InspectOnly){$result|ConvertTo-Json -Compress;return}
    Write-Host 'Rinomina tramite ID esistenti. Agente e turni Codex non avviati, arrestati o reinstallati.'
}finally{if($request){$request.token=$null};$apiToken=$null;if($secure){$secure.Dispose()}}
