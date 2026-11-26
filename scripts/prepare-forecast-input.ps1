#requires -Version 7.0
param(
 [ValidateNotNullOrEmpty()][ValidatePattern('^(SH|SZ)[0-9]{6}$')][string[]]$InstrumentIds=@('SH600030','SH600276','SZ000002'),
 [ValidateRange(5,90)][int]$DaysAhead=40,
 [ValidatePattern('^[A-Za-z0-9_-]{1,64}$')][string]$KeyPrefix=('forecast-'+[guid]::NewGuid().ToString('N')),
 [switch]$ReuseSaved
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$outputDirectory=Join-Path $projectRoot ('runtime/sdd005a/live/'+$KeyPrefix)
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$secretLine=Get-Content -LiteralPath (Join-Path $projectRoot '.env') -Encoding UTF8 | Where-Object {$_ -match '^SERVICE_TOKEN='} | Select-Object -First 1
if(-not $secretLine){throw 'Local service credential missing'}
$credential=$secretLine.Substring('SERVICE_TOKEN='.Length).Trim('"')
if([string]::IsNullOrWhiteSpace($credential)){throw 'Local service credential empty'}
$headers=@{'X-Service-Token'=$credential}
$market='http://127.0.0.1:18082'
$algorithm='http://127.0.0.1:18090'
function Request([string]$method,[string]$url,[object]$body=$null){
 $options=@{Uri=$url;Method=$method;Headers=$headers;TimeoutSec=40}
 if($null -ne $body){$options.ContentType='application/json';$options.Body=[Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 12 -Compress))}
 try {return Invoke-RestMethod @options} catch {
  $controlled='SERVICE_REQUEST_FAILED'
  try {$errorBody=$_.ErrorDetails.Message | ConvertFrom-Json; if($errorBody.code -match '^[A-Z_]{1,80}$'){$controlled=$errorBody.code}}catch{}
  throw "Forecast input request failed: $controlled. Credential not printed."
 }
}
function ReadApiData([object]$response){if($response.code -ne 'OK'){throw "Input API rejected: $($response.code)"};return $response.data}
function Save([string]$name,[object]$value){$value | ConvertTo-Json -Depth 14 | Set-Content -LiteralPath (Join-Path $outputDirectory $name) -Encoding UTF8}
try {
 foreach($instrumentId in ($InstrumentIds | Select-Object -Unique)){
  $id=$instrumentId.ToUpperInvariant()
  $daily=ReadApiData (Request Get "$market/internal/v1/market/forecast-raw-window?instrumentId=$id")
  if($daily.state -ne 'AVAILABLE' -or $daily.priceBasis -ne 'RAW' -or $daily.provider -ne 'baostock' -or $daily.bars.Count -lt 60){throw "No compatible RAW window for $id"}
  $selected=@($daily.bars | Select-Object -Last 60)
  $operation="$KeyPrefix-$id-$($daily.versionId)"
  $candidatePath=Join-Path $outputDirectory ("candidate-$id.json")
  if($ReuseSaved -and (Test-Path -LiteralPath $candidatePath)){
   $candidate=Get-Content -LiteralPath $candidatePath -Raw -Encoding UTF8 | ConvertFrom-Json
   if($candidate.rawVersionId -ne $daily.versionId -or $candidate.operationId -ne $operation){throw 'Saved candidate is for another RAW version or operation'}
  }else{
   $origin=[datetime]::ParseExact($selected[-1].date,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
   $body=@{operationId=$operation;instrumentId=$id;rawVersionId=$daily.versionId;start=$selected[0].date;end=$selected[-1].date;calendarThrough=$origin.AddDays($DaysAhead).ToString('yyyy-MM-dd')}
   $source=Request Post "$algorithm/internal/v1/market/forecast-collect" $body
   Save "source-$id.json" $source
   if($source.state -ne 'AVAILABLE' -or $null -eq $source.candidate){throw "Source candidate unavailable for $id : $($source.state)"}
   $candidate=$source.candidate
   Save "candidate-$id.json" $candidate
  }
  $publication=ReadApiData (Request Post "$market/internal/v1/market/forecast-inputs" $candidate)
  Save "publication-$id.json" $publication
  $window=ReadApiData (Request Get "$market/internal/v1/market/forecast-window?instrumentId=$id&inputVersionId=$($publication.inputVersionId)")
  if($window.rawVersionId -ne $daily.versionId -or $window.inputHash -ne $publication.inputHash -or $window.bars.Count -ne 60 -or $window.targetDates.Count -ne 5 -or (@($window.targetDates | Select-Object -Unique)).Count -ne 5){throw 'Published window contract mismatch'}
  for($i=0;$i -lt 60;$i++){
   if($window.bars[$i].date -ne $selected[$i].date -or [decimal]$window.bars[$i].close -ne [decimal]$selected[$i].close -or [decimal]$window.bars[$i].sourceAdjustmentRatio -le 0){throw 'Frozen raw window or ratio mismatch'}
  }
  $previous=$window.originDate
  foreach($target in $window.targetDates){if([string]::CompareOrdinal($target,$previous) -le 0){throw 'Target days must follow origin and ascend'};$previous=$target}
  Save "window-$id.json" $window
  $current=ReadApiData (Request Get "$market/internal/v1/market/forecast-raw-window?instrumentId=$id")
  if($current.versionId -ne $daily.versionId){throw 'RAW head changed during preparation; frozen input retained, recheck current input'}
  $latest=ReadApiData (Request Get "$market/internal/v1/market/forecast-window?instrumentId=$id")
  if($latest.inputVersionId -ne $publication.inputVersionId -or $latest.freshness -ne 'FRESH'){throw 'Current forecast window unavailable or different'}
  Save "latest-$id.json" $latest
  [pscustomobject]@{instrumentId=$id;rawVersionId=$window.rawVersionId;inputVersionId=$window.inputVersionId;replayed=$publication.replayed;originDate=$window.originDate;rows=$window.bars.Count;targets=$window.targetDates;freshness=$window.freshness} | ConvertTo-Json -Depth 5
 }
} finally {$credential=$null;$headers.Clear();$secretLine=$null}
