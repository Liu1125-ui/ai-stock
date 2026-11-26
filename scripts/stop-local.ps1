$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $projectRoot
try {
    & docker compose --env-file .env -f deploy/compose.yaml stop
    if ($LASTEXITCODE -ne 0) { throw 'Compose stop failed' }
} finally { Pop-Location }
