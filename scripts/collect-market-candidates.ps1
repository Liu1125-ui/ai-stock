#requires -Version 7.0
param(
 [Parameter(Mandatory)][ValidateNotNullOrEmpty()][ValidatePattern('^(SH|SZ)[0-9]{6}$')][string[]]$InstrumentIds,
 [Parameter(Mandatory)][ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string]$StartDate,
 [Parameter(Mandatory)][ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string]$EndDate,
 [string]$CandidateDirectory='runtime/market-onboarding',
 [switch]$ReuseSaved
)
$ErrorActionPreference='Stop'
$start=[DateOnly]::ParseExact($StartDate,'yyyy-MM-dd')
$end=[DateOnly]::ParseExact($EndDate,'yyyy-MM-dd')
if($end.DayNumber-$start.DayNumber -notin (0..1500)){throw 'Date range must span between 0 and 1500 calendar days'}
$root=Split-Path $PSScriptRoot -Parent
$dir=Join-Path $root $CandidateDirectory
$secretLine=Get-Content -LiteralPath (Join-Path $root '.env') -Encoding UTF8 | Where-Object {$_ -match '^SERVICE_TOKEN='} | Select-Object -First 1
if(!$secretLine){throw 'Local service credential missing'}
$credential=$secretLine.Substring('SERVICE_TOKEN='.Length).Trim('"')
if([string]::IsNullOrWhiteSpace($credential)){throw 'Local service credential empty'}
New-Item -ItemType Directory -Force -Path $dir | Out-Null
try{
 foreach($stock in ($InstrumentIds | Select-Object -Unique)){
  $id=$stock.ToUpperInvariant()
  $file=Join-Path $dir "$id.json"
  if(Test-Path -LiteralPath $file){
   if(!$ReuseSaved){throw "Candidate already exists: $id"}
   $saved=Get-Content -LiteralPath $file -Raw | ConvertFrom-Json
   if($saved.instrumentId -cne $id -or $saved.provider -cne 'baostock' -or $saved.calendar[0].date -ne $StartDate -or $saved.calendar[-1].date -ne $EndDate){throw "Saved candidate differs from requested data: $id"}
   Write-Output "$id reused"
   continue
  }
  $body=@{operationId='onboard-'+[guid]::NewGuid().ToString('N');provider='baostock';instrumentId=$id;start=$StartDate;end=$EndDate}
  try{
   $result=Invoke-RestMethod -Uri 'http://127.0.0.1:18090/internal/v1/market/collect' -Method Post -Headers @{'X-Service-Token'=$credential} -ContentType 'application/json' -Body ($body|ConvertTo-Json -Compress) -TimeoutSec 32
  }catch {throw "Could not collect $id. Check the local algorithm service and retry."}
  if($result.state -cne 'AVAILABLE' -or $result.operationId -cne $body.operationId -or $result.candidate.instrumentId -cne $id -or !$result.candidate.bars){throw "No publishable candidate for $id ($($result.state))"}
  $payload=$result.candidate | ConvertTo-Json -Depth 14 -Compress
  if([Text.Encoding]::UTF8.GetByteCount($payload) -gt 2097152){throw "Candidate too large: $id"}
  $temporary=Join-Path $dir ('.'+[guid]::NewGuid().ToString('N')+'.tmp')
  try{
   [IO.File]::WriteAllText($temporary,$payload)
   [IO.File]::Move($temporary,$file)
  }finally{if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary}}
  Write-Output "$id collected $($result.candidate.bars.Count) rows"
 }
}finally{$credential=$null;$secretLine=$null}
