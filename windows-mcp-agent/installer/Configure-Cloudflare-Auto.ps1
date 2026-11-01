param([Parameter(Mandatory=$true)][string]$HomeDir,[Parameter(Mandatory=$true)]$SetupContext)
. "$PSScriptRoot\Prerequisites.ps1"
$s=Read-Install $HomeDir;Assert-Stopped $HomeDir
if($s.home -ine $SetupContext.state.home -or $s.computer -ine $SetupContext.state.computer -or $s.user_sid -ne $SetupContext.state.user_sid){throw 'Contesto Cloudflare diverso dal registro di installazione.'}
$node=Ensure-SetupTool $SetupContext 'node'
$helper=Join-Path $PSScriptRoot 'Cloudflare-Provision.cjs'
$manifest=Get-Content (Join-Path $PSScriptRoot 'package.json') -Raw|ConvertFrom-Json
# Installed Scripts do not contain package.json; automatic mode is entered only
# from the verified setup payload, while manual configuration remains standalone.
$entry=@($manifest.files|Where-Object {$_.path -eq 'Cloudflare-Provision.cjs'})
if($entry.Count -ne 1 -or (Get-FileHash $helper).Hash -ine $entry[0].sha256){throw 'Configuratore Cloudflare non verificato.'}
function Invoke-CloudflareHelper($InputObject){
    $p=$null;$payload=$null;$bytes=$null
    try{
        $psi=New-Object Diagnostics.ProcessStartInfo
        $psi.FileName=$node;$psi.Arguments='"'+$helper+'"';$psi.UseShellExecute=$false;$psi.CreateNoWindow=$true
        $psi.RedirectStandardInput=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
        foreach($key in @($psi.EnvironmentVariables.Keys)){if($key -match '^(NODE_|CODEX_|OPENAI_|JARVIS_)'){$psi.EnvironmentVariables.Remove($key)}}
        $p=[Diagnostics.Process]::Start($psi)
        $stdout=$p.StandardOutput.ReadToEndAsync();$stderr=$p.StandardError.ReadToEndAsync()
        $payload=$InputObject|ConvertTo-Json -Depth 12 -Compress
        $bytes=[Text.Encoding]::UTF8.GetBytes($payload)
        $p.StandardInput.BaseStream.Write($bytes,0,$bytes.Length);$p.StandardInput.BaseStream.Flush();$p.StandardInput.Close()
        $payload=$null;[Array]::Clear($bytes,0,$bytes.Length);$bytes=$null
        while(-not $p.WaitForExit(1000)){}
        # Never print stderr, raw JSON, credentials or HTTP response bodies.
        $null=$stderr.Result
        try{$r=$stdout.Result|ConvertFrom-Json}catch{throw 'CF_HELPER_RESPONSE_INVALID'}
        if(-not $r.ok -or $p.ExitCode -ne 0){
            $code=if($r.PSObject.Properties['error'] -and $r.error -match '^CF_[A-Z0-9_]+$'){$r.error}else{'CF_HELPER_FAILED'}
            throw $code
        }
        return $r.result
    }finally{if($bytes){[Array]::Clear($bytes,0,$bytes.Length)};$payload=$null;if($p){$p.Dispose()}}
}
$lockPath=Join-Path $SetupContext.dir 'cloudflare-auto.lock'
if(Test-Path $lockPath){
    $l=Get-Content $lockPath -Raw|ConvertFrom-Json
    if(-not $l.PSObject.Properties['pid'] -or [int]$l.pid -le 0){throw 'Lock Cloudflare non riconosciuto: conservato.'}
    if(Get-Process -Id ([int]$l.pid) -ErrorAction SilentlyContinue){throw 'Configuratore Cloudflare ancora attivo o PID riutilizzato; nessuna ripetizione.'}
    [IO.File]::Move($lockPath,($lockPath+'.inactive-'+[guid]::NewGuid().ToString('N')))
}
Write-Host 'Token API Cloudflare: https://dash.cloudflare.com/profile/api-tokens'
Write-Host 'Custom: Account Tunnel Edit; Access Apps and Policies Edit; Access Organizations/Identity Providers/Groups Read; Zone Read; DNS Edit.'
Write-Host 'Limitare al proprio account e dominio. Nessun acquisto, modifica piano o permesso billing.'
$secure=$null;$apiToken=$null;$request=$null
try{
    $secure=Read-Host 'Token API Cloudflare, input nascosto (non il token tunnel)' -AsSecureString
    $apiToken=[Net.NetworkCredential]::new('', $secure).Password.Trim()
    $discovery=Invoke-CloudflareHelper @{command='discover';token=$apiToken}
    $journal=Join-Path $SetupContext.dir 'cloudflare-auto.json'
    if(Test-Path $journal){
        $previous=Get-Content $journal -Raw|ConvertFrom-Json
        $zoneID=$previous.desired.zone_id;$email=$previous.desired.email
        $label=$previous.desired.hostname.Substring(0,$previous.desired.hostname.Length-$previous.desired.zone.Length-1)
        Write-Host ('Ripresa Cloudflare per '+$previous.desired.hostname)
    }else{
        $zones=@($discovery.zones)
        if($zones.Count -eq 1){$zoneID=$zones[0].id}
        else{
            for($n=0;$n -lt $zones.Count;$n++){Write-Host (($n+1).ToString()+') '+$zones[$n].name+' / '+$zones[$n].account_name)}
            $selection=Read-Host 'Numero del dominio da usare'
            if($selection -notmatch '^[0-9]+$' -or [int]$selection -lt 1 -or [int]$selection -gt $zones.Count){throw 'Scelta dominio non valida.'}
            $zoneID=$zones[[int]$selection-1].id
        }
        $defaultLabel='jarvis-'+($s.device_name.ToLowerInvariant() -replace '[ _]+','-').Trim('-')
        $label=Read-Host ('Nome pubblico prima del dominio ['+$defaultLabel+']');if(-not $label){$label=$defaultLabel}
        $email=(Read-Host 'Email esatta autorizzata ad accedere al plugin con Cloudflare Access').Trim()
    }
    $zone=@($discovery.zones|Where-Object {$_.id -eq $zoneID})
    if($zone.Count -ne 1){throw 'Dominio del tentativo precedente non disponibile con questo token.'}
    Write-Host ('Risorse dedicate: '+$label+'.'+$zone[0].name+'; accesso: '+$email+'; origine: http://127.0.0.1:'+$s.port)
    Write-Host 'Desktop1 e le risorse preesistenti non vengono modificati. Le risorse incomplete restano protette e registrate per la ripresa.'
    $request=@{command='provision';token=$apiToken;state_dir=$SetupContext.dir;config_dir=(Join-Path $s.home 'Config');identity=@{home=$s.home;computer=$s.computer;user_sid=$s.user_sid};zone_id=$zoneID;label=$label;email=$email;port=[int]$s.port}
    $result=Invoke-CloudflareHelper $request
    Write-Host ('Cloudflare predisposto: '+$result.hostname+'; collegamento da ChatGPT ancora da collaudare.')
}finally{if($request){$request.token=$null};$apiToken=$null;if($secure){$secure.Dispose()}}
