param([Parameter(Mandatory=$true)][string]$HomeDir,[Parameter(Mandatory=$true)]$SetupContext,$SetupSettings,[switch]$NoPrompts)
. "$PSScriptRoot\Prerequisites.ps1"
$s=Read-Install $HomeDir;Assert-Stopped $HomeDir
if($s.home -ine $SetupContext.state.home -or $s.computer -ine $SetupContext.state.computer -or $s.user_sid -ne $SetupContext.state.user_sid){throw 'Contesto Cloudflare diverso dal registro di installazione.'}
. "$PSScriptRoot\Cloudflare-Client.ps1"
$client=Get-CloudflareSetupHelper $SetupContext
Clear-InactiveCloudflareLock $SetupContext
Write-Host 'Token API Cloudflare: https://dash.cloudflare.com/profile/api-tokens'
Write-Host 'Custom: Account Tunnel Edit; Access Apps and Policies Edit; Access Organizations/Identity Providers/Groups Read; Zone Read; DNS Edit.'
Write-Host 'Limitare al proprio account e dominio. Nessun acquisto, modifica piano o permesso billing.'
$secure=$null;$apiToken=$null;$request=$null
try{
    $apiToken=Get-SetupSetting $SetupSettings 'CLOUDFLARE_API_TOKEN'
    if(-not $apiToken){
        if($NoPrompts){throw 'SETUP_CONFIG_REQUIRED_CLOUDFLARE_API_TOKEN'}
        $secure=Read-Host 'Token API Cloudflare, input nascosto (non il token tunnel)' -AsSecureString
        $apiToken=[Net.NetworkCredential]::new('', $secure).Password.Trim()
    }
    $discovery=Invoke-CloudflareHelper $client.node $client.helper @{command='discover';token=$apiToken}
    $journal=Join-Path $SetupContext.dir 'cloudflare-auto.json'
    $previous=$null
    if(Test-Path $journal){$previous=Get-Content $journal -Raw|ConvertFrom-Json}
    $inputs=Resolve-SetupCloudflareInputs $SetupSettings @($discovery.zones) $previous $s.device_name -NoPrompts:$NoPrompts
    $zoneID=$inputs.zone_id;$email=$inputs.email;$label=$inputs.label
    $zone=@($discovery.zones|Where-Object {$_.id -eq $zoneID})
    if($zone.Count -ne 1){throw 'Dominio del tentativo precedente non disponibile con questo token.'}
    Write-Host ('Risorse dedicate: '+$label+'.'+$zone[0].name+'; accesso: '+$email+'; origine: http://127.0.0.1:'+$s.port)
    Write-Host 'Desktop1 e le risorse preesistenti non vengono modificati. Le risorse incomplete restano protette e registrate per la ripresa.'
    $names=Get-CloudflareResourceNames $SetupSettings $s.device_name
    $request=@{device_name=$s.device_name;tunnel_name=$names.tunnel;policy_name=$names.policy;application_name=$names.application;command='provision';token=$apiToken;state_dir=$SetupContext.dir;config_dir=(Join-Path $s.home 'Config');identity=@{home=$s.home;computer=$s.computer;user_sid=$s.user_sid};zone_id=$zoneID;label=$label;email=$email;port=[int]$s.port}
    $result=Invoke-CloudflareHelper $client.node $client.helper $request
    Write-Host ('Cloudflare predisposto: '+$result.hostname+'; collegamento da ChatGPT ancora da collaudare.')
}finally{if($request){$request.token=$null};$apiToken=$null;if($secure){$secure.Dispose()}}
