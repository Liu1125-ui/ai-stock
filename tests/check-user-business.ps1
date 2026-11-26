#requires -Version 7.0
param([switch]$Fault,[switch]$GatewayFault)
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$base='http://127.0.0.1:15173'
$session=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
function Assert-Status($response,[int]$expected,[string]$name){
    if($response.StatusCode -ne $expected){throw "$name status mismatch: $($response.StatusCode)"}
    $body=$response.Content|ConvertFrom-Json
    if($expected -ge 400 -and $null -ne $body.data){throw "$name failure includes success data"}
    Write-Output "PASS $name"
}
$csrf=Invoke-RestMethod "$base/api/v1/auth/csrf" -WebSession $session -TimeoutSec 10
if(-not $csrf.data.token){throw 'Missing CSRF token'}
Write-Output 'PASS csrf-through-frontend-and-gateway'
$denied=Invoke-WebRequest "$base/api/v1/users/me" -Headers @{'X-User-Id'='forged';'X-Role'='ADMIN'} -SkipHttpErrorCheck -TimeoutSec 10
Assert-Status $denied 401 'forged-identity-denied'
$write=Invoke-WebRequest "$base/api/v1/auth/logout" -Method Post -WebSession $session -Headers @{Origin=$base} -SkipHttpErrorCheck -TimeoutSec 10
Assert-Status $write 403 'missing-csrf-denied'
$headers=@{'X-XSRF-TOKEN'=$csrf.data.token;Origin='https://untrusted.example'}
$origin=Invoke-WebRequest "$base/api/v1/auth/logout" -Method Post -WebSession $session -Headers $headers -SkipHttpErrorCheck -TimeoutSec 10
# CORS may reject before JSON security handlers; status must still be 403.
if($origin.StatusCode -ne 403){throw 'Untrusted Origin accepted'}
Write-Output 'PASS cross-origin-write-denied'
$internal=Invoke-WebRequest "$base/internal/v1/sessions/resolve" -Method Post -SkipHttpErrorCheck -TimeoutSec 10
if($internal.StatusCode -ne 404){throw 'Frontend exposes internal sessions'}
Write-Output 'PASS internal-interface-hidden'
$serviceAuth=Invoke-WebRequest 'http://127.0.0.1:18081/internal/v1/sessions/resolve' -Method Post -ContentType 'application/json' -Body '{"token":"invalid"}' -SkipHttpErrorCheck -TimeoutSec 10
Assert-Status $serviceAuth 401 'internal-session-service-auth-required'
if($Fault){
    Push-Location $projectRoot
    try{
        & docker compose --env-file .env -f deploy/compose.yaml stop stock-user-service | Out-Null
        if($LASTEXITCODE -ne 0){throw 'Could not stop scoped user service'}
        $unavailable=Invoke-WebRequest "$base/api/v1/auth/csrf" -SkipHttpErrorCheck -TimeoutSec 15
        Assert-Status $unavailable 503 'user-service-outage-controlled'
    }finally{
        & docker compose --env-file .env -f deploy/compose.yaml start stock-user-service | Out-Null
        Pop-Location
    }
    $recovered=$false
    for($i=0;$i -lt 45;$i++){
        try{$health=Invoke-RestMethod "$base/api/v1/auth/csrf" -TimeoutSec 3;if($health.data.token){$recovered=$true;break}}catch{}
        Start-Sleep -Seconds 1
    }
    if(-not $recovered){throw 'User service did not recover'}
    Write-Output 'PASS user-service-recovery'
}
if($GatewayFault){
 Push-Location $projectRoot
 try{
  & docker compose --env-file .env -f deploy/compose.yaml stop stock-gateway | Out-Null
  if($LASTEXITCODE -ne 0){throw 'Gateway stop failed'}
  $down=Invoke-WebRequest "$base/api/v1/auth/csrf" -SkipHttpErrorCheck -TimeoutSec 15
  Assert-Status $down 503 'gateway-outage-controlled-json'
 }finally{& docker compose --env-file .env -f deploy/compose.yaml start stock-gateway | Out-Null;Pop-Location}
 $ready=$false
 for($i=0;$i -lt 45;$i++){try{$value=Invoke-RestMethod "$base/api/v1/auth/csrf" -TimeoutSec 3;if($value.data.token){$ready=$true;break}}catch{};Start-Sleep -Seconds 1}
 if(-not $ready){throw 'Gateway recovery failed'}
 Write-Output 'PASS gateway-recovery'
}
