#requires -Version 7.0
param(
 [ValidateCount(1,3)][string[]]$InstrumentIds=@('SH600030','SH600276','SZ000002'),
 [Parameter(Mandatory)][ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string]$From,
 [Parameter(Mandatory)][ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string]$To,
 [ValidatePattern('^[A-Za-z0-9_-]{1,48}$')][string]$KeyPrefix=('comparison-'+[guid]::NewGuid().ToString('N'))
)
$ErrorActionPreference='Stop'
$culture=[Globalization.CultureInfo]::InvariantCulture
$start=[datetime]::ParseExact($From,'yyyy-MM-dd',$culture)
$end=[datetime]::ParseExact($To,'yyyy-MM-dd',$culture)
if($start -gt $end -or $start -lt $end.AddYears(-1)){throw 'Comparison range must be within one calendar year'}
if(@($InstrumentIds | Select-Object -Unique).Count -ne $InstrumentIds.Count){throw 'Duplicate instrument'}
foreach($stockId in $InstrumentIds){if($stockId -notmatch '^(SH|SZ)[0-9]{6}$'){throw 'Invalid instrument'}}
$projectRoot=Split-Path $PSScriptRoot -Parent
$outputDirectory=Join-Path $projectRoot ("runtime/sdd007a/live/$KeyPrefix")
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
. (Join-Path $PSScriptRoot 'comparison-cache.ps1')
$cacheLock=Enter-ComparisonCacheLock $outputDirectory
$headers=@{}
try{
$secretLine=Get-Content -LiteralPath (Join-Path $projectRoot '.env') -Encoding utf8 | Where-Object {$_ -match '^SERVICE_TOKEN='} | Select-Object -First 1
if(-not $secretLine){throw 'Local service credential missing'}
$credential=$secretLine.Substring('SERVICE_TOKEN='.Length).Trim('"')
if([string]::IsNullOrWhiteSpace($credential)){throw 'Local service credential empty'}
$headers=@{'X-Service-Token'=$credential}
$market='http://127.0.0.1:18082'
$algorithm='http://127.0.0.1:18090'
function Request([string]$method,[string]$uri,[object]$body=$null){
 try{
  $args=@{Uri=$uri;Method=$method;Headers=$headers;TimeoutSec=35}
  if($null -ne $body){$args.ContentType='application/json';$args.Body=[Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 20 -Compress))}
  Invoke-RestMethod @args
 }catch{throw 'Comparison HTTP request did not complete; saved candidates retained. Check service status and controlled error records.'}
}
function ReadData($response){
 if($response.code -ne 'OK' -or $null -eq $response.data){throw 'Comparison API returned unavailable result'}
 $response.data
}
function Save($name,$value){
 Save-ComparisonCacheJson $outputDirectory $name $value
}
 foreach($stockId in $InstrumentIds){
  $candidatePath=Join-Path $outputDirectory "candidate-$stockId.json"
  $operation="$KeyPrefix-$stockId"
  if(Test-Path -LiteralPath $candidatePath){
   $candidate=Get-Content -LiteralPath $candidatePath -Raw -Encoding utf8 | ConvertFrom-Json
   if($candidate.instrumentId -ne $stockId -or $candidate.operationId -ne $operation -or $candidate.from -ne $From -or $candidate.to -ne $To){
    throw 'Saved candidate belongs to another request'
   }
  }else{
   $raw=ReadData (Request Get "$market/internal/v1/market/comparison-raw-window?instrumentId=$stockId&from=$From&to=$To")
   Save "raw-$stockId.json" $raw
   $body=@{operationId=$operation;instrumentId=$stockId;rawVersionId=$raw.rawVersionId;start=$From;end=$To}
   Begin-ComparisonSourceAttempt $outputDirectory $stockId
   $source=Request Post "$algorithm/internal/v1/market/comparison-collect" $body
   Save "source-$stockId.json" $source
   if($source.state -ne 'AVAILABLE' -or $null -eq $source.candidate){throw "Comparison source unavailable: $($source.state); no automatic retry"}
   $candidate=$source.candidate
   Save "candidate-$stockId.json" $candidate
  }
  $publication=ReadData (Request Post "$market/internal/v1/market/comparison-evidence" $candidate)
  Save "publication-$stockId.json" $publication
  $evidence=ReadData (Request Get "$market/internal/v1/market/comparison-evidence/$($publication.evidenceId)")
  if($evidence.rawVersionId -ne $candidate.rawVersionId -or $evidence.contentHash -ne $publication.contentHash -or $evidence.basis -ne 'BACKWARD'){
   throw 'Published comparison evidence mismatch'
  }
  Save "evidence-$stockId.json" $evidence
  [pscustomobject]@{instrumentId=$stockId;evidenceId=$publication.evidenceId;replayed=$publication.replayed;from=$evidence.from;to=$evidence.to;observations=@($evidence.days).Count} | ConvertTo-Json -Compress
 }
}finally{$credential=$null;$headers.Clear();$secretLine=$null;$cacheLock.Dispose()}