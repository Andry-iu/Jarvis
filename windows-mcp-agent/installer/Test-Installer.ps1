param([string]$PackageDir=$PSScriptRoot,[string]$TestRoot=('D:\Jarvis\InstallerTests\'+[guid]::NewGuid().ToString('N')),[string]$LegacyZipPath)
. "$PackageDir\Common.ps1"
$report=New-Object Collections.Generic.List[string]
$installs=New-Object Collections.Generic.List[string]
$runners=New-Object Collections.Generic.List[Diagnostics.Process]
function Pass([string]$Text) { $report.Add('PASS '+$Text) }
function Expect-Refusal([scriptblock]$Action,[string]$Text) { $refused=$false;try { &$Action } catch {$refused=$true};if (-not $refused) { throw ('Mancato rifiuto: '+$Text) };Pass $Text }
function Wait-Agent([string]$Dir) {
    for ($n=0;$n -lt 90;$n++) { try { return Invoke-LocalTool $Dir 'machine_info' } catch { Start-Sleep -Milliseconds 300 } }
    throw 'Agente test non raggiungibile.'
}
function Start-Runner([string]$Dir) {
    $p=Start-Process powershell.exe -ArgumentList ('-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+(Join-Path $Dir 'Scripts\Start.ps1')+'" -HomeDir "'+$Dir+'"') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $Dir 'runner-out.txt') -RedirectStandardError (Join-Path $Dir 'runner-error.txt')
    $runners.Add($p);return $p
}
New-Item -ItemType Directory -Path $TestRoot|Out-Null
try {
    foreach ($f in Get-ChildItem -LiteralPath $PackageDir -Filter '*.ps1') {
        $tokens=$null;$errors=$null;$null=[Management.Automation.Language.Parser]::ParseFile($f.FullName,[ref]$tokens,[ref]$errors)
        if ($errors.Count) { throw ('Errore sintassi: '+$f.Name+' '+$errors[0].Message) }
    };Pass 'powershell_parser'
    $a=Join-Path $TestRoot 'Desktop 2';$b=Join-Path $TestRoot 'Desktop B'
    & "$PackageDir\Install.ps1" -InstallDir $a -Port 8768 -NonInteractive -NoShortcuts -NoStart
    $installs.Add($a)
    & "$PackageDir\Install.ps1" -DeviceName 'Officina CNC' -InstallDir $b -Port 8769 -NonInteractive -NoTask -NoShortcuts -NoStart
    $installs.Add($b);Pass 'fresh_install_two_distinct_roots_with_spaces'
    $deniedRoot=Join-Path $TestRoot 'blocked-codex'
    $deniedDir=Join-Path $deniedRoot 'app\resources';New-Item -ItemType Directory -Path $deniedDir -Force|Out-Null
    $deniedExe=Join-Path $deniedDir 'codex.exe';[IO.File]::WriteAllBytes($deniedExe,(New-Object byte[] 128))
    $acl=Get-Acl $deniedExe
    $rule=New-Object Security.AccessControl.FileSystemAccessRule([Security.Principal.WindowsIdentity]::GetCurrent().User, [Security.AccessControl.FileSystemRights]::ExecuteFile, [Security.AccessControl.AccessControlType]::Deny)
    $acl.AddAccessRule($rule);Set-Acl -LiteralPath $deniedExe -AclObject $acl
    $accessDenied=$false
    try { & $deniedExe --version 2>$null } catch { $ex=$_.Exception;while($ex){if($ex -is [ComponentModel.Win32Exception] -and $ex.NativeErrorCode -eq 5){$accessDenied=$true};$ex=$ex.InnerException} }
    if(-not $accessDenied){throw 'La fixture non riproduce errore nativo Accesso negato.'};Pass 'real_native_access_denied_fixture'
    $priorAppxFunction=Get-Item Function:\Get-AppxPackage -ErrorAction SilentlyContinue
    $global:JarvisTestCodexPackageRoot=$deniedRoot
    function global:Get-AppxPackage { param($Name,$ErrorAction) [pscustomobject]@{InstallLocation=$global:JarvisTestCodexPackageRoot} }
    try {
        if(Find-Codex){throw 'Codex negato abilitato.'};Pass 'discovery_access_denied_returns_disabled'
        $d=Join-Path $TestRoot 'Codex non accessibile'
        & "$PackageDir\Install.ps1" -InstallDir $d -Port 8770 -NonInteractive -NoTask -NoShortcuts -NoStart
        $installs.Add($d)
        if((Read-AgentEnv $d).JARVIS_CODEX_ENABLED -ne '0'){throw 'Ponte Codex attivo nonostante rifiuto.'};Pass 'installation_continues_with_codex_disabled'
        if($LegacyZipPath){
            $legacy=Join-Path $TestRoot 'legacy-package';Expand-Archive -LiteralPath $LegacyZipPath -DestinationPath $legacy
            $legacyInstall=Get-ChildItem $legacy -Filter Install.ps1 -Recurse|Select-Object -First 1
            if(-not $legacyInstall){throw 'Pacchetto 1.0.1 di regressione mancante.'}
            $incomplete=Join-Path $TestRoot 'Installazione incompleta'
            Expect-Refusal { & $legacyInstall.FullName -InstallDir $incomplete -Port 8771 -NonInteractive -NoTask -NoShortcuts -NoStart } 'old_101_reproduces_install_failure'
            $originalSecret=(Get-FileHash (Join-Path $incomplete 'Config\secrets.env')).Hash
            & "$PackageDir\Repair.ps1" -HomeDir $incomplete -NonInteractive -NoTask -NoShortcuts -NoStart
            $installs.Add($incomplete)
            $repaired=Read-Install $incomplete;$recovery=Get-Content (Join-Path $incomplete 'Config\recovery.json') -Raw|ConvertFrom-Json
            if($repaired.installer_version -ne '1.0.2' -or (Read-AgentEnv $incomplete).JARVIS_CODEX_ENABLED -ne '0'){throw 'Ripresa non completata.'}
            if((Get-FileHash (Join-Path $recovery.backup 'Config\secrets.env')).Hash -ne $originalSecret){throw 'Backup precedente alterato.'};Pass 'resume_real_101_failure_with_intact_backup'
            $rollbackHome=Join-Path $TestRoot 'Rollback ripresa'
            Expect-Refusal { & $legacyInstall.FullName -InstallDir $rollbackHome -Port 8772 -NonInteractive -NoTask -NoShortcuts -NoStart } 'second_old_101_failure_fixture'
            $beforeRollback=(Get-FileHash (Join-Path $rollbackHome 'Config\secrets.env')).Hash
            $badRecovery=Join-Path $TestRoot 'bad-recovery-package';New-Item -ItemType Directory $badRecovery|Out-Null;Copy-Item "$PackageDir\*" $badRecovery
            [IO.File]::AppendAllText((Join-Path $badRecovery 'jarvis-mcp.exe'),'corrupt')
            Expect-Refusal { & (Join-Path $badRecovery 'Repair.ps1') -HomeDir $rollbackHome -NonInteractive -NoTask -NoShortcuts -NoStart } 'failed_recovery_rolls_back'
            if((Get-FileHash (Join-Path $rollbackHome 'Config\secrets.env')).Hash -ne $beforeRollback -or -not (Test-Path (Join-Path $rollbackHome 'Config\install-failed.txt'))){throw 'Originale non ripristinato.'};Pass 'rollback_preserves_original_incomplete_install'
        }
    } finally {
        Remove-Item Function:\Get-AppxPackage -ErrorAction SilentlyContinue
        if($priorAppxFunction){Set-Item Function:\Get-AppxPackage -Value $priorAppxFunction.ScriptBlock}
        Remove-Variable JarvisTestCodexPackageRoot -Scope Global -ErrorAction SilentlyContinue
    }
    $default=Read-Install $a;$custom=Read-Install $b
    if ($default.device_name -ne 'Desktop2' -or $default.plugin_name -ne 'Jarvis-Desktop2' -or $default.task_name -notlike 'Jarvis-Desktop2-*') { throw 'Nome predefinito non corretto.' };Pass 'default_name_Desktop2'
    if ($custom.device_name -ne 'Officina CNC' -or $custom.plugin_name -ne 'Jarvis-Officina CNC' -or $custom.task_name -notlike 'Jarvis-Officina-CNC-*') { throw 'Nome personalizzato non propagato.' };Pass 'custom_name_metadata_plugin_and_task'
    if ((Get-DeviceNames 'Officina CNC').default_home -ne 'C:\Jarvis-Officina CNC') { throw 'Cartella proposta non segue il nome.' };Pass 'custom_name_default_folder'
    Expect-Refusal { Get-DeviceNames '../Desktop1' } 'refuse_unsafe_device_name'
    if ((Get-LocalToken $a) -ceq (Get-LocalToken $b)) { throw 'Credenziali duplicate.' };Pass 'random_separate_credentials'
    if ((Test-Path (Join-Path $a 'Config\codex-cost-proof.json')) -or (Test-Path (Join-Path $b 'Config\codex-cost-proof.json'))) { throw 'Prova costi copiata.' };Pass 'no_cost_proof_copied'
    $hash=(Get-FileHash (Join-Path $a 'Agent\jarvis-mcp.exe')).Hash
    Expect-Refusal { & "$PackageDir\Install.ps1" -InstallDir $a -Port 8768 -NonInteractive -NoTask -NoShortcuts -NoStart } 'refuse_existing_installation'
    if ((Get-FileHash (Join-Path $a 'Agent\jarvis-mcp.exe')).Hash -ne $hash) { throw 'Installazione precedente modificata.' }
    Expect-Refusal { & "$PackageDir\Install.ps1" -InstallDir (Join-Path $TestRoot 'bad-root') -ProjectsRoot $TestRoot -Port 8768 -NonInteractive -NoTask -NoShortcuts -NoStart } 'refuse_project_containing_private_install'
    Expect-Refusal { Assert-CloudflareInputs 'https://wrong.example' ('a'*64) 'https://desktop2.example.com' ([guid]::NewGuid().ToString()) } 'refuse_invalid_cloudflare_domain'
    Expect-Refusal { Assert-CloudflareInputs 'https://test.cloudflareaccess.com' 'invalid' 'https://desktop2.example.com' ([guid]::NewGuid().ToString()) } 'refuse_invalid_access_aud'
    $s=Read-Install $a
    $task=Get-ScheduledTask -TaskName $s.task_name
    if ($task.Principal.RunLevel -ne 'Highest' -or $task.Principal.LogonType -ne 'Interactive' -or $task.Settings.ExecutionTimeLimit -ne 'PT0S') { throw 'Task non conforme.' };Pass 'interactive_elevated_logon_task_without_time_limit'
    Start-ScheduledTask -TaskName $s.task_name
    $ia=Wait-Agent $a
    $runner=Start-Runner $b;$ib=Wait-Agent $b
    if ($ia.machine_id -eq $ib.machine_id -or $ia.machine_id -eq '1b927a030fa3b7dfa15c40cfccb4ff60' -or $ib.machine_id -eq '1b927a030fa3b7dfa15c40cfccb4ff60') { throw 'Identita clonate.' };Pass 'isolated_identity_and_real_mcp'
    & (Join-Path $a 'Scripts\Check.ps1') -HomeDir $a;Pass 'catalog_queue_check'
    Expect-Refusal { & "$PackageDir\Install.ps1" -InstallDir (Join-Path $TestRoot 'occupied') -Port 8768 -NonInteractive -NoTask -NoShortcuts -NoStart } 'refuse_occupied_port_without_stopping_owner'
    $blocked=$false
    try { Invoke-WebRequest 'http://127.0.0.1:8768/mcp' -Method Post -ContentType 'application/json' -Body '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' -UseBasicParsing -TimeoutSec 5|Out-Null } catch { if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) { $blocked=$true } }
    if (-not $blocked) { throw 'MCP senza autenticazione non rifiutato.' };Pass 'unauthenticated_mcp_refused'
    & (Join-Path $b 'Scripts\Stop.ps1') -HomeDir $b -Force
    if (-not $runner.WaitForExit(10000)) { throw 'Runner non terminato.' }
    $runner2=Start-Runner $b;$ib2=Wait-Agent $b
    if ($ib.machine_id -ne $ib2.machine_id) { throw 'Identita cambiata al riavvio.' };Pass 'identity_persists_on_restart'
    & (Join-Path $a 'Scripts\Disable-Startup.ps1') -HomeDir $a
    if ((Get-ScheduledTask -TaskName $s.task_name).Settings.Enabled) { throw 'Task non disabilitato.' };Pass 'disable_startup_preserves_state_without_stopping_current_work'
    $badPackage=Join-Path $TestRoot 'corrupted-package';New-Item -ItemType Directory $badPackage|Out-Null
    Copy-Item "$PackageDir\*" $badPackage
    [IO.File]::AppendAllText((Join-Path $badPackage 'jarvis-mcp.exe'),'corrupt')
    Expect-Refusal { & (Join-Path $badPackage 'Install.ps1') -InstallDir (Join-Path $TestRoot 'must-not-exist') -Port 8770 -NonInteractive -NoTask -NoShortcuts -NoStart } 'corrupted_payload_rejected_before_install'
    if (Test-Path (Join-Path $TestRoot 'must-not-exist')) { throw 'Mutazione precedente al controllo checksum.' }
    Pass 'COMPLETE'
} catch { $report.Add('ERROR '+$_.Exception.Message);throw }
finally {
    foreach ($dir in $installs) {
        try { & (Join-Path $dir 'Scripts\Stop.ps1') -HomeDir $dir -Force } catch { $report.Add('CLEANUP_ERROR '+$_.Exception.Message) }
        $s=Read-Install $dir
        if ($s.task_registered) { Unregister-ScheduledTask -TaskName $s.task_name -Confirm:$false -ErrorAction SilentlyContinue }
    }
    foreach ($r in $runners) { $null=$r.WaitForExit(10000) }
    $report|Set-Content (Join-Path $TestRoot 'RESULTS.txt') -Encoding UTF8
    $report|Set-Content 'D:\Jarvis\Logs\diagnostics\installer-061-test.log' -Encoding UTF8
}
