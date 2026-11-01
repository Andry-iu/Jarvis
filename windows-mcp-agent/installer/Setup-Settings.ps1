# Literal UTF-8 KEY=value data, never PowerShell/env expansion or dot-sourcing.
function ConvertFrom-SetupSettingsText([string]$Text) {
    if($Text.Length -gt 65536 -or $Text.IndexOf([char]0) -ge 0){throw 'SETUP_CONFIG_INVALID_SIZE_OR_NUL'}
    $Text=$Text.TrimStart([char]0xFEFF)
    $allowed=@('INSTALL_DIR','DEVICE_NAME','PROJECTS_ROOT','MCP_PORT','CODEX_EXECUTABLE','SETUP_NO_PROMPTS','SETUP_ALLOW_BROWSER_LOGIN','SETUP_REMOTE_MODE','SETUP_NO_TASK','SETUP_NO_SHORTCUTS','CLOUDFLARE_API_TOKEN','CLOUDFLARE_ZONE_ID','CLOUDFLARE_ZONE_NAME','CLOUDFLARE_HOST_LABEL','CLOUDFLARE_ACCESS_EMAIL','CLOUDFLARE_HOSTNAME','CLOUDFLARE_TEAM_DOMAIN','CLOUDFLARE_APP_AUD','CLOUDFLARE_TUNNEL_ID','CLOUDFLARE_TUNNEL_TOKEN')
    $values=@{};$lineNumber=0
    foreach($line in ($Text -split "`r`n|`n|`r")){
        $lineNumber++;$trimmed=$line.Trim()
        if(-not $trimmed -or $trimmed.StartsWith('#')){continue}
        $separator=$trimmed.IndexOf('=')
        if($separator -lt 1){throw ('SETUP_CONFIG_INVALID_LINE_'+$lineNumber)}
        $key=$trimmed.Substring(0,$separator).Trim()
        if($key -cnotmatch '^[A-Z][A-Z0-9_]*$' -or $key -notin $allowed){throw ('SETUP_CONFIG_UNKNOWN_KEY_LINE_'+$lineNumber)}
        if($values.ContainsKey($key)){throw ('SETUP_CONFIG_DUPLICATE_KEY_LINE_'+$lineNumber)}
        $value=$trimmed.Substring($separator+1).Trim()
        # Matching outer quotes optional; backslashes, $, # and = stay literal.
        if($value.StartsWith('"') -or $value.StartsWith("'")){
            if($value.Length -lt 2 -or $value[$value.Length-1] -ne $value[0]){throw ('SETUP_CONFIG_QUOTES_LINE_'+$lineNumber)}
            $value=$value.Substring(1,$value.Length-2)
        }
        $values[$key]=$value
    }
    foreach($key in @('SETUP_NO_PROMPTS','SETUP_ALLOW_BROWSER_LOGIN','SETUP_NO_TASK','SETUP_NO_SHORTCUTS')){
        if($values.ContainsKey($key) -and $values[$key] -cnotmatch '^(true|false)$'){throw ('SETUP_CONFIG_INVALID_'+$key)}
    }
    if($values.ContainsKey('SETUP_REMOTE_MODE') -and $values.SETUP_REMOTE_MODE -cnotmatch '^(auto|manual|skip)$'){throw 'SETUP_CONFIG_INVALID_SETUP_REMOTE_MODE'}
    if($values.ContainsKey('MCP_PORT') -and ($values.MCP_PORT -notmatch '^[0-9]{4,5}$' -or [int]$values.MCP_PORT -lt 1024 -or [int]$values.MCP_PORT -gt 65535)){throw 'SETUP_CONFIG_INVALID_MCP_PORT'}
    foreach($key in @('INSTALL_DIR','PROJECTS_ROOT','CODEX_EXECUTABLE')){
        if($values.ContainsKey($key) -and $values[$key]){try{$null=Get-FullPath $values[$key]}catch{throw ('SETUP_CONFIG_INVALID_'+$key)}}
    }
    if($values.ContainsKey('DEVICE_NAME') -and $values.DEVICE_NAME){try{$null=Get-DeviceNames $values.DEVICE_NAME}catch{throw 'SETUP_CONFIG_INVALID_DEVICE_NAME'}}
    if($values.ContainsKey('CLOUDFLARE_ZONE_ID') -and $values.CLOUDFLARE_ZONE_ID -and $values.CLOUDFLARE_ZONE_ID -notmatch '^[a-fA-F0-9]{32}$'){throw 'SETUP_CONFIG_INVALID_CLOUDFLARE_ZONE_ID'}
    if($values.ContainsKey('CLOUDFLARE_ZONE_NAME') -and $values.CLOUDFLARE_ZONE_NAME -and $values.CLOUDFLARE_ZONE_NAME -cnotmatch '^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,63}$'){throw 'SETUP_CONFIG_INVALID_CLOUDFLARE_ZONE_NAME'}
    if($values.ContainsKey('CLOUDFLARE_HOST_LABEL') -and $values.CLOUDFLARE_HOST_LABEL -and $values.CLOUDFLARE_HOST_LABEL -cnotmatch '^[a-z0-9][a-z0-9-]{0,61}[a-z0-9]$'){throw 'SETUP_CONFIG_INVALID_CLOUDFLARE_HOST_LABEL'}
    if($values.ContainsKey('CLOUDFLARE_ACCESS_EMAIL') -and $values.CLOUDFLARE_ACCESS_EMAIL -and $values.CLOUDFLARE_ACCESS_EMAIL -notmatch '^[-A-Za-z0-9._+]+@(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,63}$'){throw 'SETUP_CONFIG_INVALID_CLOUDFLARE_ACCESS_EMAIL'}
    return $values
}
function Protect-SetupSettingsFile([string]$Path){
    try{
        $acl=New-Object Security.AccessControl.FileSecurity
        $acl.SetAccessRuleProtection($true,$false)
        foreach($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User.Value,'S-1-5-18')){
            $identity=New-Object Security.Principal.SecurityIdentifier($sid)
            $rule=New-Object Security.AccessControl.FileSystemAccessRule($identity,[Security.AccessControl.FileSystemRights]::FullControl,[Security.AccessControl.AccessControlType]::Allow)
            $acl.AddAccessRule($rule)
        }
        Set-Acl -LiteralPath $Path -AclObject $acl
    }catch{throw 'SETUP_CONFIG_ACL_FAILED'}
}
function Read-SetupSettings([string]$Path,[switch]$Required){
    $config=@{present=$false;values=@{}}
    if(-not (Test-Path -LiteralPath $Path -PathType Leaf)){
        if($Required){throw 'SETUP_CONFIG_FILE_MISSING'}
        return $config
    }
    try{$Path=Get-FullPath $Path}catch{throw 'SETUP_CONFIG_PATH_INVALID'}
    Protect-SetupSettingsFile $Path
    $stream=$null;$bytes=$null;$text=$null
    try{
        $stream=[IO.File]::Open($Path,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
        if($stream.Length -gt 65536){throw 'SETUP_CONFIG_INVALID_SIZE_OR_NUL'}
        $bytes=New-Object byte[] ([int]$stream.Length)
        $offset=0
        while($offset -lt $bytes.Length){$count=$stream.Read($bytes,$offset,$bytes.Length-$offset);if($count -le 0){throw 'SETUP_CONFIG_READ_FAILED'};$offset+=$count}
        try{$text=(New-Object Text.UTF8Encoding($false,$true)).GetString($bytes)}catch{throw 'SETUP_CONFIG_UTF8_REQUIRED'}
        $config.values=ConvertFrom-SetupSettingsText $text;$config.present=$true
        return $config
    }catch{
        if($_.Exception.Message -cmatch '^SETUP_CONFIG_[A-Z0-9_]+$'){throw $_.Exception.Message}
        throw 'SETUP_CONFIG_READ_FAILED'
    }finally{if($stream){$stream.Dispose()};if($bytes){[Array]::Clear($bytes,0,$bytes.Length)};$text=$null}
}
function Get-SetupSetting($Settings,[string]$Key,[string]$Default=''){
    if($Settings -and $Settings.values.ContainsKey($Key) -and $Settings.values[$Key]){return [string]$Settings.values[$Key]}
    return $Default
}
function Get-SetupSettingBool($Settings,[string]$Key,[bool]$Default=$false){
    if($Settings -and $Settings.values.ContainsKey($Key)){return ($Settings.values[$Key] -ceq 'true')}
    return $Default
}
function Clear-SetupSettings($Settings){if($Settings){$Settings.values.Clear()}}
function Resolve-SetupCloudflareInputs($Settings,$Zones,$Previous,[string]$DeviceName,[switch]$NoPrompts){
    $zoneID=Get-SetupSetting $Settings 'CLOUDFLARE_ZONE_ID'
    $zoneName=Get-SetupSetting $Settings 'CLOUDFLARE_ZONE_NAME'
    $email=Get-SetupSetting $Settings 'CLOUDFLARE_ACCESS_EMAIL'
    $label=Get-SetupSetting $Settings 'CLOUDFLARE_HOST_LABEL'
    if($Previous){
        $d=$Previous.desired
        $previousLabel=$d.hostname.Substring(0,$d.hostname.Length-$d.zone.Length-1)
        if(($zoneID -and $zoneID -ine $d.zone_id) -or ($zoneName -and $zoneName -ine $d.zone) -or ($email -and $email -ine $d.email) -or ($label -and $label -cne $previousLabel)){throw 'SETUP_CONFIG_CONFLICT_CLOUDFLARE_RESUME'}
        $zoneID=$d.zone_id;$email=$d.email;$label=$previousLabel
    }
    $candidates=@($Zones|Where-Object {(-not $zoneID -or $_.id -ieq $zoneID) -and (-not $zoneName -or $_.name -ieq $zoneName)})
    if($candidates.Count -eq 0){throw 'SETUP_CONFIG_CLOUDFLARE_ZONE_NOT_FOUND'}
    if($candidates.Count -gt 1){
        if($NoPrompts){throw 'SETUP_CONFIG_REQUIRED_CLOUDFLARE_ZONE_NAME_OR_ID'}
        for($n=0;$n -lt $candidates.Count;$n++){Write-Host (($n+1).ToString()+') '+$candidates[$n].name+' / '+$candidates[$n].account_name)}
        $selection=Read-Host 'Numero del dominio da usare'
        if($selection -notmatch '^[0-9]+$' -or [int]$selection -lt 1 -or [int]$selection -gt $candidates.Count){throw 'SETUP_CONFIG_INVALID_CLOUDFLARE_ZONE_SELECTION'}
        $zoneID=$candidates[[int]$selection-1].id
    }else{$zoneID=$candidates[0].id}
    if(-not $label){
        $default='jarvis-'+($DeviceName.ToLowerInvariant() -replace '[ _]+','-').Trim('-')
        if(-not $NoPrompts){$label=Read-Host ('Nome pubblico prima del dominio ['+$default+']')}
        if(-not $label){$label=$default}
    }
    if(-not $email){
        if($NoPrompts){throw 'SETUP_CONFIG_REQUIRED_CLOUDFLARE_ACCESS_EMAIL'}
        $email=(Read-Host 'Email esatta autorizzata in Cloudflare Access').Trim()
    }
    return @{zone_id=$zoneID;label=$label;email=$email}
}
