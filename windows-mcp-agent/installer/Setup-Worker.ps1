param([Parameter(Mandatory=$true)][string]$RequestPath,[Parameter(Mandatory=$true)][string]$ExpectedHash)
. "$PSScriptRoot\Prerequisites.ps1"
if((Get-FileHash -LiteralPath $RequestPath).Hash -ine $ExpectedHash){throw 'Richiesta worker alterata.'}
$r=Get-Content -LiteralPath $RequestPath -Raw|ConvertFrom-Json
$dir=Get-FullPath $r.state_dir
if($r.format -ne 1 -or $r.id -notmatch '^[a-f0-9]{32}$' -or $r.user_sid -ne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value -or $r.computer -ine $env:COMPUTERNAME -or (Split-Path $RequestPath -Parent) -ine $dir){throw 'Contesto worker non valido.'}
$resultPath=Join-Path $dir ($r.id+'.result.json')
$result=[pscustomobject]@{id=$r.id;installer_version='1.1.1';state='running';worker=(Get-Record (Get-Process -Id $PID));exit_code=$null;finished_at='';error_code=''}
Write-SetupJson $resultPath $result
$guard=$null
try {
    # Shared per-user lock also survives parent loss while this worker lives.
    $guardPath=Join-Path $env:LOCALAPPDATA 'Jarvis-MCP\dependency.lock'
    New-Item -ItemType Directory -Path (Split-Path $guardPath -Parent) -Force|Out-Null
    $guard=[IO.File]::Open($guardPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    if((Get-FileHash -LiteralPath $r.asset).Hash -ine $r.sha256){throw 'Checksum dipendenza non valido.'}
    if($r.kind -eq 'codex') {
        if($r.version -ne '0.165.0-alpha.1.2' -or $r.sha256 -ine '2d2aacc5270987f1d33eb22969bf1936528a5b09da152fec0eb9d4c97dcc0d8f'){throw 'Installer Codex non approvato.'}
        foreach($key in @((Get-ChildItem Env:).Name)){if($key -match '^(CODEX_|OPENAI_|JARVIS_)'){Remove-Item ('Env:'+ $key)}}
        $env:CODEX_NON_INTERACTIVE='1';$env:CODEX_INSTALLER_USE_RELEASES_OPENAI_COM='0'
        & "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $r.asset -Release '0.165.0-alpha.1.2' *> (Join-Path $dir ($r.id+'.install.log'))
        if($LASTEXITCODE -ne 0){throw 'Installazione Codex fallita.'}
    }elseif($r.kind -in @('git','go')){
        $target=Get-FullPath $r.target
        if(-not $target.StartsWith((Join-Path $dir 'Tools')+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Target worker esterno alla cartella gestita.'}
        if(Test-Path $target){[IO.Directory]::Move($target,($target+'.incomplete-'+[guid]::NewGuid().ToString('N')))}
        New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force|Out-Null
        if($r.kind -eq 'go'){Expand-Archive -LiteralPath $r.asset -DestinationPath $target}
        else{
            # Current Git for Windows SFX accepts -y and -o; no legacy -gm2/
            # -InstallPath and no escaped double backslashes or extra arguments.
            $p=Start-Process $r.asset -ArgumentList ('-y -o"'+$target+'"') -PassThru -Wait -WindowStyle Hidden
            try{if($p.ExitCode -ne 0){throw 'Estrazione Git fallita.'}}finally{$p.Dispose()}
        }
        $exe=if($r.kind -eq 'git'){Join-Path $target 'cmd\git.exe'}else{Join-Path $target 'go\bin\go.exe'}
        $probe=Get-SetupToolProbe $r.kind $exe
        Write-SetupJson (Join-Path $dir ($r.id+'.verification.json')) $probe
        if(-not $probe.available){$result.error_code=('VERIFY_'+$probe.error_code.ToUpperInvariant());throw 'Componente estratto non funzionante.'}
    }else{throw 'Operazione worker non ammessa.'}
    $result.state='completed';$result.exit_code=0
}catch{$result.state='failed';$result.exit_code=1;if(-not $result.error_code){$result.error_code='DEPENDENCY_INSTALL_FAILED'}}
finally{if($guard){$guard.Dispose()};$result.finished_at=[DateTime]::UtcNow.ToString('o');Write-SetupJson $resultPath $result}
exit $result.exit_code
