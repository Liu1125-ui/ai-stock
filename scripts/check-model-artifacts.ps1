#requires -Version 7.0
param([ValidateNotNullOrEmpty()][ValidatePattern('^(SH|SZ)[0-9]{6}$')][string[]]$InstrumentIds=@('SH600030','SH600276','SZ000002'),[string]$CatalogPath='runtime/model-artifacts/catalog.json',[string]$AlgorithmUrl='http://127.0.0.1:18090',[string]$MarketUrl='http://127.0.0.1:18082')
$ErrorActionPreference='Stop';$projectRoot=Split-Path $PSScriptRoot -Parent
$outputDirectory=Join-Path $projectRoot 'runtime/sdd005b/http';New-Item -ItemType Directory -Force $outputDirectory|Out-Null
$secretLine=Get-Content -LiteralPath (Join-Path $projectRoot '.env') -Encoding UTF8|Where-Object {$_ -match '^SERVICE_TOKEN='}|Select-Object -First 1
if(-not $secretLine){throw 'Local service credential missing'}
$credential=$secretLine.Substring('SERVICE_TOKEN='.Length).Trim('"');if([string]::IsNullOrWhiteSpace($credential)){throw 'Local service credential empty'}
$headers=@{'X-Service-Token'=$credential}
function Request([string]$method,[string]$url,[object]$body=$null){
 $options=@{Method=$method;Uri=$url;Headers=$headers;TimeoutSec=55}
 if($null -ne $body){$options.ContentType='application/json';$options.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 14 -Compress))}
 try{return Invoke-RestMethod @options}catch{throw 'Candidate validation service request failed. Credential not printed.'}
}
try {
 $catalog=Get-Content -LiteralPath (Join-Path $projectRoot $CatalogPath) -Raw -Encoding UTF8|ConvertFrom-Json
 if(!$catalog.artifacts){throw 'Model artifact catalog is empty'}
 $records=@()
 foreach($code in ($InstrumentIds | Select-Object -Unique)){
  $id=$code.ToUpperInvariant()
  $marketResponse=Request Get "$MarketUrl/internal/v1/market/forecast-window?instrumentId=$id"
  if($marketResponse.code -ne 'OK' -or $marketResponse.data.freshness -ne 'FRESH'){throw 'Current frozen window unavailable'}
  $window=$marketResponse.data
  foreach($model in $catalog.artifacts){
   $body=@{artifactId=$model.artifactId;manifestHash=$model.manifestHash;window=$window}
   $first=Request Post "$AlgorithmUrl/internal/v1/model-artifacts/validate" $body
   if($first.state -ne 'AVAILABLE' -or $first.result.validationOnly -ne $true -or $first.result.points.Count -ne 5){throw "Candidate does not pass for $id or is outside its declared stock coverage: $($first.state)"}
   $result=$first.result
   if($result.artifactId -ne $model.artifactId -or $result.manifestHash -ne $model.manifestHash -or $result.checkpointHash -ne $model.checkpointHash -or $result.inputVersionId -ne $window.inputVersionId -or $result.inputHash -ne $window.inputHash -or $result.rawVersionId -ne $window.rawVersionId -or $result.calendarHash -ne $window.calendarHash){throw 'Candidate version echoes differ'}
   for($i=0;$i -lt 5;$i++){if($result.points[$i].step -ne $i+1 -or $result.points[$i].targetDate -ne $window.targetDates[$i] -or [decimal]$result.points[$i].ratio -le 0 -or [decimal]$result.points[$i].referencePrice -le 0){throw 'Candidate five-point contract differs'}}
   $second=Request Post "$AlgorithmUrl/internal/v1/model-artifacts/validate" $body
   if($second.state -ne 'AVAILABLE' -or ($result|ConvertTo-Json -Depth 12 -Compress) -cne ($second.result|ConvertTo-Json -Depth 12 -Compress)){throw 'Repeated candidate result differs'}
   $first|ConvertTo-Json -Depth 14|Set-Content -LiteralPath (Join-Path $outputDirectory "$id-$($model.artifactId).json") -Encoding UTF8
   $records+=@{instrumentId=$id;seed=$model.seed;artifactId=$model.artifactId;manifestHash=$model.manifestHash;inputVersionId=$window.inputVersionId;inputHash=$window.inputHash;targets=$window.targetDates;points=5;repeatExactlyEqual=$true;validationOnly=$true;clippedFraction=$result.clippedFraction;elapsedMillis=$first.elapsedMillis}
   Write-Output "$id seed$($model.seed): five points and repeat verified"
  }
 }
 $records|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $outputDirectory 'summary.json') -Encoding UTF8
} finally {$credential=$null;$secretLine=$null;$headers.Clear()}
