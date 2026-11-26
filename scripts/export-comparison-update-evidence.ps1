#requires -Version 7.0
param([Parameter(Mandatory)][string[]]$TaskIds,[ValidatePattern('^[A-Za-z0-9_-]{1,48}$')][string]$RunName='acceptance')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if($TaskIds.Count -ne 3 -or @($TaskIds | Sort-Object -Unique).Count -ne 3 -or @($TaskIds | Where-Object {$_ -notmatch '^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$'}).Count){throw 'Three unique UUID task IDs required'}
$output=Join-Path $root "runtime/sdd007d/live/$RunName"
$token=$null;$headers=$null
try {
 $config=[IO.File]::ReadAllText((Join-Path $root '.env'))
 $match=[regex]::Match($config,'(?m)^SERVICE_TOKEN=(.+)$')
 if(-not $match.Success){throw 'Internal service configuration unavailable'}
 $token=$match.Groups[1].Value.Trim().Trim([char]34).Trim([char]39);$config=$null
 if([string]::IsNullOrWhiteSpace($token)){throw 'Internal service configuration unavailable'}
 $headers=@{'X-Service-Token'=$token}
 $seen=[Collections.Generic.HashSet[string]]::new()
 $results=@();$summary=@()
 foreach($id in $TaskIds){
  try {
   $r=Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:18084/internal/v1/tasks/$id" -Headers $headers -TimeoutSec 5 -MaximumRedirection 0
   if($r.code -ne 'OK' -or $r.data.task.taskType -ne 'COMPARISON_UPDATE' -or $r.data.task.state -ne 'SUCCEEDED' -or $r.data.task.versionId -notmatch '^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$'){throw 'Successful comparison update task required'}
   $task=$r.data.task;$version=$task.versionId
   $r=Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:18082/internal/v1/market/comparison-evidence/$version" -Headers $headers -TimeoutSec 5 -MaximumRedirection 0
   $e=$r.data
   if($r.code -ne 'OK' -or $e.evidenceId -ne $version -or $e.instrumentId -ne $task.instrumentId -or $e.to -ne $task.targetDate -or $e.provider -ne 'baostock' -or $e.basis -ne 'BACKWARD' -or $e.contentHash -notmatch '^[a-f0-9]{64}$' -or $e.rawContentHash -notmatch '^[a-f0-9]{64}$' -or $e.instrumentId -notin @('SH600030','SH600276','SZ000002') -or -not $seen.Add($e.instrumentId)){throw 'Evidence identity or source mismatch'}
   $results+=,$e
   $summary+=@{taskId=$id;instrumentId=$task.instrumentId;evidenceId=$version;targetDate=$task.targetDate;state=$task.state}
  }catch{throw 'Read-only evidence export failed; inspect task and service status without exposing credentials'}
 }
 [IO.Directory]::CreateDirectory($output) | Out-Null
 foreach($e in $results){[IO.File]::WriteAllText((Join-Path $output ("evidence-"+$e.instrumentId+".json")),($e | ConvertTo-Json -Depth 100),[Text.UTF8Encoding]::new($false))}
 [IO.File]::WriteAllText((Join-Path $output 'tasks.json'),($summary | ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))
 Write-Output "Saved read-only evidence exports to $output; no collection or model calls."
}finally{$token=$null;$headers=$null;$config=$null}
