# Shared, secret-safe transport for verified setup payloads only.
function Get-CloudflareSetupHelper($Context){
$node=Ensure-SetupTool $Context 'node'
$helper=Join-Path $PSScriptRoot 'Cloudflare-Provision.cjs'
$manifest=Get-Content (Join-Path $PSScriptRoot 'package.json') -Raw|ConvertFrom-Json
# Installed Scripts do not contain package.json; automatic mode is entered only
# from the verified setup payload, while manual configuration remains standalone.
$entry=@($manifest.files|Where-Object {$_.path -eq 'Cloudflare-Provision.cjs'})
if($entry.Count -ne 1 -or (Get-FileHash $helper).Hash -ine $entry[0].sha256){throw 'Configuratore Cloudflare non verificato.'}
return @{node=$node;helper=$helper}
}
function Invoke-CloudflareHelper($NodeExecutable,[string]$HelperPath,$InputObject){
    $p=$null;$payload=$null;$bytes=$null
    try{
        $psi=New-Object Diagnostics.ProcessStartInfo
        $psi.FileName=$NodeExecutable;$psi.Arguments='"'+$HelperPath+'"';$psi.UseShellExecute=$false;$psi.CreateNoWindow=$true
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
function Clear-InactiveCloudflareLock($Context){
$lockPath=Join-Path $Context.dir 'cloudflare-auto.lock'
if(Test-Path $lockPath){
    $l=Get-Content $lockPath -Raw|ConvertFrom-Json
    if(-not $l.PSObject.Properties['pid'] -or [int]$l.pid -le 0){throw 'Lock Cloudflare non riconosciuto: conservato.'}
    if(Get-Process -Id ([int]$l.pid) -ErrorAction SilentlyContinue){throw 'Configuratore Cloudflare ancora attivo o PID riutilizzato; nessuna ripetizione.'}
    [IO.File]::Move($lockPath,($lockPath+'.inactive-'+[guid]::NewGuid().ToString('N')))
}
 }
