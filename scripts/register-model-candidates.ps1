#requires -Version 7.0
param([string]$BaseUrl = 'http://127.0.0.1:18083')
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$candidateDirectory = Join-Path $projectRoot 'runtime/sdd005c/candidates'
$summaryPath = Join-Path $projectRoot 'runtime/sdd005c/registration-summary.json'
$serviceToken = $null
try {
    $tokenLine = [IO.File]::ReadLines((Join-Path $projectRoot '.env')) | Where-Object { $_.StartsWith('SERVICE_TOKEN=') } | Select-Object -First 1
    if (-not $tokenLine) { throw 'SERVICE_TOKEN is not configured locally' }
    $serviceToken = $tokenLine.Substring(14).Trim().Trim('"')
    $tokenLine = $null
    $previous = if (Test-Path -LiteralPath $summaryPath) { Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json } else { @() }
    $records = @()
    foreach ($seed in 42,43,44) {
        $body = [IO.File]::ReadAllText((Join-Path $candidateDirectory "registration-$seed.json"))
        $expected = $body | ConvertFrom-Json
        $first = Invoke-RestMethod -Uri "$BaseUrl/internal/v1/models/candidates" -Method Post -ContentType 'application/json' -Headers @{ 'X-Service-Token' = $serviceToken } -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 30
        $repeat = Invoke-RestMethod -Uri "$BaseUrl/internal/v1/models/candidates" -Method Post -ContentType 'application/json' -Headers @{ 'X-Service-Token' = $serviceToken } -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 30
        $model = $first.data
        if ($first.code -ne 'OK' -or $repeat.code -ne 'OK' -or $model.state -ne 'PENDING' -or $repeat.data.state -ne 'PENDING' -or $model.modelId -ne $repeat.data.modelId -or $model.registrationHash -ne $repeat.data.registrationHash -or $model.artifactId -ne $expected.artifactId -or $model.manifestHash -ne $expected.manifestHash) { throw 'Candidate registration or replay verification failed' }
        foreach ($property in $expected.evaluation.psobject.Properties) {
            if ([string]$model.evaluation.($property.Name) -cne [string]$property.Value) { throw 'Saved evaluation differs from original report' }
        }
        $old = @($previous | Where-Object { $_.seed -eq $seed })
        if ($old.Count -gt 0 -and ($old[0].modelId -ne $model.modelId -or $old[0].registrationHash -ne $model.registrationHash)) { throw 'Persistent registration changed after restart' }
        $records += [ordered]@{seed=$seed;modelId=$model.modelId;artifactId=$model.artifactId;manifestHash=$model.manifestHash;checkpointHash=$model.checkpointHash;sourceHash=$model.evaluation.sourceHash;registrationHash=$model.registrationHash;state=$model.state;revision=$model.revision;replayVerified=$true}
        Write-Host "Verified seed ${seed}: $($model.modelId), PENDING"
    }
    $records | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding utf8
    Write-Host 'Three candidates registered and replay verified. No model published or administrator created.'
} finally { $serviceToken=$null; $tokenLine=$null }
