$ErrorActionPreference = 'Stop'
$backend = (Resolve-Path (Join-Path $PSScriptRoot '..\backend')).Path
$token = if ([string]::IsNullOrWhiteSpace($env:SERVICE_TOKEN)) { 'local-demo-secret' } else { $env:SERVICE_TOKEN }
$env:SERVICE_TOKEN = $token
$env:MARKET_URL = 'http://127.0.0.1:8082'
$env:NACOS_ENABLED = 'false'
$marketJar = Join-Path $backend 'stock-market-service\target\stock-market-service-0.1.0-SNAPSHOT.jar'
$predictionJar = Join-Path $backend 'stock-prediction-service\target\stock-prediction-service-0.1.0-SNAPSHOT.jar'
$marketLog = Join-Path $env:TEMP 'stock-market-run.log'
$predictionLog = Join-Path $env:TEMP 'stock-prediction-run.log'
$market = $null
$prediction = $null

function Wait-Health([int]$port) {
    for ($i=0; $i -lt 60; $i++) {
        Start-Sleep -Milliseconds 500
        try {
            $result = Invoke-RestMethod -Uri "http://127.0.0.1:$port/actuator/health" -TimeoutSec 1
            if ($result.status -eq 'UP') { return }
        } catch { }
    }
    throw "Service on $port did not become healthy"
}

try {
    $market = Start-Process -FilePath 'java' -ArgumentList @('-jar', $marketJar) -PassThru -WindowStyle Hidden -RedirectStandardOutput $marketLog -RedirectStandardError ($marketLog + '.err')
    Wait-Health 8082
    $prediction = Start-Process -FilePath 'java' -ArgumentList @('-jar', $predictionJar) -PassThru -WindowStyle Hidden -RedirectStandardOutput $predictionLog -RedirectStandardError ($predictionLog + '.err')
    Wait-Health 8083

    $headers = @{'X-Service-Token'=$token;'X-Trace-Id'='trace-1234'}
    $direct = Invoke-RestMethod -Uri 'http://127.0.0.1:8082/internal/v1/instruments/DEMO-001' -Headers $headers
    if ($direct.data.code -ne 'DEMO.XX' -or -not $direct.data.demoOnly) { throw 'Market data did not come from marked fixture' }
    $viaPrediction = Invoke-RestMethod -Uri 'http://127.0.0.1:8083/internal/v1/diagnostics/instruments/DEMO-001' -Headers $headers
    if ($viaPrediction.data.code -ne $direct.data.code -or $viaPrediction.data.sourceService -ne 'stock-market-service') { throw 'Cross-service value mismatch' }
    Write-Output "normal: $($viaPrediction.data.code) from $($viaPrediction.data.sourceService), trace=$($viaPrediction.traceId)"

    $unauthorized = Invoke-WebRequest -Uri 'http://127.0.0.1:8082/internal/v1/instruments/DEMO-001' -SkipHttpErrorCheck
    if ($unauthorized.StatusCode -ne 401) { throw "Expected 401, got $($unauthorized.StatusCode)" }
    Write-Output 'without service credential: 401'

    Stop-Process -Id $market.Id -Force
    Start-Sleep -Seconds 2
    $unavailable = Invoke-WebRequest -Uri 'http://127.0.0.1:8083/internal/v1/diagnostics/instruments/DEMO-001' -Headers $headers -SkipHttpErrorCheck
    $body = $unavailable.Content | ConvertFrom-Json
    if ($unavailable.StatusCode -ne 503 -or $body.code -ne 'DEPENDENCY_UNAVAILABLE' -or $body.data -ne $null) { throw "Unexpected failure behavior: $($unavailable.StatusCode) $($unavailable.Content)" }
    Write-Output "market stopped: 503 $($body.code), trace=$($body.traceId)"

    $market = Start-Process -FilePath 'java' -ArgumentList @('-jar', $marketJar) -PassThru -WindowStyle Hidden -RedirectStandardOutput $marketLog -RedirectStandardError ($marketLog + '.err')
    Wait-Health 8082
    $recovered = Invoke-RestMethod -Uri 'http://127.0.0.1:8083/internal/v1/diagnostics/instruments/DEMO-001' -Headers $headers
    if ($recovered.data.code -ne 'DEMO.XX') { throw 'Service did not recover' }
    Write-Output "recovered: $($recovered.data.code)"
} catch {
    Write-Output "ERROR: $($_.Exception.Message)"
    if (Test-Path $marketLog) { Get-Content -LiteralPath $marketLog -Tail 35 }
    if (Test-Path $predictionLog) { Get-Content -LiteralPath $predictionLog -Tail 35 }
    throw
} finally {
    if ($market) { Stop-Process -Id $market.Id -Force -ErrorAction SilentlyContinue }
    if ($prediction) { Stop-Process -Id $prediction.Id -Force -ErrorAction SilentlyContinue }
}
