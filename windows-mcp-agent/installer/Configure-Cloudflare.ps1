param([string]$HomeDir=(Split-Path $PSScriptRoot -Parent),[string]$PreparedExecutable,[string]$PreparedSha256,[string]$PreparedRelease,[switch]$Automatic,$SetupContext,$SetupSettings,[switch]$NoPrompts)
. "$PSScriptRoot\Prerequisites.ps1"
$s=Read-Install $HomeDir;$HomeDir=$s.home
$cfPath=Join-Path $HomeDir 'Agent\cloudflared.exe';$cfConfig=Join-Path $HomeDir 'Config\cloudflared.json'
$pending=Join-Path $HomeDir 'Config\cloudflare-pending.json';$tokenPath=Join-Path $HomeDir 'Config\tunnel-token.txt'
if($s.remote_configured){
    $cf=Get-Content $cfConfig -Raw|ConvertFrom-Json
    $values=Read-AgentEnv $HomeDir
    if((Get-FileHash $cfPath).Hash -ine $cf.sha256 -or -not (Test-Path $tokenPath) -or -not $values['JARVIS_CF_TEAM_DOMAIN'] -or -not $values['JARVIS_CF_APP_AUD']){throw 'Configurazione remota registrata ma incompleta: nessuna sovrascrittura automatica.'}
    Write-Host 'Configurazione Cloudflare locale gia presente; collegamento remoto da verificare in ChatGPT.'
    return
}
Assert-Stopped $HomeDir
if(-not $PreparedExecutable){
    $ctx=Open-SetupState $HomeDir (Join-Path $env:LOCALAPPDATA 'Jarvis-MCP\Setup')
    try{$PreparedExecutable=Get-SetupDownload $ctx 'cloudflared';$PreparedSha256=$ctx.state.assets.cloudflared.sha256;$PreparedRelease=$ctx.state.assets.cloudflared.version}finally{$ctx.lock.Dispose()}
}
if($PreparedSha256 -notmatch '^[a-fA-F0-9]{64}$' -or (Get-FileHash -LiteralPath $PreparedExecutable).Hash -ine $PreparedSha256){throw 'Download cloudflared non verificato.'}
$help=& $PreparedExecutable tunnel run --help 2>$null
if($LASTEXITCODE -ne 0 -or ($help -join ' ') -notmatch 'token-file'){throw 'cloudflared non supporta token-file.'}
if($Automatic){
    if(-not $SetupContext){throw 'Avviare INSTALLA.cmd per la configurazione automatica Cloudflare.'}
    & "$PSScriptRoot\Configure-Cloudflare-Auto.ps1" -HomeDir $HomeDir -SetupContext $SetupContext -SetupSettings $SetupSettings -NoPrompts:$NoPrompts
}
Write-Host ('Usare un NUOVO tunnel per '+$s.device_name+' e servizio HTTP http://127.0.0.1:'+$s.port)
if(-not $Automatic){Write-Host 'Modalita manuale: predisporre DNS, Access e Managed OAuth nel proprio account Cloudflare.'}
if(Test-Path $pending){
    $p=Get-Content $pending -Raw|ConvertFrom-Json
    foreach($pair in @(@('CLOUDFLARE_HOSTNAME','hostname'),@('CLOUDFLARE_TEAM_DOMAIN','team'),@('CLOUDFLARE_APP_AUD','aud'),@('CLOUDFLARE_TUNNEL_ID','tunnel_id'))){
        $configured=Get-SetupSetting $SetupSettings $pair[0]
        if($configured -and $configured.TrimEnd('/') -ine ([string]$p.($pair[1])).TrimEnd('/')){throw ('SETUP_CONFIG_CONFLICT_'+$pair[0])}
    }
}
else{
    $fields=@{}
    foreach($pair in @(@('CLOUDFLARE_HOSTNAME','Indirizzo pubblico HTTPS'),@('CLOUDFLARE_TEAM_DOMAIN','Team domain HTTPS'),@('CLOUDFLARE_APP_AUD','Application Audience AUD Access'),@('CLOUDFLARE_TUNNEL_ID','UUID del tunnel dedicato'))){
        $value=Get-SetupSetting $SetupSettings $pair[0]
        if(-not $value){if($NoPrompts){throw ('SETUP_CONFIG_REQUIRED_'+$pair[0])};$value=(Read-Host $pair[1]).Trim()}
        $fields[$pair[0]]=$value.TrimEnd('/')
    }
    $hostname=$fields.CLOUDFLARE_HOSTNAME;$team=$fields.CLOUDFLARE_TEAM_DOMAIN;$aud=$fields.CLOUDFLARE_APP_AUD;$tunnelID=$fields.CLOUDFLARE_TUNNEL_ID
    Assert-CloudflareInputs $team $aud $hostname $tunnelID
    $p=[pscustomobject]@{hostname=$hostname;team=$team;aud=$aud;tunnel_id=$tunnelID}
    Write-SetupJson $pending $p
}
Assert-CloudflareInputs $p.team $p.aud $p.hostname $p.tunnel_id
$secure=$null;$token=$null
try{
    $configuredToken=Get-SetupSetting $SetupSettings 'CLOUDFLARE_TUNNEL_TOKEN'
    if(Test-Path $tokenPath){
        $token=[IO.File]::ReadAllText($tokenPath)
        if($configuredToken -and $configuredToken -cne $token){throw 'SETUP_CONFIG_CONFLICT_CLOUDFLARE_TUNNEL_TOKEN'}
    }elseif($configuredToken){$token=$configuredToken}
    else{if($NoPrompts){throw 'SETUP_CONFIG_REQUIRED_CLOUDFLARE_TUNNEL_TOKEN'};$secure=Read-Host 'Token del tunnel, input nascosto, mai in chat' -AsSecureString;$token=[Net.NetworkCredential]::new('', $secure).Password}
    if($token.Length -lt 40 -or $token.Length -gt 4096 -or $token -notmatch '^[A-Za-z0-9_+/=-]+$'){throw 'Formato token non valido.'}
    $normalized=$token.Replace('-','+').Replace('_','/');$normalized=$normalized.PadRight($normalized.Length+(4-$normalized.Length%4)%4,'=')
    try{$metadata=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($normalized))|ConvertFrom-Json}catch{throw 'Token tunnel non decodificabile.'}
    if(-not $metadata.PSObject.Properties['t'] -or $metadata.t -ine $p.tunnel_id){throw 'Il token non appartiene al tunnel indicato.'}
    if(Test-Path $cfPath){if((Get-FileHash $cfPath).Hash -ine $PreparedSha256){throw 'cloudflared diverso: nessuna sostituzione implicita.'}}
    else{$temp=$cfPath+'.new-'+[guid]::NewGuid().ToString('N');Copy-Item -LiteralPath $PreparedExecutable -Destination $temp;if((Get-FileHash $temp).Hash -ine $PreparedSha256){throw 'Verifica copia cloudflared fallita.'};[IO.File]::Move($temp,$cfPath)}
    Write-SetupJson $cfConfig @{release=$PreparedRelease;sha256=$PreparedSha256;tunnel_id=$p.tunnel_id}
    if(-not (Test-Path $tokenPath)){Write-Utf8 $tokenPath $token}
    $values=Read-AgentEnv $HomeDir
    if($values['JARVIS_CF_TEAM_DOMAIN'] -ne $p.team -or $values['JARVIS_CF_APP_AUD'] -ne $p.aud){$values['JARVIS_CF_TEAM_DOMAIN']=$p.team;$values['JARVIS_CF_APP_AUD']=$p.aud;Write-AgentEnv $HomeDir $values}
    $s.remote_configured=$true;$s.public_endpoint=$p.hostname+'/mcp'
    Write-SetupJson (Join-Path $HomeDir 'Config\installation.json') $s
    Write-Utf8 (Join-Path $HomeDir 'Collegamento-ChatGPT.txt') ('Nome plugin: '+$s.plugin_name+"`r`nURL MCP: "+$s.public_endpoint+"`r`nAutenticazione: OAuth Cloudflare Access / Managed OAuth.`r`nCollegamento remoto da verificare in ChatGPT; nessuna credenziale da incollare nella chat.`r`n")
    Write-Host ('Configurazione locale pronta: '+$s.plugin_name+'; '+$s.public_endpoint)
}finally{$token=$null;$configuredToken=$null;if($secure){$secure.Dispose()}}
