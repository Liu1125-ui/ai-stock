param([string]$AlgorithmUrl='http://127.0.0.1:18090')
$ErrorActionPreference='Stop';$root=Split-Path $PSScriptRoot -Parent
$secretLine=Get-Content (Join-Path $root '.env') -Encoding UTF8 | Where-Object {$_ -match '^SERVICE_TOKEN='} | Select-Object -First 1
if(-not $secretLine){throw 'Service credential missing'}
$credential=$secretLine.Substring('SERVICE_TOKEN='.Length).Trim('"')
if([string]::IsNullOrWhiteSpace($credential)){throw 'Service credential empty'}
$body=@{operationId='online-probe-'+(Get-Date -Format yyyyMMddHHmmss);provider='baostock';instrumentId='SH600030';start=(Get-Date).AddDays(-7).ToString('yyyy-MM-dd');end=(Get-Date).ToString('yyyy-MM-dd')} | ConvertTo-Json -Compress
$response=Invoke-RestMethod -Uri "$AlgorithmUrl/internal/v1/market/collect" -Method Post -Headers @{'X-Service-Token'=$credential} -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 35
Write-Output "Baostock result=$($response.state) rows=$(@($response.candidate.bars).Count)"
