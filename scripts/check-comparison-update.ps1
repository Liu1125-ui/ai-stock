#requires -Version 7.0
param([Parameter(Mandatory)][string]$Modules,[Parameter(Mandatory)][string]$Tests)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$valid=@('stock-market-service','stock-scheduler-service')
if(@($Modules.Split(',') | Where-Object {$_ -notin $valid}).Count -or $Tests -notmatch '^[A-Za-z0-9_,]+$'){throw 'Invalid test selection'}
$keys=@('MARKET_TEST_DB_URL','MARKET_TEST_DB_PASSWORD','SCHEDULER_TEST_DB_URL','SCHEDULER_TEST_DB_PASSWORD')
$saved=@{}; foreach($key in $keys){$saved[$key]=[Environment]::GetEnvironmentVariable($key)}
try {
 $secret=[IO.File]::ReadAllText((Join-Path $root 'runtime/auth-test-db-secret.txt')).Trim()
 $env:MARKET_TEST_DB_URL='jdbc:mysql://127.0.0.1:13316/stock_market_test?serverTimezone=UTC'
 $env:SCHEDULER_TEST_DB_URL='jdbc:mysql://127.0.0.1:13316/stock_scheduler_test?serverTimezone=UTC'
 $env:MARKET_TEST_DB_PASSWORD=$secret; $env:SCHEDULER_TEST_DB_PASSWORD=$secret
 & mvn -B -f (Join-Path $root 'backend/pom.xml') '-Pcli-build,auth-integration' -pl $Modules -am "-Dtest=$Tests" '-Dsurefire.failIfNoSpecifiedTests=false' test
 if($LASTEXITCODE -ne 0){throw 'Selected tests failed'}
} finally {
 foreach($key in $keys){[Environment]::SetEnvironmentVariable($key,$saved[$key])}
 $secret=$null
}
