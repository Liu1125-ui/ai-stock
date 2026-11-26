#requires -Version 7.0
param(
    [ValidateSet('Submit','List','Get','Retry','Cancel')][string]$Action='List',
    [string[]]$InstrumentIds=@('SH600030','SH600276','SZ000002'),
    [string]$TargetDate=(Get-Date -Format 'yyyy-MM-dd'),
    [string]$TaskId,
    [string]$KeyPrefix=('manual-'+[guid]::NewGuid().ToString('N'))
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$secretLine=Get-Content (Join-Path $root '.env') -Encoding UTF8 | Where-Object {$_ -match '^SERVICE_TOKEN='} | Select-Object -First 1
if(-not $secretLine){throw 'Local service credential missing'}
$credential=$secretLine.Substring('SERVICE_TOKEN='.Length).Trim('"')
if([string]::IsNullOrWhiteSpace($credential)){throw 'Local service credential empty'}
$base='http://127.0.0.1:18084/internal/v1/tasks'
$headers=@{'X-Service-Token'=$credential}
function Call-Task([string]$method,[string]$url,[object]$body=$null){
    $options=@{Uri=$url;Method=$method;Headers=$headers;TimeoutSec=10}
    if($null -ne $body){$options.ContentType='application/json';$options.Body=[Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Compress))}
    try {$response=Invoke-RestMethod @options} catch {throw 'Task request failed; check service health, credential, parameters and current state. Credential not printed.'}
    if($response.code -ne 'OK'){throw "Task request rejected: $($response.code)"}
    return $response.data
}
try{
    switch($Action){
        'Submit'{
            if($TargetDate -notmatch '^\d{4}-\d{2}-\d{2}$'){throw 'TargetDate must use yyyy-MM-dd'}
            foreach($id in $InstrumentIds){
                if($id -notmatch '^(SH|SZ)\d{6}$'){throw 'InstrumentId must use SH/SZ plus 6 digits'}
                Call-Task 'Post' $base @{instrumentId=$id;targetDate=$TargetDate;key="$KeyPrefix-$id"} | ConvertTo-Json -Depth 6
            }
        }
        'List'{Call-Task 'Get' $base | ConvertTo-Json -Depth 6}
        default{
            if($TaskId -notmatch '^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$'){throw 'Valid TaskId required'}
            $suffix=if($Action -eq 'Get'){''}else{'/'+$Action.ToLowerInvariant()}
            $method=if($Action -eq 'Get'){'Get'}else{'Post'}
            Call-Task $method "$base/$TaskId$suffix" | ConvertTo-Json -Depth 8
        }
    }
} finally {$credential=$null;$headers.Clear();$secretLine=$null}
