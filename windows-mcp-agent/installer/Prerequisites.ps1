# Resumable prerequisites. No inference, account reads or paid services.
. "$PSScriptRoot\Common.ps1"
. "$PSScriptRoot\Setup-Settings.ps1"
function Write-SetupJson([string]$Path,$Value) {
    $tmp=$Path+'.new-'+[guid]::NewGuid().ToString('N')
    Write-Utf8 $tmp ($Value|ConvertTo-Json -Depth 20)
    if(Test-Path -LiteralPath $Path){[IO.File]::Replace($tmp,$Path,$Path+'.previous')}else{[IO.File]::Move($tmp,$Path)}
}
function Assert-SetupPackage([string]$PackageDir) {
    $m=Get-Content (Join-Path $PackageDir 'package.json') -Raw|ConvertFrom-Json
    if($m.installer_version -ne '1.2.2' -or $m.agent_version -ne '0.6.1'){throw 'Pacchetto non compatibile.'}
    foreach($f in $m.files){if(($f.path -like '*.env' -and $f.path -ne 'setup.example.env') -or $f.path -match '^setup\.local\.env(\.|$)' -or $f.path -like '*.secret.env*'){throw 'SETUP_CONFIG_SECRET_IN_MANIFEST'};if($f.path -notmatch '^[A-Za-z0-9_.-]+$' -or (Get-FileHash (Join-Path $PackageDir $f.path)).Hash -ine $f.sha256){throw ('Pacchetto incompleto o alterato: '+$f.path)}}
}
function Open-SetupState([string]$HomeDir,[string]$StateBase) {
    $HomeDir=Get-FullPath $HomeDir
    $sha=[Security.Cryptography.SHA256]::Create()
    try{$key=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($HomeDir.ToLowerInvariant())))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
    $dir=Join-Path $StateBase $key
    $null=Get-FullPath $dir
    New-Item -ItemType Directory -Path $dir -Force|Out-Null;Protect-Folder $dir
    $lock=[IO.File]::Open((Join-Path $dir 'setup.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try {
        $path=Join-Path $dir 'progress.json';$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        if(Test-Path $path){$state=Get-Content $path -Raw|ConvertFrom-Json;if($state.format -ne 1 -or $state.home -ine $HomeDir -or $state.computer -ine $env:COMPUTERNAME -or $state.user_sid -ne $sid){throw 'Registro appartenente ad altro PC, utente o cartella.'}}
        else{$state=[pscustomobject]@{format=1;home=$HomeDir;computer=$env:COMPUTERNAME;user_sid=$sid;installer_version='1.2.2';stage='created';updated_at='';device_name='';projects_root='';port=8765;assets=[pscustomobject]@{};jobs=[pscustomobject]@{};tools=[pscustomobject]@{};last_error=''}}
        return @{dir=$dir;path=$path;lock=$lock;state=$state}
    }catch{$lock.Dispose();throw}
}
function Save-SetupState($Context,[string]$Stage) {
    $Context.state.stage=$Stage;$Context.state.updated_at=[DateTime]::UtcNow.ToString('o');Write-SetupJson $Context.path $Context.state
    Write-Host ('Installazione: '+$Stage)
}
function Get-SetupToolProbe([ValidateSet('git','go','node')][string]$Kind,[string]$Exe) {
    $result=[pscustomobject]@{kind=$Kind;path=$Exe;available=$false;version='';error_code='missing_executable';native_error=$null;exit_code=$null;checked_at=[DateTime]::UtcNow.ToString('o')}
    $p=$null
    try{
        if(-not (Test-Path -LiteralPath $Exe -PathType Leaf)){return $result}
        $psi=New-Object Diagnostics.ProcessStartInfo
        $psi.FileName=$Exe;$psi.Arguments=if($Kind -eq 'go'){'version'}else{'--version'}
        $psi.UseShellExecute=$false;$psi.CreateNoWindow=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
        $p=[Diagnostics.Process]::Start($psi)
        $stdout=$p.StandardOutput.ReadToEndAsync();$stderr=$p.StandardError.ReadToEndAsync()
        if(-not $p.WaitForExit(15000)){$p.Kill();$null=$p.WaitForExit(3000);$result.error_code='probe_timeout';return $result}
        $result.exit_code=$p.ExitCode;$text=$stdout.Result.Trim();$null=$stderr.Result
        if($p.ExitCode -ne 0){$result.error_code='version_exit_nonzero';return $result}
        $valid=if($Kind -eq 'go'){$text -match '^go version go(\d+\.\d+(?:\.\d+)?) windows/amd64' -and [version]$matches[1] -ge [version]'1.23.2'}elseif($Kind -eq 'node'){$text -match '^v(\d+)\.\d+\.\d+$' -and [int]$matches[1] -ge 22}else{$text -match '^git version \d+\.\d+'}
        if(-not $valid){$result.error_code='version_unrecognized';return $result}
        $result.available=$true;$result.version=$text;$result.error_code='';return $result
    }catch{
        $result.error_code='start_failed';$ex=$_.Exception
        while($ex){if($ex -is [ComponentModel.Win32Exception]){$result.native_error=$ex.NativeErrorCode};$ex=$ex.InnerException}
        return $result
    }finally{if($p){$p.Dispose()}}
}
function Find-SetupTool([string]$Kind,$Context) {
    $candidates=New-Object Collections.Generic.List[string]
    if($Context.state.tools.PSObject.Properties[$Kind]){$candidates.Add([string]$Context.state.tools.$Kind)}
    if($Context.state.assets.PSObject.Properties[$Kind]){
        $managed=Join-Path $Context.dir ('Tools\'+$Kind+'-'+$Context.state.assets.$Kind.version)
        if($Kind -eq 'git'){$candidates.Add((Join-Path $managed 'cmd\git.exe'))}elseif($Kind -eq 'node'){$candidates.Add((Join-Path $managed ('node-'+$Context.state.assets.node.version+'-win-x64\node.exe')))}else{$candidates.Add((Join-Path $managed 'go\bin\go.exe'))}
    }
    try{foreach($cmd in @(Get-Command ($Kind+'.exe') -CommandType Application -All -ErrorAction SilentlyContinue)){if($cmd.Source){$candidates.Add($cmd.Source)}}}catch{}
    if($Kind -eq 'git'){$candidates.Add((Join-Path $env:ProgramFiles 'Git\cmd\git.exe'))}
    if($Kind -eq 'node'){$candidates.Add((Join-Path $env:ProgramFiles 'nodejs\node.exe'))}
    if($Kind -eq 'go'){$candidates.Add((Join-Path $env:ProgramFiles 'Go\bin\go.exe'))}
    $probes=New-Object Collections.Generic.List[object]
    foreach($exe in @($candidates|Select-Object -Unique)){
        $probe=Get-SetupToolProbe $Kind $exe;$probes.Add($probe)
        if($probe.available){return $exe}
    }
    $Context.state|Add-Member -NotePropertyName last_tool_probes -NotePropertyValue $probes.ToArray() -Force
    return ''
}
function Test-LegacyGitReceiptRepair($Context,[string]$Kind,[string]$AssetPath,[string]$Target,$Receipt) {
    # One narrowly identified 1.1.0 defect; never replay a running/uncertain worker.
    try{
        if($Kind -ne 'git' -or $Receipt.state -ne 'completed' -or $Receipt.exit_code -ne 0 -or $Receipt.PSObject.Properties['installer_version']){return $false}
        if(Get-OwnedProcess $Receipt.worker "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"){return $false}
        $job=$Context.state.jobs.git
        if($job.id -notmatch '^[a-f0-9]{32}$' -or $Receipt.id -ne $job.id){return $false}
        $requestPath=Join-Path $Context.dir ($job.id+'.request.json')
        $request=Get-Content -LiteralPath $requestPath -Raw|ConvertFrom-Json
        if($request.format -ne 1 -or $request.PSObject.Properties['installer_version'] -or $request.kind -ne 'git' -or $request.id -ne $job.id){return $false}
        if($request.user_sid -ne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value -or $request.computer -ine $env:COMPUTERNAME -or $request.state_dir -ine $Context.dir){return $false}
        $asset=$Context.state.assets.git
        if($request.version -ne $asset.version -or $request.sha256 -ine $asset.sha256 -or $request.asset -ine $AssetPath -or $request.target -ine $Target){return $false}
        if($Target -ine (Join-Path $Context.dir ('Tools\git-'+$asset.version)) -or $AssetPath -ine (Join-Path $Context.dir ('Downloads\'+$asset.name))){return $false}
        if((Get-FileHash -LiteralPath $AssetPath).Hash -ine $asset.sha256){return $false}
        # Positive ready state is handled before Invoke-SetupOperation; failed probe
        # here identifies the known extraction defect, without changing its receipt.
        return (-not (Get-SetupToolProbe 'git' (Join-Path $Target 'cmd\git.exe')).available)
    }catch{return $false}
}

function Get-SetupAsset($Context,[ValidateSet('git','go','node','codex','cloudflared')][string]$Kind) {
    if($Context.state.assets.PSObject.Properties[$Kind]){return $Context.state.assets.$Kind}
    [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
    if($Kind -eq 'codex'){$asset=[pscustomobject]@{version='0.165.0-alpha.1.2';name='codex-install.ps1';url='https://github.com/openai/codex/releases/download/rust-v0.165.0-alpha.1.2/install.ps1';sha256='2d2aacc5270987f1d33eb22969bf1936528a5b09da152fec0eb9d4c97dcc0d8f'}}
    elseif($Kind -eq 'node'){
        $releases=Invoke-RestMethod 'https://nodejs.org/dist/index.json' -TimeoutSec 30
        $r=@($releases|Where-Object {$_.version -match '^v24\.\d+\.\d+$' -and $_.lts -and 'win-x64-zip' -in $_.files}|Sort-Object {[version]$_.version.Substring(1)} -Descending)[0]
        if(-not $r){throw 'Node 24 LTS x64 non trovato nei metadati ufficiali.'}
        $name='node-'+$r.version+'-win-x64.zip'
        $checks=(Invoke-WebRequest ('https://nodejs.org/dist/'+$r.version+'/SHASUMS256.txt') -UseBasicParsing -TimeoutSec 30).Content
        $lines=@($checks -split "`n"|Where-Object {$_ -match ('^([a-fA-F0-9]{64})\s+'+[regex]::Escape($name)+'\s*$')})
        if($lines.Count -ne 1 -or $lines[0] -notmatch '^([a-fA-F0-9]{64})\s+'){throw 'Checksum ufficiale Node non valido.'}
        $asset=[pscustomobject]@{version=$r.version;name=$name;url=('https://nodejs.org/dist/'+$r.version+'/'+$name);sha256=$matches[1]}
    }
    elseif($Kind -eq 'go'){
        $releases=Invoke-RestMethod 'https://go.dev/dl/?mode=json' -TimeoutSec 30
        $r=@($releases|Where-Object {$_.stable})[0]
        $a=@($r.files|Where-Object {$_.os -eq 'windows' -and $_.arch -eq 'amd64' -and $_.kind -eq 'archive'})
        if($a.Count -ne 1 -or $a[0].filename -notmatch '^go\d+\.\d+(?:\.\d+)?\.windows-amd64\.zip$' -or $a[0].sha256 -notmatch '^[a-fA-F0-9]{64}$'){throw 'Metadati ufficiali Go non validi.'}
        $asset=[pscustomobject]@{version=$r.version;name=$a[0].filename;url=('https://go.dev/dl/'+$a[0].filename);sha256=$a[0].sha256}
    }else{
        if($Kind -eq 'git'){$repo='git-for-windows/git';$pattern='^PortableGit-[0-9.]+-64-bit\.7z\.exe$'}else{$repo='cloudflare/cloudflared';$pattern='^cloudflared-windows-amd64\.exe$'}
        $r=Invoke-RestMethod ('https://api.github.com/repos/'+$repo+'/releases/latest') -Headers @{'User-Agent'='Jarvis-Setup-1.2.2'} -TimeoutSec 30
        $a=@($r.assets|Where-Object {$_.name -match $pattern})
        if($a.Count -ne 1 -or -not $a[0].PSObject.Properties['digest'] -or $a[0].digest -notmatch '^sha256:([a-fA-F0-9]{64})$'){throw ('Checksum ufficiale assente: '+$Kind)}
        $digest=$matches[1];$prefix='https://github.com/'+$repo+'/releases/download/'
        if(-not $a[0].browser_download_url.StartsWith($prefix,[StringComparison]::Ordinal) -or $r.tag_name -notmatch '^[A-Za-z0-9_.-]+$'){throw 'Origine download non valida.'}
        $asset=[pscustomobject]@{version=$r.tag_name;name=$a[0].name;url=$a[0].browser_download_url;sha256=$digest}
    }
    $Context.state.assets|Add-Member -NotePropertyName $Kind -NotePropertyValue $asset -Force
    Save-SetupState $Context ('resolved_'+$Kind);return $asset
}
function Get-SetupDownload($Context,[string]$Kind) {
    $a=Get-SetupAsset $Context $Kind
    if($a.name -notmatch '^[A-Za-z0-9_.-]+$' -or $a.sha256 -notmatch '^[a-fA-F0-9]{64}$'){throw 'Registro download non valido.'}
    $allowed=if($Kind -eq 'codex'){'^https://github\.com/openai/codex/releases/download/rust-v0\.165\.0-alpha\.1\.2/install\.ps1$'}elseif($Kind -eq 'go'){'^https://go\.dev/dl/go[0-9.]+\.windows-amd64\.zip$'}elseif($Kind -eq 'node'){'^https://nodejs\.org/dist/v24\.[0-9]+\.[0-9]+/node-v24\.[0-9]+\.[0-9]+-win-x64\.zip$'}elseif($Kind -eq 'git'){'^https://github\.com/git-for-windows/git/releases/download/[A-Za-z0-9_.-]+/PortableGit-[0-9.]+-64-bit\.7z\.exe$'}else{'^https://github\.com/cloudflare/cloudflared/releases/download/[A-Za-z0-9_.-]+/cloudflared-windows-amd64\.exe$'}
    if($a.url -notmatch $allowed -or ($Kind -eq 'codex' -and $a.sha256 -ine '2d2aacc5270987f1d33eb22969bf1936528a5b09da152fec0eb9d4c97dcc0d8f')){throw 'Origine o versione nel registro non valida.'}
    $cache=Join-Path $Context.dir 'Downloads';New-Item -ItemType Directory -Path $cache -Force|Out-Null
    $path=Join-Path $cache $a.name
    if(Test-Path $path){if((Get-FileHash $path).Hash -ieq $a.sha256){return $path};[IO.File]::Move($path,($path+'.invalid-'+[guid]::NewGuid().ToString('N')))}
    $part=$path+'.part-'+[guid]::NewGuid().ToString('N');Save-SetupState $Context ('downloading_'+$Kind)
    Invoke-WebRequest $a.url -OutFile $part -UseBasicParsing -TimeoutSec 600
    if((Get-FileHash $part).Hash -ine $a.sha256){throw ('Checksum errato: '+$Kind+'; file conservato, non eseguito.')}
    [IO.File]::Move($part,$path);return $path
}
function Invoke-SetupOperation($Context,[ValidateSet('git','go','node','codex')][string]$Kind,[string]$AssetPath,[string]$Target) {
    if($Context.state.jobs.PSObject.Properties[$Kind]){
        $old=$Context.state.jobs.$Kind
        $receipt=Join-Path $Context.dir ($old.id+'.result.json')
        if(-not (Test-Path $receipt)){throw ('Esito installazione '+$Kind+' incerto: nessuna ripetizione automatica. Registro: '+$Context.dir)}
        $r=Get-Content $receipt -Raw|ConvertFrom-Json
        if($r.state -eq 'running'){
            $alive=Get-OwnedProcess $r.worker "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
            if($alive){throw ('Installazione '+$Kind+' ancora in corso; riaprire lo stesso INSTALLA.cmd quando termina.')}
            throw ('Installazione '+$Kind+' interrotta con esito incerto; nessuna ripetizione automatica.')
        }
        if($r.state -ne 'failed'){
            if(-not (Test-LegacyGitReceiptRepair $Context $Kind $AssetPath $Target $r)){throw ('Installazione '+$Kind+' dichiarata riuscita ma componente non verificato; consultare il registro.')}
            if(-not $Context.state.PSObject.Properties['repaired_legacy_jobs']){$Context.state|Add-Member -NotePropertyName repaired_legacy_jobs -NotePropertyValue @()}
            $Context.state.repaired_legacy_jobs=@($Context.state.repaired_legacy_jobs)+[pscustomobject]@{id=$old.id;reason='legacy_110_git_sfx_arguments';at=[DateTime]::UtcNow.ToString('o')}
            Save-SetupState $Context 'repairing_verified_110_git_defect'
        }
    }
    $id=[guid]::NewGuid().ToString('N');$a=$Context.state.assets.$Kind
    $request=[pscustomobject]@{format=1;installer_version='1.2.2';kind=$Kind;asset=$AssetPath;sha256=$a.sha256;target=$Target;version=$a.version;state_dir=$Context.dir;id=$id;user_sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;computer=$env:COMPUTERNAME}
    $requestPath=Join-Path $Context.dir ($id+'.request.json');Write-SetupJson $requestPath $request
    $Context.state.jobs|Add-Member -NotePropertyName $Kind -NotePropertyValue ([pscustomobject]@{id=$id;state='dispatched'}) -Force
    Save-SetupState $Context ('installing_'+$Kind)
    $worker=Join-Path $PSScriptRoot 'Setup-Worker.ps1'
    $args='-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+$worker+'" -RequestPath "'+$requestPath+'" -ExpectedHash '+(Get-FileHash $requestPath).Hash
    $p=Start-Process "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -ArgumentList $args -PassThru -WindowStyle Hidden
    try{while(-not $p.WaitForExit(1000)){};if($p.ExitCode -ne 0){throw ('Installazione '+$Kind+' non riuscita; rilanciare INSTALLA.cmd dopo aver risolto la causa, registro '+$Context.dir)}}finally{$p.Dispose()}
    $receipt=Get-Content (Join-Path $Context.dir ($id+'.result.json')) -Raw|ConvertFrom-Json
    if($receipt.state -ne 'completed'){throw ('Completamento '+$Kind+' non confermato.')}
}
function Ensure-SetupTool($Context,[ValidateSet('git','go','node')][string]$Kind) {
    $exe=Find-SetupTool $Kind $Context
    if(-not $exe){
        $asset=Get-SetupDownload $Context $Kind
        $target=Join-Path $Context.dir ('Tools\'+$Kind+'-'+$Context.state.assets.$Kind.version)
        $null=Get-FullPath $target
        # Target is written only by the worker, never merged into an existing tree.
        Invoke-SetupOperation $Context $Kind $asset $target
        $exe=if($Kind -eq 'git'){Join-Path $target 'cmd\git.exe'}elseif($Kind -eq 'node'){Join-Path $target ('node-'+$Context.state.assets.node.version+'-win-x64\node.exe')}else{Join-Path $target 'go\bin\go.exe'}
        $Context.state.tools|Add-Member -NotePropertyName $Kind -NotePropertyValue $exe -Force
        $probe=Get-SetupToolProbe $Kind $exe
        if(-not $probe.available){Write-SetupJson (Join-Path $Context.dir ($Kind+'-verification.json')) $probe;throw ('Verifica '+$Kind+' fallita: '+$probe.error_code+'; diagnostica nel registro di installazione.')}
    }
    $Context.state.tools|Add-Member -NotePropertyName $Kind -NotePropertyValue $exe -Force
    Save-SetupState $Context ('verified_'+$Kind);return $exe
}
function Ensure-SetupCodex($Context,[string]$PreferredExecutable='') {
    $exe=Find-Codex $PreferredExecutable
    if($PreferredExecutable -and -not $exe){throw 'SETUP_CONFIG_CODEX_EXECUTABLE_NOT_USABLE'}
    if(-not $exe){$asset=Get-SetupDownload $Context 'codex';Invoke-SetupOperation $Context 'codex' $asset ''; $exe=Find-Codex}
    if(-not $exe){throw 'CLI Codex non verificata dopo installazione.'}
    $Context.state.tools|Add-Member -NotePropertyName codex -NotePropertyValue $exe -Force
    Save-SetupState $Context 'verified_codex';return $exe
}
function Ensure-SetupLogin([string]$Exe,[switch]$NonInteractive,[switch]$AllowBrowserLogin) {
    $saved=@{};foreach($item in @(Get-ChildItem Env:)){if($item.Name -match '^(CODEX_|OPENAI_)'){$saved[$item.Name]=$item.Value;Remove-Item ('Env:'+$item.Name)}}
    try {
    # Capture, never print login-status output (may contain account details).
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try{$status=& $Exe login status 2>&1;$ok=($LASTEXITCODE -eq 0 -and ($status -join ' ') -match 'ChatGPT')}finally{$ErrorActionPreference=$old}
    if($ok){return}
    if($NonInteractive -and -not $AllowBrowserLogin){throw 'Accesso ChatGPT richiesto: rilanciare INSTALLA.cmd in modalita interattiva.'}
    Write-Host 'Completa nel browser l accesso con ChatGPT Business. Non acquistare crediti e non usare API key.'
    # No config overwrite, no API-key login, no TUI, no exec/turn.
    & $Exe login
    if($LASTEXITCODE -ne 0){throw 'Accesso non completato; riprendere con lo stesso INSTALLA.cmd.'}
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try{$status=& $Exe login status 2>&1;$ok=($LASTEXITCODE -eq 0 -and ($status -join ' ') -match 'ChatGPT')}finally{$ErrorActionPreference=$old}
    if(-not $ok){throw 'Account ChatGPT non confermato; nessuna elaborazione avviata.'}
    }finally{foreach($key in $saved.Keys){Set-Item ('Env:'+$key) $saved[$key]}}
}
function Update-SetupScripts([string]$HomeDir,[string]$PackageDir) {
    $s=Read-Install $HomeDir;$manifest=Get-Content (Join-Path $PackageDir 'package.json') -Raw|ConvertFrom-Json
    $history=Get-Content (Join-Path $PackageDir 'script-history.json') -Raw|ConvertFrom-Json
    $exclude=@('Install.ps1','Repair.ps1','Setup.ps1','Setup-Worker.ps1','Configure-Cloudflare-Names.ps1','Test-Installer.ps1','Test-Bootstrap.ps1','Test-SetupSettings.ps1')
    $changes=New-Object Collections.Generic.List[object]
    foreach($f in @($manifest.files|Where-Object {($_.path -like '*.ps1' -or $_.path -eq 'Cloudflare-Provision.cjs') -and $_.path -notin $exclude}|Sort-Object @{Expression={if($_.path -eq 'Start.ps1'){0}else{1}}})){
        $target=Join-Path $HomeDir ('Scripts\'+$f.path);$old='missing'
        if(Test-Path $target){$null=Get-FullPath $target;$old=(Get-FileHash $target).Hash;if($old -ieq $f.sha256){continue}
            if(-not $history.PSObject.Properties[$f.path]){throw ('Script locale non riconosciuto: '+$f.path)}
            if($old -notin @($history.($f.path))){throw ('Script locale modificato: '+$f.path+'; nessuna sovrascrittura.')}
        }
        $changes.Add([pscustomobject]@{name=$f.path;before=$old;after=$f.sha256;target=$target})
    }
    $pending=Join-Path $HomeDir 'Config\installer-scripts-pending.json'
    if($changes.Count -eq 0){if(Test-Path $pending){Remove-Item -LiteralPath $pending};return}
    Assert-Stopped $HomeDir
    $guard=[IO.File]::Open((Join-Path $HomeDir 'Config\runner.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try{
        Assert-Stopped $HomeDir
        $backup=Join-Path $HomeDir ('Backups\installer-scripts-'+[guid]::NewGuid().ToString('N'));New-Item -ItemType Directory -Path $backup|Out-Null
        foreach($c in $changes){if($c.before -ne 'missing'){Copy-Item -LiteralPath $c.target -Destination (Join-Path $backup $c.name);if((Get-FileHash (Join-Path $backup $c.name)).Hash -ine $c.before){throw 'Backup script non verificato.'}}}
        Write-SetupJson $pending @{version='1.2.2';backup=$backup;files=$changes.ToArray()}
        foreach($c in $changes){
            if($c.before -ne 'missing' -and (Get-FileHash $c.target).Hash -ine $c.before){throw 'Script cambiato durante aggiornamento; nessuna sovrascrittura.'}
            $tmp=$c.target+'.new-'+[guid]::NewGuid().ToString('N');Copy-Item -LiteralPath (Join-Path $PackageDir $c.name) -Destination $tmp
            if((Get-FileHash $tmp).Hash -ine $c.after){throw 'Copia script non verificata.'}
            if(Test-Path $c.target){[IO.File]::Replace($tmp,$c.target,(Join-Path $backup ($c.name+'.atomic-backup')))}else{[IO.File]::Move($tmp,$c.target)}
        }
        Write-SetupJson (Join-Path $backup 'receipt.json') @{version='1.2.2';files=$changes.ToArray();at=[DateTime]::UtcNow.ToString('o')}
        Remove-Item -LiteralPath $pending
    }finally{$guard.Dispose()}
}
