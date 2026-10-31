param([string]$HomeDir=(Split-Path $PSScriptRoot -Parent))
. "$PSScriptRoot\Common.ps1"
$s=Read-Install $HomeDir;$HomeDir=$s.home;Assert-Stopped $HomeDir
if ($s.remote_configured) { throw 'Collegamento gia configurato: conservare Config e verificare prima di sostituirlo. Nessuna sovrascrittura automatica.' }
Write-Host ('Usare un NUOVO tunnel per '+$s.device_name+': non aggiungere una replica al tunnel di Desktop1.')
Write-Host ('Nel nuovo tunnel impostare il servizio HTTP http://127.0.0.1:'+$s.port)
Write-Host 'Proteggere il nuovo hostname con Access e Managed OAuth, consentendo solo il proprio account.'
Write-Host 'I valori si inseriscono soltanto su questo PC; nessun token deve essere inviato in chat.'
$hostname=(Read-Host ('Nuovo indirizzo pubblico HTTPS per '+$s.device_name)).Trim().TrimEnd('/')
$team=(Read-Host 'Team domain, es. https://nome.cloudflareaccess.com').Trim().TrimEnd('/')
$aud=(Read-Host 'Application Audience AUD della nuova applicazione Access').Trim()
$tunnelID=(Read-Host ('UUID del NUOVO tunnel per '+$s.device_name)).Trim()
Assert-CloudflareInputs $team $aud $hostname $tunnelID
$secure=Read-Host 'Token del NUOVO tunnel (solo valore eyJ..., input nascosto)' -AsSecureString
$token=[Net.NetworkCredential]::new('', $secure).Password
try {
    if ($token.Length -lt 40 -or $token.Length -gt 4096 -or $token -notmatch '^[A-Za-z0-9_+/=-]+$') { throw 'Formato token non valido.' }
    $normalized=$token.Replace('-','+').Replace('_','/');$normalized=$normalized.PadRight($normalized.Length+(4-$normalized.Length%4)%4,'=')
    try { $metadata=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($normalized))|ConvertFrom-Json } catch { throw 'Token tunnel non decodificabile.' }
    if (-not $metadata.PSObject.Properties['t'] -or $metadata.t -ine $tunnelID) { throw 'Il token non appartiene al tunnel indicato.' }
    $cfPath=Join-Path $HomeDir 'Agent\cloudflared.exe'
    if (Test-Path -LiteralPath $cfPath) { throw 'cloudflared gia presente: nessuna sostituzione automatica.' }
    [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
    Write-Host 'Download cloudflared dalla release ufficiale, con verifica SHA-256...'
    $release=Invoke-RestMethod 'https://api.github.com/repos/cloudflare/cloudflared/releases/latest' -Headers @{'User-Agent'='Jarvis-Installer-1.0.2'} -TimeoutSec 30
    $assets=@($release.assets|Where-Object {$_.name -eq 'cloudflared-windows-amd64.exe'})
    if ($assets.Count -ne 1 -or -not $assets[0].PSObject.Properties['digest'] -or $assets[0].digest -notmatch '^sha256:([a-fA-F0-9]{64})$') { throw 'Checksum ufficiale non disponibile: download non autorizzato a proseguire.' }
    $expected=$matches[1]
    $url=$assets[0].browser_download_url
    if ($url -notmatch '^https://github\.com/cloudflare/cloudflared/releases/download/[A-Za-z0-9_.-]+/cloudflared-windows-amd64\.exe$') { throw 'URL download inatteso.' }
    $download=Join-Path $HomeDir ('Agent\cloudflared-download-'+[guid]::NewGuid().ToString('N')+'.exe')
    Invoke-WebRequest $url -OutFile $download -UseBasicParsing -TimeoutSec 180
    if ((Get-FileHash -LiteralPath $download).Hash -ine $expected) { Remove-Item -LiteralPath $download;throw 'Checksum cloudflared errato.' }
    $help=& $download tunnel run --help 2>$null
    if ($LASTEXITCODE -ne 0 -or ($help -join ' ') -notmatch 'token-file') { throw 'cloudflared non supporta token-file.' }
    [IO.File]::Move($download,$cfPath)
    Write-Utf8 (Join-Path $HomeDir 'Config\cloudflared.json') (@{release=$release.tag_name;sha256=$expected;tunnel_id=$tunnelID}|ConvertTo-Json)
    Write-Utf8 (Join-Path $HomeDir 'Config\tunnel-token.txt') $token
    $values=Read-AgentEnv $HomeDir;$values.JARVIS_CF_TEAM_DOMAIN=$team;$values.JARVIS_CF_APP_AUD=$aud
    Write-AgentEnv $HomeDir $values
    $s.remote_configured=$true;$s.public_endpoint=$hostname+'/mcp'
    Write-Utf8 (Join-Path $HomeDir 'Config\installation.json') ($s|ConvertTo-Json)
    Write-Utf8 (Join-Path $HomeDir 'Collegamento-ChatGPT.txt') ('Nome plugin: '+$s.plugin_name+"`r`nURL MCP: "+$s.public_endpoint+"`r`nAutenticazione: OAuth Cloudflare Access / Managed OAuth.`r`nCollegamento remoto da verificare in ChatGPT; nessuna credenziale da incollare nella chat.`r`n")
    Write-Host ('Configurazione locale completata. Nuovo plugin '+$s.plugin_name+': '+$s.public_endpoint)
    Write-Host 'Avviare l agente e completare il login OAuth dal plugin. DNS, Access e Managed OAuth devono gia essere predisposti nell account Cloudflare.'
} finally { $token=$null;$secure.Dispose() }
