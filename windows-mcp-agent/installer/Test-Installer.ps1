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
    $realCodex=Find-Codex
    if(-not $realCodex){throw 'CLI compatibile necessaria per il collaudo.'}
    $expectedCodexVersion=(& $realCodex --version|Out-String).Trim();if($LASTEXITCODE -ne 0){throw 'Versione CLI non verificabile.'}
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
        if(Find-Codex $deniedExe){throw 'Codex negato abilitato.'};Pass 'discovery_access_denied_is_unavailable'
        $d=Join-Path $TestRoot 'Codex non accessibile'
        Expect-Refusal { & "$PackageDir\Install.ps1" -InstallDir $d -CodexExecutable $deniedExe -Port 8770 -NonInteractive -NoTask -NoShortcuts -NoStart } 'missing_codex_refuses_installation'
        if(Test-Path $d){throw 'Directory creata senza Codex.'};Pass 'no_partial_install_without_codex'
        $priorGetCommand=Get-Item Function:\Get-Command -ErrorAction SilentlyContinue
        $global:JarvisNativeCodexFixture=$realCodex
        function global:Get-Command { param($Name,$CommandType,[switch]$All,$ErrorAction) if($Name -eq 'codex.exe'){[pscustomobject]@{Source=$global:JarvisNativeCodexFixture}}else{Microsoft.PowerShell.Core\Get-Command $Name} }
        try { if((Find-Codex) -ine $realCodex){throw 'CLI nativa non preferita al pacchetto negato.'};Pass 'native_cli_preferred_over_denied_app' }
        finally { Remove-Item Function:\Get-Command;if($priorGetCommand){Set-Item Function:\Get-Command -Value $priorGetCommand.ScriptBlock};Remove-Variable JarvisNativeCodexFixture -Scope Global }
        if($LegacyZipPath){
            $legacy=Join-Path $TestRoot 'legacy-package';Expand-Archive -LiteralPath $LegacyZipPath -DestinationPath $legacy
            $legacyInstall=Get-ChildItem $legacy -Filter Install.ps1 -Recurse|Select-Object -First 1
            if(-not $legacyInstall){throw 'Pacchetto 1.0.1 di regressione mancante.'}
            $incomplete=Join-Path $TestRoot 'Installazione incompleta'
            Expect-Refusal { & $legacyInstall.FullName -InstallDir $incomplete -Port 8771 -NonInteractive -NoTask -NoShortcuts -NoStart } 'old_101_reproduces_install_failure'
            $originalSecret=(Get-FileHash (Join-Path $incomplete 'Config\secrets.env')).Hash
            & "$PackageDir\Repair.ps1" -HomeDir $incomplete -CodexExecutable $realCodex -NonInteractive -NoTask -NoShortcuts -NoStart
            $installs.Add($incomplete)
            & "$PackageDir\Connect-Codex.ps1" -HomeDir $incomplete -CodexExecutable $realCodex -NonInteractive
            if(-not (Get-Content (Join-Path $incomplete 'Config\codex-connection.json') -Raw|ConvertFrom-Json).codex_connected){throw 'Avvio e connessione dopo ripresa falliti.'};Pass 'cold_start_and_codex_connection_after_recovery'
            $repaired=Read-Install $incomplete;$recovery=Get-Content (Join-Path $incomplete 'Config\recovery.json') -Raw|ConvertFrom-Json
            if($repaired.installer_version -ne '1.3.0' -or (Read-AgentEnv $incomplete).JARVIS_CODEX_ENABLED -ne '1'){throw 'Ripresa non completata.'}
            if((Get-FileHash (Join-Path $recovery.backup 'Config\secrets.env')).Hash -ne $originalSecret){throw 'Backup precedente alterato.'};Pass 'resume_real_101_failure_with_intact_backup'
            $rollbackHome=Join-Path $TestRoot 'Rollback ripresa'
            Expect-Refusal { & $legacyInstall.FullName -InstallDir $rollbackHome -Port 8772 -NonInteractive -NoTask -NoShortcuts -NoStart } 'second_old_101_failure_fixture'
            $beforeRollback=(Get-FileHash (Join-Path $rollbackHome 'Config\secrets.env')).Hash
            $badRecovery=Join-Path $TestRoot 'bad-recovery-package';New-Item -ItemType Directory $badRecovery|Out-Null;Copy-Item "$PackageDir\*" $badRecovery
            [IO.File]::AppendAllText((Join-Path $badRecovery 'jarvis-mcp.exe'),'corrupt')
            Expect-Refusal { & (Join-Path $badRecovery 'Repair.ps1') -HomeDir $rollbackHome -CodexExecutable $realCodex -NonInteractive -NoTask -NoShortcuts -NoStart } 'failed_recovery_rolls_back'
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
    $packageMetadata=Get-Content (Join-Path $PackageDir 'package.json') -Raw|ConvertFrom-Json
    if($ia.version -ne '0.7.0' -or $ib.version -ne '0.7.0'){throw 'Versione agente nuova installazione non allineata.'};Pass 'fresh_agent_070_on_both_installations'
    $required=@('maintenance_status','known_folder','process_start','window_list','window_inspect','window_focus','window_close','ui_action','ui_keys','ui_file_dialog')
    foreach($info in @($ia,$ib)){foreach($name in $required){if($info.capabilities -notcontains $name){throw ('Strumento 0.7 mancante: '+$name)}}};Pass 'all_new_agent_capabilities_present'
    foreach($dir in @($a,$b)){
        if(-not (Test-Path (Join-Path $dir 'Agent\jarvis-mcp.exe')) -or (Test-Path (Join-Path $dir 'jarvis-mcp.exe'))){throw 'Layout agente non conforme.'}
        if((Get-FileHash (Join-Path $dir 'Scripts\update-agent.ps1')).Hash -ine (Get-FileHash (Join-Path $PackageDir 'update-agent.ps1')).Hash){throw 'Updater comune non installato.'}
    };Pass 'agent_subdirectory_and_same_common_updater_installed'
    $maintenance=Invoke-LocalTool $a 'maintenance_status' @{machine_id=$ia.machine_id}
    if($maintenance.log_retention_days -ne 30 -or $maintenance.batch_retention_days -ne 30 -or $maintenance.interval_hours -ne 24 -or $maintenance.error){throw 'Manutenzione predefinita non allineata.'};Pass 'new_install_retention_30_days_24_hours'
    & (Join-Path $a 'Scripts\Check.ps1') -HomeDir $a;Pass 'catalog_queue_check'
    $beforePid=(Get-Content (Join-Path $a 'Config\running.json') -Raw|ConvertFrom-Json).agent.pid
    & "$PackageDir\Connect-Codex.ps1" -HomeDir $a -CodexExecutable $realCodex -NonInteractive
    $connection=Get-Content (Join-Path $a 'Config\codex-connection.json') -Raw|ConvertFrom-Json
    if(-not $connection.codex_connected -or $connection.codex_version -ne $expectedCodexVersion -or $connection.account_type -ne 'chatgpt' -or -not $connection.checked_at){throw 'Connessione/account non verificati.'}
    if((Get-Content (Join-Path $a 'Config\running.json') -Raw|ConvertFrom-Json).agent.pid -ne $beforePid){throw 'Agente attivo riavviato.'};Pass 'readonly_codex_account_usage_without_restart_or_inference'
    $savedEnv=Read-AgentEnv $a;$changed=@{};foreach($key in $savedEnv.Keys){$changed[$key]=$savedEnv[$key]};$changed.JARVIS_CODEX_ENABLED='0';Write-AgentEnv $a $changed
    try { Expect-Refusal { & "$PackageDir\Connect-Codex.ps1" -HomeDir $a -CodexExecutable $realCodex -NonInteractive } 'different_active_configuration_refused_without_stop' }
    finally { Write-AgentEnv $a $savedEnv }
    if((Get-Content (Join-Path $a 'Config\running.json') -Raw|ConvertFrom-Json).agent.pid -ne $beforePid -or -not (Get-Process -Id $beforePid -ErrorAction SilentlyContinue)){throw 'Agente interrotto.'}
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
    & (Join-Path $a 'Scripts\Stop.ps1') -HomeDir $a -Force
    & "$PackageDir\Connect-Codex.ps1" -HomeDir $a -CodexExecutable $realCodex -NonInteractive
    if((Get-ScheduledTask -TaskName $s.task_name).Settings.Enabled){throw 'Task riabilitato implicitamente.'};Pass 'connector_starts_without_reenabling_disabled_startup'
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
    $report|Set-Content 'D:\Jarvis\Logs\diagnostics\installer-130-test.log' -Encoding UTF8
}
