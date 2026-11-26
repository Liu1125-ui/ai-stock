#requires -Version 7.0
param(
 [string]$CandidateDirectory='runtime/sdd003/candidates',
 [ValidatePattern('^http://127\.0\.0\.1:\d{1,5}$')][string]$MarketUrl='http://127.0.0.1:18082',
 [ValidateNotNullOrEmpty()][ValidatePattern('^(SH|SZ)[0-9]{6}$')][string[]]$InstrumentIds=@('SH600030','SH600276','SZ000002'),
 [switch]$ValidateOnly
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$dir=Resolve-Path -LiteralPath (Join-Path $root $CandidateDirectory)
$checked=foreach($code in ($InstrumentIds | Select-Object -Unique)){
 $code=$code.ToUpperInvariant()
 $file=Join-Path $dir.Path "$code.json"
 if((Get-Item -LiteralPath $file).Length -gt 2097152){throw "Candidate too large: $code"}
 $json=Get-Content -LiteralPath $file -Raw -Encoding UTF8
 $candidate=$json | ConvertFrom-Json
 if($candidate.instrumentId -cne $code -or $candidate.ticker -cne $code -or $candidate.priceBasis -cne 'RAW' -or $candidate.provider -cnotin @('baostock','tushare') -or !$candidate.bars -or $candidate.operationId -cnotmatch '^[A-Za-z0-9_-]{1,128}$'){
  throw "Invalid market candidate: $code"
 }
 [pscustomobject]@{Code=$code;Json=$json;Rows=$candidate.bars.Count}
}
if($ValidateOnly){
 foreach($candidate in $checked){Write-Output "$($candidate.Code) rows=$($candidate.Rows) candidate checked"}
 return
}
$secretLine=Get-Content -LiteralPath (Join-Path $root '.env') -Encoding UTF8 | Where-Object {$_ -match '^SERVICE_TOKEN='} | Select-Object -First 1
if(-not $secretLine){throw 'Service credential missing in ignored .env'}
$credential=$secretLine.Substring('SERVICE_TOKEN='.Length).Trim('"')
if([string]::IsNullOrWhiteSpace($credential)){throw 'Service credential empty'}
try{
 foreach($candidate in $checked){
  try{
   $response=Invoke-RestMethod -Uri "$MarketUrl/internal/v1/market/versions" -Method Post -Headers @{'X-Service-Token'=$credential} -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($candidate.Json)) -TimeoutSec 30
  }catch{
   $code='SERVICE_REQUEST_FAILED'
   try{$body=$_.ErrorDetails.Message | ConvertFrom-Json;if($body.code -cmatch '^[A-Z_]{1,80}$'){$code=$body.code}}catch{}
   throw "Import stopped at $($candidate.Code): $code. Earlier successful items remain published."
  }
  if($response.code -ne 'OK' -or !$response.data.versionId){throw "Candidate rejected: $($candidate.Code)"}
  Write-Output "$($candidate.Code) version $($response.data.versionId) replayed=$($response.data.replayed)"
 }
}finally{$credential=$null;$secretLine=$null}
