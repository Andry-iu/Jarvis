# Isolated tests: no Jarvis, Codex, Cloudflare network, credentials or Desktop1.
param([string]$PackageDir=$PSScriptRoot)
. "$PackageDir\Common.ps1"
. "$PackageDir\Setup-Settings.ps1"
$passed=0
function Check([bool]$Condition,[string]$Name){if(-not $Condition){throw ('FAIL '+$Name)};$script:passed++;Write-Host ('PASS '+$Name)}
function Refuse([scriptblock]$Action,[string]$Code){
    $caught='';try{&$Action|Out-Null}catch{$caught=$_.Exception.Message}
    Check ($caught -cmatch $Code) $Code
    Check (-not $caught.Contains('fixture-secret')) 'error_contains_no_secret'
}
# Any accidental console prompt fails the test instead of blocking the process.
function Read-Host {throw 'TEST_UNEXPECTED_CONSOLE_PROMPT'}
$v=ConvertFrom-SetupSettingsText @'
# literal data, never shell code
INSTALL_DIR=D:\Jarvis test
DEVICE_NAME=Desktop2
MCP_PORT=8765
SETUP_NO_PROMPTS=true
SETUP_REMOTE_MODE=auto
CLOUDFLARE_API_TOKEN="fixture-secret=abc#$();${env:USERNAME}"
CLOUDFLARE_ACCESS_EMAIL=one@example.com
'@
Check ($v.INSTALL_DIR -ceq 'D:\Jarvis test') 'literal_windows_path'
Check ($v.CLOUDFLARE_API_TOKEN -ceq 'fixture-secret=abc#$();${env:USERNAME}') 'secret_quotes_equals_hash_dollar_are_literal'
$s=@{present=$true;values=$v}
Check (Get-SetupSettingBool $s 'SETUP_NO_PROMPTS') 'true_boolean'
Check (-not (Get-SetupSettingBool $s 'SETUP_NO_TASK')) 'missing_boolean_default'
Check ((Get-SetupSetting $s 'CLOUDFLARE_API_TOKEN') -ceq $v.CLOUDFLARE_API_TOKEN) 'secret_read_without_prompt'
Refuse {ConvertFrom-SetupSettingsText 'BAD=fixture-secret'} '^SETUP_CONFIG_UNKNOWN_KEY_LINE_1$'
Refuse {ConvertFrom-SetupSettingsText "CLOUDFLARE_API_TOKEN=fixture-secret`nCLOUDFLARE_API_TOKEN=other"} '^SETUP_CONFIG_DUPLICATE_KEY_LINE_2$'
Refuse {ConvertFrom-SetupSettingsText 'fixture-secret'} '^SETUP_CONFIG_INVALID_LINE_1$'
Refuse {ConvertFrom-SetupSettingsText 'SETUP_NO_PROMPTS=fixture-secret'} '^SETUP_CONFIG_INVALID_SETUP_NO_PROMPTS$'
Refuse {ConvertFrom-SetupSettingsText 'SETUP_REMOTE_MODE=fixture-secret'} '^SETUP_CONFIG_INVALID_SETUP_REMOTE_MODE$'
Refuse {ConvertFrom-SetupSettingsText 'MCP_PORT=99999'} '^SETUP_CONFIG_INVALID_MCP_PORT$'
Refuse {ConvertFrom-SetupSettingsText 'CLOUDFLARE_API_TOKEN="fixture-secret'} '^SETUP_CONFIG_QUOTES_LINE_1$'
Refuse {ConvertFrom-SetupSettingsText ('CLOUDFLARE_API_TOKEN=fixture-secret'+[char]0)} '^SETUP_CONFIG_INVALID_SIZE_OR_NUL$'
Refuse {ConvertFrom-SetupSettingsText ('#'+('x'*65536))} '^SETUP_CONFIG_INVALID_SIZE_OR_NUL$'
Refuse {ConvertFrom-SetupSettingsText 'CLOUDFLARE_ACCESS_EMAIL=fixture-secret'} '^SETUP_CONFIG_INVALID_CLOUDFLARE_ACCESS_EMAIL$'
Refuse {ConvertFrom-SetupSettingsText 'CLOUDFLARE_ZONE_ID=fixture-secret'} '^SETUP_CONFIG_INVALID_CLOUDFLARE_ZONE_ID$'
$zones=@([pscustomobject]@{id=('a'*32);name='example.com';account_name='One'},[pscustomobject]@{id=('b'*32);name='other.example';account_name='Two'})
$v.CLOUDFLARE_ZONE_NAME='example.com'
$selectionResult=Resolve-SetupCloudflareInputs $s $zones $null 'Desktop2' -NoPrompts
Check ($selectionResult.zone_id -ceq ('a'*32) -and $selectionResult.label -ceq 'jarvis-desktop2' -and $selectionResult.email -ceq 'one@example.com') 'complete_config_resolves_without_prompts'
$v.Remove('CLOUDFLARE_ZONE_NAME')
Refuse {Resolve-SetupCloudflareInputs $s $zones $null 'Desktop2' -NoPrompts} '^SETUP_CONFIG_REQUIRED_CLOUDFLARE_ZONE_NAME_OR_ID$'
$previous=[pscustomobject]@{desired=[pscustomobject]@{zone_id=('a'*32);zone='example.com';hostname='jarvis-desktop2.example.com';email='one@example.com'}}
$v.CLOUDFLARE_ACCESS_EMAIL='two@example.com'
Refuse {Resolve-SetupCloudflareInputs $s $zones $previous 'Desktop2' -NoPrompts} '^SETUP_CONFIG_CONFLICT_CLOUDFLARE_RESUME$'
$v.CLOUDFLARE_ACCESS_EMAIL='one@example.com'
$selectionResult=Resolve-SetupCloudflareInputs $s $zones $previous 'Desktop2' -NoPrompts
Check ($selectionResult.zone_id -ceq ('a'*32)) 'matching_resume_uses_recorded_identity'
$v.Remove('CLOUDFLARE_ACCESS_EMAIL')
Refuse {Resolve-SetupCloudflareInputs $s @($zones[0]) $null 'Desktop2' -NoPrompts} '^SETUP_CONFIG_REQUIRED_CLOUDFLARE_ACCESS_EMAIL$'
Clear-SetupSettings $s;Check ($s.values.Count -eq 0) 'settings_cleared_after_use'
foreach($pair in @(@('Desktop2','jarvis-desktop2'),@('Desktop3','jarvis-desktop3'),@('Notebook','jarvis-notebook'),@('PC Ufficio','jarvis-pc-ufficio'))){
    $names=Get-CloudflareResourceNames @{values=@{}} $pair[0]
    Check ($names.tunnel -ceq $pair[1] -and $names.policy -ceq $pair[1] -and $names.application -ceq $pair[1]) 'all_names_derived_from_device'
}
$names=Get-CloudflareResourceNames @{values=@{CLOUDFLARE_TUNNEL_NAME='Custom tunnel';CLOUDFLARE_APPLICATION_NAME='Custom app';CLOUDFLARE_POLICY_NAME='Custom policy'}} 'Notebook'
Check ($names.tunnel -ceq 'Custom tunnel' -and $names.application -ceq 'Custom app' -and $names.policy -ceq 'Custom policy') 'explicit_name_overrides'
# Read/ACL tests use an isolated temporary directory, not an existing install.
$temp=Join-Path ([IO.Path]::GetTempPath()) ('jarvis-settings-test-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp|Out-Null
try{
    $file=Join-Path $temp 'setup.local.env'
    $c=Read-SetupSettings $file;Check (-not $c.present) 'optional_missing_file'
    Refuse {Read-SetupSettings $file -Required} '^SETUP_CONFIG_FILE_MISSING$'
    Write-Utf8 $file "INSTALL_DIR=C:\JarvisTest`nCLOUDFLARE_API_TOKEN=fixture-secret"
    $hash=(Get-FileHash $file).Hash;$c=Read-SetupSettings $file
    Check ($c.present -and $c.values.CLOUDFLARE_API_TOKEN -ceq 'fixture-secret') 'file_read_without_echo'
    Check ((Get-FileHash $file).Hash -ceq $hash) 'reader_does_not_change_file_contents'
    $acl=Get-Acl -LiteralPath $file
    Check $acl.AreAccessRulesProtected 'secret_file_has_no_inherited_acl'
    $allowed=@([Security.Principal.WindowsIdentity]::GetCurrent().User.Value,'S-1-5-18')
    foreach($rule in $acl.Access){Check ($rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -in $allowed) 'acl_only_current_user_and_system'}
    [IO.File]::WriteAllBytes($file,[byte[]]@(0xff,0xfe,0xff))
    Refuse {Read-SetupSettings $file} '^SETUP_CONFIG_UTF8_REQUIRED$'
    Clear-SetupSettings $c
}finally{Remove-Item -LiteralPath $temp -Recurse -Force}
Write-Host ('PASS COMPLETE: '+$passed+' assertions; no external calls')
