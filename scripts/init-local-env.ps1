#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $projectRoot '.env'
$keys = @('MYSQL_ROOT_PASSWORD','SERVICE_TOKEN','USER_DB_PASSWORD','MARKET_DB_PASSWORD','PREDICTION_DB_PASSWORD','SCHEDULER_DB_PASSWORD','NOTIFICATION_DB_PASSWORD','MAIL_PAYLOAD_KEY','MAIL_DIGEST_KEY','AUTH_PAYLOAD_KEY','AUTH_DIGEST_KEY')
$lines = [Collections.Generic.List[string]]::new()
if (Test-Path -LiteralPath $envFile) { foreach($line in [IO.File]::ReadAllLines($envFile)) { $lines.Add($line) } }
foreach($key in $keys) {
    if (@($lines | Where-Object { $_.StartsWith("$key=") }).Count -gt 0) { continue }
    $size = if ($key -like '*_KEY') { 32 } else { 24 }
    $bytes = New-Object byte[] $size
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $value = [Convert]::ToHexString($bytes)
    $lines.Add("$key=$value")
}
[IO.File]::WriteAllLines($envFile, $lines)
Write-Output 'Ignored .env initialized; missing keys added, existing credentials preserved.'
