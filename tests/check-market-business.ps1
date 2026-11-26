#requires -Version 7.0
param([switch]$Faults)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
$compose=@('compose','--env-file','.env','-f','deploy/compose.yaml')
$checks=[Collections.Generic.List[object]]::new()
function Status([string]$url,[string]$method='GET',[string]$body=''){
 $args=@{Uri=$url;Method=$method;TimeoutSec=8;SkipHttpErrorCheck=$true}
 if($method -eq 'POST'){$args.ContentType='application/json';$args.Body=$body}
 return (Invoke-WebRequest @args).StatusCode
}
function Check([string]$name,[int]$actual,[int]$expected){if($actual -ne $expected){throw "$name expected $expected, got $actual"};$checks.Add([pscustomobject]@{name=$name;status=$actual});Write-Output "PASS $name $actual"}
function WaitPort([int]$port){for($i=0;$i -lt 50;$i++){try{$s=(Invoke-RestMethod "http://127.0.0.1:$port/actuator/health" -TimeoutSec 2).status;if($s -eq 'UP'){return}}catch{};Start-Sleep -Milliseconds 400};throw "Service $port did not recover"}
try{
 Check 'gateway-session-required' (Status 'http://127.0.0.1:18080/api/v1/instruments') 401
 Check 'market-direct-session-required' (Status 'http://127.0.0.1:18082/api/v1/instruments') 401
 Check 'gateway-internal-hidden' (Status 'http://127.0.0.1:18080/internal/v1/market/versions') 404
 Check 'market-import-requires-service-token-before-JSON' (Status 'http://127.0.0.1:18082/internal/v1/market/versions' 'POST' '{}') 401
 $sql='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root -D stock_market -Nse "SELECT CONCAT(instrument_id,CHAR(9),COUNT(*),CHAR(9),MIN(trade_date),CHAR(9),MAX(trade_date)) FROM market_daily_bar b JOIN market_data_version v USING(version_id) GROUP BY instrument_id ORDER BY instrument_id; SELECT COUNT(*) FROM market_data_version;"'
 $rows=@(& docker @compose exec -T mysql sh -c $sql)
 if($LASTEXITCODE -ne 0 -or $rows.Count -ne 4){throw 'Version audit failed'}
 foreach($line in $rows[0..2]){if($line.Replace('\t',' ') -notmatch '^(SH600030|SH600276|SZ000002)\s+421\s+2025-01-02\s+2026-09-24$'){throw 'Unexpected source coverage'}}
 if($rows[3] -ne '3'){throw 'Unexpected version count'}
 $checks.Add([pscustomobject]@{name='historical-versions';rowsPerInstrument=421;versionCount=3;lastTradeDate='2026-09-24'})
 Write-Output 'PASS three historical stock versions, 421 rows each, no duplicates'
 if($Faults){
  try{
   & docker @compose stop algorithm | Out-Null;if($LASTEXITCODE -ne 0){throw 'Stop algorithm failed'}
   Check 'algorithm-down-market-health' (Status 'http://127.0.0.1:18082/actuator/health') 200
  }finally{& docker @compose start algorithm | Out-Null}
  try{
   & docker @compose stop stock-market-service | Out-Null;if($LASTEXITCODE -ne 0){throw 'Stop market failed'}
   Check 'market-down-gateway-fails' (Status 'http://127.0.0.1:18080/api/v1/instruments') 503
   Check 'market-down-user-still-protects-watchlist' (Status 'http://127.0.0.1:18081/api/v1/users/me/watchlist') 401
  }finally{& docker @compose start stock-market-service | Out-Null;WaitPort 18082}
  try{
   & docker @compose stop stock-user-service | Out-Null;if($LASTEXITCODE -ne 0){throw 'Stop user failed'}
   $response=Invoke-WebRequest 'http://127.0.0.1:18082/api/v1/instruments' -Headers @{Cookie='AISTOCK_SESSION=invalid-test-session'} -SkipHttpErrorCheck -TimeoutSec 8
   Check 'user-down-market-fails-closed' $response.StatusCode 503
  }finally{& docker @compose start stock-user-service | Out-Null;WaitPort 18081}
 }
 $path=Join-Path $root ('runtime/sdd003/market-business-'+(Get-Date -Format yyyyMMdd-HHmmss)+'.json')
 [pscustomobject]@{checkedAt=[DateTimeOffset]::Now.ToString('o');checks=$checks.ToArray()} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $path -Encoding utf8
 Write-Output "Evidence saved to ignored $path"
}finally{Pop-Location}
