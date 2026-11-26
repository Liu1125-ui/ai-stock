#requires -Version 5.1
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$names = @('AISTOCK_ADMIN_BOOTSTRAP','AISTOCK_ADMIN_EMAIL','AISTOCK_ADMIN_PASSWORD','AISTOCK_ADMIN_NICKNAME')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
$pointer = [IntPtr]::Zero
Push-Location $projectRoot
try {
    $env:AISTOCK_ADMIN_EMAIL = Read-Host 'Admin email (not already registered)'
    $env:AISTOCK_ADMIN_NICKNAME = Read-Host 'Admin nickname (2-30 characters)'
    $secret = Read-Host 'Admin password (12-64 characters; input hidden)' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    $env:AISTOCK_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $env:AISTOCK_ADMIN_BOOTSTRAP = '1'
    # Pass environment variable NAMES, never credentials in command-line arguments.
    & docker compose --env-file .env -f deploy/compose.yaml run --rm --no-deps `
        -e AISTOCK_ADMIN_BOOTSTRAP -e AISTOCK_ADMIN_EMAIL -e AISTOCK_ADMIN_PASSWORD -e AISTOCK_ADMIN_NICKNAME stock-user-service
    if ($LASTEXITCODE -ne 0) { throw 'Admin initialization failed. Review the controlled status above.' }
} finally {
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
    if ($secret) { $secret.Dispose() }
    Pop-Location
}
