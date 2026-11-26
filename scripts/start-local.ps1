#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $projectRoot
try {
    & "$PSScriptRoot/init-local-env.ps1"
    Push-Location backend
    try { & mvn -q -Pcli-build clean package; if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' } }
    finally { Pop-Location }
    Push-Location frontend
    try {
        & pnpm install --frozen-lockfile
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency install failed' }
        & pnpm build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
    } finally { Pop-Location }
    & docker compose --env-file .env -f deploy/compose.yaml up -d --build
    if ($LASTEXITCODE -ne 0) { throw 'Compose startup failed' }
    & "$projectRoot/tests/check-compose.ps1" -SkipFault
    if ($LASTEXITCODE -ne 0) { throw 'Integration checks failed' }
    Write-Output 'Engineering preview: http://127.0.0.1:15173'
} finally { Pop-Location }
