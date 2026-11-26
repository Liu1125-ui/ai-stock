#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$secretPath = Join-Path $projectRoot 'runtime/auth-test-db-secret.txt'
if (-not (Test-Path -LiteralPath $secretPath)) { throw 'Run start-auth-test-db.ps1 first' }
$previousUrl = $env:AUTH_TEST_DB_URL
$previousPassword = $env:AUTH_TEST_DB_PASSWORD
$previousMailUrl = $env:MAIL_TEST_DB_URL
$previousMailPassword = $env:MAIL_TEST_DB_PASSWORD
$previousSchedulerUrl = $env:SCHEDULER_TEST_DB_URL
$previousSchedulerPassword = $env:SCHEDULER_TEST_DB_PASSWORD
$previousMarketUrl = $env:MARKET_TEST_DB_URL
$previousMarketPassword = $env:MARKET_TEST_DB_PASSWORD
$previousPredictionUrl = $env:PREDICTION_TEST_DB_URL
$previousPredictionPassword = $env:PREDICTION_TEST_DB_PASSWORD
Push-Location (Join-Path $projectRoot 'backend')
try {
    $env:AUTH_TEST_DB_URL = 'jdbc:mysql://127.0.0.1:13316/stock_user_test?serverTimezone=UTC'
    $env:AUTH_TEST_DB_PASSWORD = [IO.File]::ReadAllText($secretPath).Trim()
    $env:MAIL_TEST_DB_URL = 'jdbc:mysql://127.0.0.1:13316/stock_notification_test?serverTimezone=UTC'
    $env:MAIL_TEST_DB_PASSWORD = $env:AUTH_TEST_DB_PASSWORD
    $env:MARKET_TEST_DB_URL = 'jdbc:mysql://127.0.0.1:13316/stock_market_test?serverTimezone=UTC'
    $env:MARKET_TEST_DB_PASSWORD = $env:AUTH_TEST_DB_PASSWORD
    $env:SCHEDULER_TEST_DB_URL = 'jdbc:mysql://127.0.0.1:13316/stock_scheduler_test?serverTimezone=UTC'
    $env:SCHEDULER_TEST_DB_PASSWORD = $env:AUTH_TEST_DB_PASSWORD
    $env:PREDICTION_TEST_DB_URL = 'jdbc:mysql://127.0.0.1:13316/stock_prediction_test?serverTimezone=UTC'
    $env:PREDICTION_TEST_DB_PASSWORD = $env:AUTH_TEST_DB_PASSWORD
    & mvn -B '-Pauth-integration,cli-build' clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Auth Task1 tests failed; read Maven failure details' }
} finally {
    $env:AUTH_TEST_DB_URL = $previousUrl
    $env:AUTH_TEST_DB_PASSWORD = $previousPassword
    $env:MAIL_TEST_DB_URL = $previousMailUrl
    $env:MAIL_TEST_DB_PASSWORD = $previousMailPassword
    $env:MARKET_TEST_DB_URL = $previousMarketUrl
    $env:MARKET_TEST_DB_PASSWORD = $previousMarketPassword
    $env:SCHEDULER_TEST_DB_URL = $previousSchedulerUrl
    $env:SCHEDULER_TEST_DB_PASSWORD = $previousSchedulerPassword
    $env:PREDICTION_TEST_DB_URL = $previousPredictionUrl
    $env:PREDICTION_TEST_DB_PASSWORD = $previousPredictionPassword
    Pop-Location
}

