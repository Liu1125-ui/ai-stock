#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$containerName = 'aistock-sdd002-auth-test-mysql'
$secretPath = Join-Path $projectRoot 'runtime/auth-test-db-secret.txt'
$labelKey = 'aistock.sdd002.auth-test'
$existing = & docker ps -a --filter "name=^/$containerName$" --format '{{.Names}}'
if ($LASTEXITCODE -ne 0) { throw 'Docker engine unavailable' }
if ($existing) {
    $label = & docker inspect --format '{{index .Config.Labels "aistock.sdd002.auth-test"}}' $containerName
    if ($label -ne 'true') { throw 'Container name belongs to another workload' }
    if (-not (Test-Path -LiteralPath $secretPath)) { throw 'Existing test credential missing; do not overwrite it' }
    & docker start $containerName | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Test container could not start' }
} else {
    New-Item -ItemType Directory -Force (Split-Path $secretPath) | Out-Null
    $secret = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    [IO.File]::WriteAllText($secretPath, $secret)
    $previousPassword = $env:MYSQL_ROOT_PASSWORD
    try {
        $env:MYSQL_ROOT_PASSWORD = $secret
        & docker run -d --name $containerName --label "$labelKey=true" --memory 512m --env MYSQL_ROOT_PASSWORD --env MYSQL_DATABASE=stock_user_test --publish '127.0.0.1:13316:3306' --tmpfs '/var/lib/mysql:rw' 'mysql:8.0@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Test database startup failed' }
    } finally {
        $env:MYSQL_ROOT_PASSWORD = $previousPassword
        $secret = $null
    }
}
$ready = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    & docker exec $containerName sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h 127.0.0.1 -u root -Nse "SELECT 1" stock_user_test' 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Milliseconds 500
}
if (-not $ready) { throw 'Isolated test MySQL not ready; production containers were not modified' }
& docker exec $containerName sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h 127.0.0.1 -u root -e "CREATE DATABASE IF NOT EXISTS stock_notification_test CHARACTER SET ascii COLLATE ascii_bin"'
if ($LASTEXITCODE -ne 0) { throw 'Isolated mail test database creation failed' }
& docker exec $containerName sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h 127.0.0.1 -u root -e "CREATE DATABASE IF NOT EXISTS stock_market_test CHARACTER SET utf8mb4 COLLATE utf8mb4_bin"'
if ($LASTEXITCODE -ne 0) { throw 'Isolated market test database creation failed' }
& docker exec $containerName sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h 127.0.0.1 -u root -e "CREATE DATABASE IF NOT EXISTS stock_scheduler_test CHARACTER SET utf8mb4 COLLATE utf8mb4_bin"'
if ($LASTEXITCODE -ne 0) { throw 'Isolated scheduler test database creation failed' }
& docker exec $containerName sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h 127.0.0.1 -u root -e "CREATE DATABASE IF NOT EXISTS stock_prediction_test CHARACTER SET utf8mb4 COLLATE utf8mb4_bin"'
if ($LASTEXITCODE -ne 0) { throw 'Isolated prediction test database creation failed' }
Write-Output 'Isolated MySQL ready: 127.0.0.1:13316/stock_user_test. Credential stored in ignored runtime; no production volume attached.'

