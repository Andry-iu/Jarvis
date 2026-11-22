param([string]$HomeDir=(Split-Path $PSScriptRoot -Parent),[string]$EnrollmentFile,[string]$DeviceId,[string]$DeviceName)
. "$PSScriptRoot\Common.ps1"
$s=Read-Install $HomeDir
if(-not $EnrollmentFile){$EnrollmentFile=Read-Host 'Percorso del file privato peer-enrollment.json del computer da abbinare'}
$item=Get-Item -LiteralPath $EnrollmentFile
if($item.Length -gt 16384 -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'File di abbinamento non valido'}
$enrollment=[IO.File]::ReadAllText($item.FullName)|ConvertFrom-Json
if(-not $DeviceId){$DeviceId=$enrollment.machine_id}
if(-not $DeviceName){$DeviceName=$enrollment.name}
$dir=Join-Path $HomeDir 'Config\enrollments';New-Item -ItemType Directory -Force $dir|Out-Null;Protect-Folder $dir
$name=[guid]::NewGuid().ToString('N')+'.json';Copy-Item -LiteralPath $item.FullName -Destination (Join-Path $dir $name)
$info=Invoke-LocalTool $HomeDir 'machine_info';$list=Invoke-LocalTool $HomeDir 'device_list' @{machine_id=$info.machine_id}
try{
 $result=Invoke-LocalTool $HomeDir 'device_pair' @{machine_id=$info.machine_id;device_id=$DeviceId;label=$DeviceName;path=$name;expected_sha256=$list.configuration_sha256;request_id=[guid]::NewGuid().ToString('N')}
 Write-Host ('Abbinamento verificato e salvato: '+$DeviceName)
}finally{Remove-Item -LiteralPath (Join-Path $dir $name) -Force -ErrorAction SilentlyContinue}
