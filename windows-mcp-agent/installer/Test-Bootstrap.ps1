param([string]$PackageDir=$PSScriptRoot,[string]$LegacyZipPath,[switch]$CheckNetwork,[string]$TestRoot=('D:\Jarvis\InstallerTests\bootstrap-'+[guid]::NewGuid().ToString('N')))
. "$PackageDir\Prerequisites.ps1"
$report=New-Object Collections.Generic.List[string];$installs=New-Object Collections.Generic.List[string];$ctx=$null
function Pass([string]$s){$report.Add('PASS '+$s)}
function Refuse([scriptblock]$Action,[string]$Label,[string]$ExpectedPattern=''){$refused=$false;try{&$Action}catch{if($ExpectedPattern -and $_.Exception.Message -notmatch $ExpectedPattern){throw};$refused=$true};if(-not $refused){throw ('Mancato rifiuto: '+$Label)};Pass $Label}
New-Item -ItemType Directory -Path $TestRoot|Out-Null
try{
    Assert-SetupPackage $PackageDir;Pass 'package_manifest_before_dependencies'
    $homeDir=Join-Path $TestRoot 'Jarvis resumable';$ctx=Open-SetupState $homeDir (Join-Path $TestRoot 'Setup')
    Refuse {Open-SetupState $homeDir (Join-Path $TestRoot 'Setup')} 'concurrent_setup_same_root_refused'
    $ctx.state.device_name='Desktop2';Save-SetupState $ctx 'fixture_checkpoint';$stateDir=$ctx.dir;$ctx.lock.Dispose();$ctx=$null
    $ctx=Open-SetupState $homeDir (Join-Path $TestRoot 'Setup');if($ctx.state.stage -ne 'fixture_checkpoint'){throw 'Checkpoint perso.'};Pass 'checkpoint_persists_after_reopen'
    $global:JarvisDownloadCalls=0;$global:JarvisDownloadCorrupt=$false;$global:JarvisDownloadFail=$false
    function global:Invoke-WebRequest {param($Uri,$OutFile,[switch]$UseBasicParsing,$TimeoutSec) $global:JarvisDownloadCalls++;if($global:JarvisDownloadFail){throw 'fixture network failure'};[IO.File]::WriteAllText($OutFile, $(if($global:JarvisDownloadCorrupt){'corrupt'}else{'download-fixture'}),(New-Object Text.UTF8Encoding($false)))}
    $sample=Join-Path $TestRoot 'sample';Write-Utf8 $sample 'download-fixture';$hash=(Get-FileHash $sample).Hash
    $ctx.state.assets|Add-Member -NotePropertyName go -NotePropertyValue ([pscustomobject]@{version='go1.27.0';name='go1.27.0.windows-amd64.zip';url='https://go.dev/dl/go1.27.0.windows-amd64.zip';sha256=$hash}) -Force
    $first=Get-SetupDownload $ctx 'go';$second=Get-SetupDownload $ctx 'go'
    if($first -ne $second -or $global:JarvisDownloadCalls -ne 1){throw 'Cache non riusata.'};Pass 'verified_cache_reused_without_download'
    [IO.File]::WriteAllText($first,'tampered');$global:JarvisDownloadCorrupt=$true
    Refuse {Get-SetupDownload $ctx 'go'} 'bad_download_hash_never_executed'
    $global:JarvisDownloadCorrupt=$false;$global:JarvisDownloadFail=$true
    Refuse {Get-SetupDownload $ctx 'go'} 'interrupted_download_recorded'
    $global:JarvisDownloadFail=$false;$null=Get-SetupDownload $ctx 'go';Pass 'download_resumes_after_network_error'
    $ctx.state.assets.go.url='https://unexpected.example/payload';Refuse {Get-SetupDownload $ctx 'go'} 'journal_download_origin_tampering_refused';$ctx.state.assets.go.url='https://go.dev/dl/go1.27.0.windows-amd64.zip'
    Remove-Item Function:\Invoke-WebRequest
    foreach($v in @('JarvisDownloadCalls','JarvisDownloadCorrupt','JarvisDownloadFail')){Remove-Variable $v -Scope Global}
    # Real Windows executable fixtures exercise extraction and worker receipts.
    $csc=Join-Path $env:SystemRoot 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
    $source=Join-Path $TestRoot 'fixture.cs'
    Write-Utf8 $source @'
using System;using System.IO;using System.Reflection;
class Fixture {static int Main(string[] a){
 string me=Assembly.GetExecutingAssembly().Location;
 if(a.Length>0 && a[0]=="version"){Console.WriteLine("go version go1.27.0 windows/amd64");return 0;}
 if(a.Length>0 && a[0]=="--version"){Console.WriteLine("git version 2.50.0.windows.1");return 0;}
 if(a.Length>1 && a[0]=="tunnel"){Console.WriteLine("--token-file");return 0;}
 string target=null;
 foreach(string x in a){if(x=="-y")continue;if(x.StartsWith("-o")){target=x.Substring(2);continue;}break;}
 if(target==null)target=Path.Combine(Path.GetDirectoryName(me),"PortableGit");
 Directory.CreateDirectory(Path.Combine(target,"cmd"));File.Copy(me,Path.Combine(target,"cmd","git.exe"));return 0;}}
'@
    $fixture=Join-Path $TestRoot 'portable-fixture.exe';& $csc /nologo /target:exe ('/out:'+$fixture) $source|Out-Null;if($LASTEXITCODE -ne 0){throw 'Fixture compilazione fallita.'}
    $legacyWanted=Join-Path $TestRoot 'wrong legacy target'
    $legacyProc=Start-Process $fixture -ArgumentList ('-y -gm2 -InstallPath="'+$legacyWanted+'"') -Wait -PassThru
    if($legacyProc.ExitCode -ne 0 -or (Test-Path (Join-Path $legacyWanted 'cmd\git.exe'))){throw 'Fixture non riproduce falso successo estrazione.'};$legacyProc.Dispose();Pass 'legacy_sfx_flags_exit_zero_without_requested_target'
    $payload=Join-Path $TestRoot 'go-payload';New-Item -ItemType Directory (Join-Path $payload 'go\bin') -Force|Out-Null;Copy-Item $fixture (Join-Path $payload 'go\bin\go.exe')
    $zip=Join-Path $TestRoot 'go-fixture.zip';Compress-Archive -Path (Join-Path $payload 'go') -DestinationPath $zip
    foreach($kind in @('git','go')){
        $asset=if($kind -eq 'git'){$fixture}else{$zip}
        $ctx.state.assets|Add-Member -NotePropertyName $kind -NotePropertyValue ([pscustomobject]@{version='fixture';sha256=(Get-FileHash $asset).Hash}) -Force
        $target=Join-Path $ctx.dir ('Tools\'+$kind+'-fixture')
        Invoke-SetupOperation $ctx $kind $asset $target
        $tool=Find-SetupTool $kind $ctx
        if(-not $tool.StartsWith($target,[StringComparison]::OrdinalIgnoreCase)){throw ('Estrazione o recupero output '+$kind+' non verificato.')}
        Pass ('missing_'+$kind+'_worker_extraction_and_receipt')
        $job=$ctx.state.jobs.$kind.id;$null=Ensure-SetupTool $ctx $kind
        if($ctx.state.jobs.$kind.id -ne $job){throw 'Worker ripetuto nonostante output verificato.'};Pass ($kind+'_resume_reconciles_completed_output_without_reinstall')
    }
    $ctx.state.jobs.PSObject.Properties.Remove('git')
    $ctx.state.assets.git.version='failure-fixture';$ctx.state.assets.git.sha256='0'*64
    $failedTarget=Join-Path $ctx.dir 'Tools\git-failure-fixture'
    Refuse {Invoke-SetupOperation $ctx 'git' $fixture $failedTarget} 'worker_hash_failure_has_failed_receipt_without_execution'
    if(Test-Path $failedTarget){throw 'Eseguito payload non verificato.'}
    $ctx.state.assets.git.sha256=(Get-FileHash $fixture).Hash
    Invoke-SetupOperation $ctx 'git' $fixture $failedTarget
    if(-not (Test-Path (Join-Path $failedTarget 'cmd\git.exe'))){throw 'Ripresa errore noto fallita.'};Pass 'known_failed_worker_can_resume_after_correction'
    $legacyID='b'*32;$cache=Join-Path $ctx.dir 'Downloads';$legacyName='PortableGit-2.56.0.4-64-bit.7z.exe';$legacyAsset=Join-Path $cache $legacyName
    Copy-Item $fixture $legacyAsset
    $ctx.state.assets.git=[pscustomobject]@{version='v2.56.0.windows.4';sha256=(Get-FileHash $legacyAsset).Hash;name=$legacyName}
    $legacyTarget=Join-Path $ctx.dir 'Tools\git-v2.56.0.windows.4'
    $ctx.state.jobs.git=[pscustomobject]@{id=$legacyID;state='dispatched'}
    $request=[pscustomobject]@{format=1;kind='git';asset=$legacyAsset;sha256=$ctx.state.assets.git.sha256;target=$legacyTarget;version=$ctx.state.assets.git.version;state_dir=$ctx.dir;id=$legacyID;user_sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;computer=$env:COMPUTERNAME}
    Write-SetupJson (Join-Path $ctx.dir ($legacyID+'.request.json')) $request
    $legacyReceipt=[pscustomobject]@{id=$legacyID;state='completed';worker=@{pid=2147483647;started_ticks='1'};exit_code=0;finished_at=[DateTime]::UtcNow.ToString('o');error_code=''}
    $legacyReceiptPath=Join-Path $ctx.dir ($legacyID+'.result.json');Write-SetupJson $legacyReceiptPath $legacyReceipt
    $originalReceiptHash=(Get-FileHash $legacyReceiptPath).Hash
    if(-not (Test-LegacyGitReceiptRepair $ctx 'git' $legacyAsset $legacyTarget $legacyReceipt)){throw 'Ripresa nota 1.1.0 non riconosciuta.'}
    $request.user_sid='other';Write-SetupJson (Join-Path $ctx.dir ($legacyID+'.request.json')) $request
    if(Test-LegacyGitReceiptRepair $ctx 'git' $legacyAsset $legacyTarget $legacyReceipt){throw 'Altro utente accettato.'}
    $request.user_sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;Write-SetupJson (Join-Path $ctx.dir ($legacyID+'.request.json')) $request
    $legacyReceipt|Add-Member -NotePropertyName installer_version -NotePropertyValue '1.2.0'
    if(Test-LegacyGitReceiptRepair $ctx 'git' $legacyAsset $legacyTarget $legacyReceipt){throw 'Ricevuta nuova trattata come legacy.'}
    $legacyReceipt.PSObject.Properties.Remove('installer_version')
    Invoke-SetupOperation $ctx 'git' $legacyAsset $legacyTarget
    if(-not (Get-SetupToolProbe 'git' (Join-Path $legacyTarget 'cmd\git.exe')).available -or (Get-FileHash $legacyReceiptPath).Hash -ne $originalReceiptHash){throw 'Ripresa legacy o conservazione ricevuta fallita.'};Pass 'verified_110_git_defect_recovered_without_erasing_original_receipt'
    $noopSource=Join-Path $TestRoot 'noop.cs';Write-Utf8 $noopSource 'class Noop {static int Main(){return 0;}}'
    $noop=Join-Path $TestRoot 'noop.exe';& $csc /nologo /target:exe ('/out:'+$noop) $noopSource|Out-Null
    $ctx.state.jobs.PSObject.Properties.Remove('git');$ctx.state.assets.git=[pscustomobject]@{version='noop';sha256=(Get-FileHash $noop).Hash}
    Refuse {Invoke-SetupOperation $ctx 'git' $noop (Join-Path $ctx.dir 'Tools\git-noop')} 'extractor_zero_without_git_is_failed_worker'
    $failedReceipt=Get-Content (Join-Path $ctx.dir ($ctx.state.jobs.git.id+'.result.json')) -Raw|ConvertFrom-Json
    if($failedReceipt.state -ne 'failed' -or $failedReceipt.error_code -ne 'VERIFY_MISSING_EXECUTABLE'){throw 'Ricevuta falsely completed.'};Pass 'worker_requires_native_version_verification_before_completed'
    $ctx.state.jobs|Add-Member -NotePropertyName codex -NotePropertyValue ([pscustomobject]@{id=('a'*32)}) -Force
    Refuse {Invoke-SetupOperation $ctx 'codex' $fixture ''} 'uncertain_dispatch_not_replayed'
    $receipt=Join-Path $ctx.dir (('a'*32)+'.result.json');Write-SetupJson $receipt @{state='running';worker=(Get-Record (Get-Process -Id $PID))}
    Refuse {Invoke-SetupOperation $ctx 'codex' $fixture ''} 'running_worker_not_duplicated'
    $sid=$ctx.state.user_sid;$ctx.state.user_sid='wrong-user';Save-SetupState $ctx 'binding_fixture';$ctx.lock.Dispose();$ctx=$null
    Refuse {Open-SetupState $homeDir (Join-Path $TestRoot 'Setup')} 'journal_user_binding_refused'
    $obj=Get-Content (Join-Path $stateDir 'progress.json') -Raw|ConvertFrom-Json;$obj.user_sid=$sid;Write-SetupJson (Join-Path $stateDir 'progress.json') $obj
    $realCodex=Find-Codex;if(-not $realCodex){throw 'CLI reale richiesta per integrazione.'}
    & "$PackageDir\Install.ps1" -InstallDir $homeDir -Port 8773 -CodexExecutable $realCodex -NonInteractive -NoTask -NoShortcuts -NoStart;$installs.Add($homeDir)
    $before=(Get-FileHash (Join-Path $homeDir 'Config\secrets.env')).Hash
    Remove-Item (Join-Path $homeDir 'Config\install-complete.json')
    & "$PackageDir\Repair.ps1" -HomeDir $homeDir -CodexExecutable $realCodex -NonInteractive -NoTask -NoShortcuts -NoStart
    $recovery=Get-Content (Join-Path $homeDir 'Config\recovery.json') -Raw|ConvertFrom-Json
    if((Get-FileHash (Join-Path $recovery.backup 'Config\secrets.env')).Hash -ne $before){throw 'Backup alterato.'};Pass 'resume_own_partial_after_env_before_completion_with_intact_backup'
    $early=Join-Path $TestRoot 'Early power loss';New-Item -ItemType Directory -Path $early|Out-Null
    $intent=Get-Content (Join-Path $homeDir 'Jarvis-Install-Intent.json') -Raw|ConvertFrom-Json;$intent.home=$early;$intent.projects_root=Join-Path $early 'Projects';$intent.port=8774
    Write-SetupJson (Join-Path $early 'Jarvis-Install-Intent.json') $intent
    & "$PackageDir\Repair.ps1" -HomeDir $early -CodexExecutable $realCodex -NonInteractive -NoTask -NoShortcuts -NoStart;$installs.Add($early)
    if(-not (Test-Path (Join-Path $early 'Config\install-complete.json'))){throw 'Ripresa prima di Config fallita.'};Pass 'resume_power_loss_before_metadata_or_credentials'
    $late=Join-Path $TestRoot 'Interrupted with task'
    & "$PackageDir\Install.ps1" -InstallDir $late -Port 8776 -CodexExecutable $realCodex -NonInteractive -NoShortcuts -NoStart;$installs.Add($late)
    $lateSettings=Read-Install $late;$priorTaskName=$lateSettings.task_name
    Remove-Item (Join-Path $late 'Config\install-complete.json')
    $broken=Join-Path $TestRoot 'broken-recovery';New-Item -ItemType Directory -Path $broken|Out-Null;Copy-Item "$PackageDir\*" $broken
    [IO.File]::AppendAllText((Join-Path $broken 'jarvis-mcp.exe'),'corrupt')
    Refuse {& (Join-Path $broken 'Repair.ps1') -HomeDir $late -CodexExecutable $realCodex -NonInteractive -NoShortcuts -NoStart} 'partial_task_recovery_failure_rolls_back' 'Ripresa non completata'
    if(-not (Get-ScheduledTask -TaskName $priorTaskName -ErrorAction SilentlyContinue)){throw 'Task precedente non ripristinato.'};Pass 'rollback_restores_original_registered_task'
    & "$PackageDir\Repair.ps1" -HomeDir $late -CodexExecutable $realCodex -NonInteractive -NoShortcuts -NoStart
    $lateSettings=Read-Install $late
    if((Get-ScheduledTask -TaskName $priorTaskName -ErrorAction SilentlyContinue) -or -not (Get-ScheduledTask -TaskName $lateSettings.task_name -ErrorAction SilentlyContinue)){throw 'Task duplicato o mancante dopo recupero.'};Pass 'partial_registered_task_recovered_without_duplicate'
    # Pending Cloudflare values and a synthetic token exercise local transaction only.
    $tunnel=[guid]::NewGuid().ToString();$aud='a'*64
    Write-SetupJson (Join-Path $homeDir 'Config\cloudflare-pending.json') @{hostname='https://desktop2.example.com';team='https://fixture.cloudflareaccess.com';aud=$aud;tunnel_id=$tunnel}
    $token=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes((@{t=$tunnel;fixture='not-a-real-credential'}|ConvertTo-Json -Compress)))
    Write-Utf8 (Join-Path $homeDir 'Config\tunnel-token.txt') $token
    & "$PackageDir\Configure-Cloudflare.ps1" -HomeDir $homeDir -PreparedExecutable $fixture -PreparedSha256 (Get-FileHash $fixture).Hash -PreparedRelease 'fixture'
    if(-not (Read-Install $homeDir).remote_configured){throw 'Configurazione locale non completata.'}
    $s=Read-Install $homeDir;$s.remote_configured=$false;Write-SetupJson (Join-Path $homeDir 'Config\installation.json') $s
    & "$PackageDir\Configure-Cloudflare.ps1" -HomeDir $homeDir -PreparedExecutable $fixture -PreparedSha256 (Get-FileHash $fixture).Hash -PreparedRelease 'fixture'
    & "$PackageDir\Configure-Cloudflare.ps1" -HomeDir $homeDir
    Pass 'cloudflare_pending_resume_reuses_binary_and_secret_without_duplicate_prompts'
    Refuse {& "$PackageDir\Setup.ps1" -InstallDir $early -ExpectedUserSid 'wrong-user' -NonInteractive -SkipRemote -NoTask -NoShortcuts} 'uac_other_user_refused'
    # Full entrypoint twice with real installed tools/login, no downloads or inference.
    & "$PackageDir\Setup.ps1" -InstallDir $early -NonInteractive -SkipRemote -NoTask -NoShortcuts
    $identity=Invoke-LocalTool $early 'machine_info';$secretHash=(Get-FileHash (Join-Path $early 'Config\secrets.env')).Hash
    & "$PackageDir\Setup.ps1" -InstallDir $early -NonInteractive -SkipRemote -NoTask -NoShortcuts
    if((Invoke-LocalTool $early 'machine_info').machine_id -ne $identity.machine_id -or (Get-FileHash (Join-Path $early 'Config\secrets.env')).Hash -ne $secretHash){throw 'Ripresa ha ricreato identita o segreti.'};Pass 'single_entrypoint_repeat_preserves_live_identity_and_credentials'
    if($LegacyZipPath){
        $legacy=Join-Path $TestRoot 'legacy';Expand-Archive -LiteralPath $LegacyZipPath -DestinationPath $legacy
        $old=Get-ChildItem $legacy -Filter Install.ps1 -Recurse|Select-Object -First 1
        $upgrade=Join-Path $TestRoot 'Upgrade complete 101'
        & $old.FullName -InstallDir $upgrade -Port 8775 -NonInteractive -NoTask -NoShortcuts -NoStart;$installs.Add($upgrade)
        $oldToken=(Get-FileHash (Join-Path $upgrade 'Config\secrets.env')).Hash
        $check=Join-Path $upgrade 'Scripts\Check.ps1';$original=[IO.File]::ReadAllText($check);[IO.File]::AppendAllText($check,'# local edit')
        Refuse {Update-SetupScripts $upgrade $PackageDir} 'modified_installed_scripts_not_overwritten'
        Write-Utf8 $check $original
        # Simulate power loss after replacing Start, before replacing the others.
        Write-SetupJson (Join-Path $upgrade 'Config\installer-scripts-pending.json') @{version='1.2.0';fixture=$true}
        Copy-Item (Join-Path $PackageDir 'Start.ps1') (Join-Path $upgrade 'Scripts\Start.ps1') -Force
        Refuse {& (Join-Path $upgrade 'Scripts\Start.ps1') -HomeDir $upgrade} 'partial_script_upgrade_blocks_start'
        & "$PackageDir\Setup.ps1" -InstallDir $upgrade -NonInteractive -SkipRemote -NoTask -NoShortcuts
        if((Test-Path (Join-Path $upgrade 'Config\installer-scripts-pending.json')) -or (Get-FileHash (Join-Path $upgrade 'Scripts\Common.ps1')).Hash -ne (Get-FileHash (Join-Path $PackageDir 'Common.ps1')).Hash -or (Get-FileHash (Join-Path $upgrade 'Config\secrets.env')).Hash -ne $oldToken){throw 'Aggiornamento script non verificato.'};Pass 'upgrade_complete_101_resumes_and_preserves_credentials_with_backup'
    }
    if($CheckNetwork){
        $network=Open-SetupState (Join-Path $TestRoot 'Network-only') (Join-Path $TestRoot 'NetworkState')
        try{$download=Get-SetupDownload $network 'codex';if((Get-FileHash $download).Hash -ine '2d2aacc5270987f1d33eb22969bf1936528a5b09da152fec0eb9d4c97dcc0d8f'){throw 'Installer ufficiale non verificato.'};Pass 'official_pinned_codex_installer_download_hash_without_execution'
            foreach($kind in @('git','go','cloudflared')){$asset=Get-SetupAsset $network $kind;if($asset.sha256 -notmatch '^[a-fA-F0-9]{64}$'){throw 'Digest assente.'};Pass ('official_'+$kind+'_release_metadata_and_digest')}
        }finally{$network.lock.Dispose()}
    }
    Pass 'COMPLETE'
}catch{$report.Add('ERROR '+$_.Exception.Message);$report.Add('LOCATION '+$_.ScriptStackTrace);throw}
finally{
    if($ctx){$ctx.lock.Dispose()}
    if(Test-Path Function:\Invoke-WebRequest){Remove-Item Function:\Invoke-WebRequest -ErrorAction SilentlyContinue}
    foreach($dir in $installs){try{& (Join-Path $dir 'Scripts\Stop.ps1') -HomeDir $dir -Force;$i=Read-Install $dir;if($i.task_registered){Unregister-ScheduledTask -TaskName $i.task_name -Confirm:$false}}catch{$report.Add('CLEANUP_ERROR '+$_.Exception.Message)}}
    $report|Set-Content (Join-Path $TestRoot 'RESULTS.txt') -Encoding UTF8
    $report|Set-Content 'D:\Jarvis\Logs\diagnostics\bootstrap-110-test.log' -Encoding UTF8
}
