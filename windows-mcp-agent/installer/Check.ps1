param([string]$HomeDir=(Split-Path $PSScriptRoot -Parent))
. "$PSScriptRoot\Common.ps1"
$s=Read-Install $HomeDir
$i=Invoke-LocalTool $HomeDir 'machine_info'
$catalog=Invoke-LocalTool $HomeDir 'project_catalog' @{machine_id=$i.machine_id}
$queue=Invoke-LocalTool $HomeDir 'queue_list' @{machine_id=$i.machine_id;limit=10}
[pscustomobject]@{computer=$i.name;machine_id=$i.machine_id;version=$i.version;projects_root=$catalog.root;queue_healthy=$queue.queue_healthy;queued=$queue.total;cloudflare_configured=$s.remote_configured;remote_chat_verified=$false}|Format-List
Write-Host 'Il controllo locale non certifica OAuth o il collegamento dal plugin ChatGPT.'
