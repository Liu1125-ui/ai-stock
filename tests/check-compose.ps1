#requires -Version 7.0
param([switch]$SkipFault)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $projectRoot
$compose = @('compose','--env-file','.env','-f','deploy/compose.yaml')
$values = @{}
Get-Content -LiteralPath '.env' | ForEach-Object {
    $parts = $_ -split '=',2
    if ($parts.Count -eq 2) { $values[$parts[0]] = $parts[1] }
}
$headers = @{'X-Service-Token'=$values['SERVICE_TOKEN']; 'X-Trace-Id'='compose-check-001'}
$results = [Collections.Generic.List[object]]::new()
function Record([string]$name, [object]$detail) {
    $results.Add([pscustomobject]@{check=$name;passed=$true;detail=$detail})
    Write-Output "PASS $name"
}
function Wait-Service([int]$port) {
    for ($i=0; $i -lt 90; $i++) {
        try {
            $r = Invoke-RestMethod "http://127.0.0.1:$port/actuator/health" -TimeoutSec 2
            if ($r.status -eq 'UP') { return }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw "Service $port not healthy"
}

foreach ($port in 18081..18085) { Wait-Service $port; Record "database-aware-health-$port" 'UP' }
for ($i=0; $i -lt 60; $i++) {
    try {
        $front = Invoke-RestMethod 'http://127.0.0.1:15173/api/v1/system/status' -TimeoutSec 2
        $algorithm = Invoke-RestMethod 'http://127.0.0.1:18090/health' -TimeoutSec 2
        if ($front.data.component -eq 'stock-gateway' -and $algorithm.inference -eq 'UNAVAILABLE') { break }
    } catch { }
    Start-Sleep -Milliseconds 500
}
if ($front.data.component -ne 'stock-gateway' -or $algorithm.inference -ne 'UNAVAILABLE') { throw 'Frontend or algorithm not ready' }
Record 'frontend-proxy-and-algorithm-health' 'gateway reachable; inference unavailable'
$denied = Invoke-WebRequest 'http://127.0.0.1:18090/internal/v1/infer' -Method Post -SkipHttpErrorCheck
if ($denied.StatusCode -ne 401) { throw 'Algorithm accepted missing credentials' }
$unavailable = Invoke-WebRequest 'http://127.0.0.1:18090/internal/v1/infer' -Method Post -Headers $headers -SkipHttpErrorCheck
if ($unavailable.StatusCode -ne 503 -or ($unavailable.Content | ConvertFrom-Json).detail -ne 'PUBLISHED_MODEL_NOT_CONFIGURED') { throw 'Algorithm inference contract failed' }
$hidden = Invoke-WebRequest 'http://127.0.0.1:15173/internal/v1/infer' -Method Post -SkipHttpErrorCheck
if ($hidden.StatusCode -ne 404) { throw 'Frontend exposes internal endpoint' }
Record 'algorithm-auth-and-no-fake-prediction' '401 without credentials; 503 without model; frontend internal 404'
$nacosHostPort=if($values['NACOS_HOST_PORT']){$values['NACOS_HOST_PORT']}else{'18488'}
$roles = @('user','market','prediction','scheduler','notification')
foreach ($role in $roles) {
    $r = Invoke-RestMethod "http://127.0.0.1:$nacosHostPort/nacos/v1/ns/instance/list?serviceName=stock-$role-service" -TimeoutSec 5
    if (@($r.hosts | Where-Object healthy).Count -ne 1) { throw "Nacos $role has no single healthy instance" }
    Record "nacos-$role" 'one healthy registered instance'
}
$sqlCheck = @'
set -euo pipefail
for role in user market prediction scheduler notification; do
  key="${role^^}_DB_PASSWORD"
  own=$(MYSQL_PWD="${!key}" mysql -u"stock_${role}" -D"stock_${role}" -Nse 'SELECT DATABASE();')
  test "$own" = "stock_${role}"
  other=user
  if [ "$role" = user ]; then other=market; fi
  if MYSQL_PWD="${!key}" mysql -u"stock_${role}" -Nse "USE stock_${other};" 2>/tmp/stock-denied; then
    echo "Cross-database access unexpectedly permitted" >&2; exit 1
  fi
  grep -q 'ERROR 1044' /tmp/stock-denied
  echo "$role own database allowed, other database denied"
done
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -Nse 'SELECT VERSION();'
'@
$dbResult = & docker @compose exec -T mysql bash -c $sqlCheck
if ($LASTEXITCODE -ne 0) { throw 'Database isolation failed' }
Record 'five-database-isolation' ($dbResult -join '; ')
$response = Invoke-RestMethod 'http://127.0.0.1:18083/internal/v1/diagnostics/instruments/DEMO-001' -Headers $headers -TimeoutSec 8
if ($response.data.code -ne 'DEMO.XX' -or $response.data.sourceService -ne 'stock-market-service' -or -not $response.data.demoOnly -or $response.traceId -ne 'compose-check-001') { throw 'Nacos/Feign result invalid' }
Record 'nacos-feign-market-lookup' $response.data
$denied = Invoke-WebRequest 'http://127.0.0.1:18082/internal/v1/instruments/DEMO-001' -SkipHttpErrorCheck -TimeoutSec 5
if ($denied.StatusCode -ne 401) { throw 'Internal credential check failed' }
Record 'internal-auth' 401
$hidden = Invoke-WebRequest 'http://127.0.0.1:18080/internal/v1/instruments/DEMO-001' -SkipHttpErrorCheck -TimeoutSec 5
if ($hidden.StatusCode -ne 404) { throw 'Gateway exposed internal endpoint' }
Record 'gateway-hides-internal-endpoint' 404

if (-not $SkipFault) {
    try {
        & docker @compose stop stock-market-service | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Market stop failed' }
        $codes = @()
        for($i=0;$i -lt 4;$i++) {
            $r = Invoke-WebRequest 'http://127.0.0.1:18083/internal/v1/diagnostics/instruments/DEMO-001' -Headers $headers -SkipHttpErrorCheck -TimeoutSec 10
            $body = $r.Content | ConvertFrom-Json
            if ($r.StatusCode -ne 503 -or $body.data -ne $null -or $body.traceId -ne 'compose-check-001') { throw 'Failure response invalid' }
            $codes += $body.code
        }
        if ('DEPENDENCY_CIRCUIT_OPEN' -notin $codes) { throw 'Sentinel did not open' }
        Record 'market-stop-sentinel' ($codes -join ',')
    } finally {
        & docker @compose start stock-market-service | Out-Null
    }
    Wait-Service 18082
    $recovered = $false
    for($i=0;$i -lt 40;$i++) {
        try {
            $r = Invoke-RestMethod 'http://127.0.0.1:18083/internal/v1/diagnostics/instruments/DEMO-001' -Headers $headers -TimeoutSec 8
            if ($r.data.code -eq 'DEMO.XX') { $recovered=$true; break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $recovered) { throw 'Market did not recover through discovery/circuit breaker' }
    Record 'market-recovery' 'DEMO.XX'
}
New-Item -ItemType Directory -Path runtime -Force | Out-Null
$evidence = [pscustomobject]@{checkedAt=[DateTimeOffset]::Now.ToString('o'); realData=$false; results=$results.ToArray()}
$evidencePath = "runtime/compose-verification-$([DateTime]::Now.ToString('yyyyMMdd-HHmmss')).json"
$evidence | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $evidencePath -Encoding utf8
Write-Output "Evidence saved to ignored $evidencePath (no credentials)."
