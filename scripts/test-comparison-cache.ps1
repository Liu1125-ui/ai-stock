#requires -Version 7.0
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'comparison-cache.ps1')
$testRoot=Join-Path ([IO.Path]::GetTempPath()) ('stock-comparison-cache-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$lock=$null
try{
 $lock=Enter-ComparisonCacheLock $testRoot
 $rejected=$false
 try{$other=Enter-ComparisonCacheLock $testRoot;$other.Dispose()}catch{$rejected=$true}
 if(-not $rejected){throw 'Duplicate cache lock unexpectedly acquired'}
 Save-ComparisonCacheJson $testRoot 'candidate.json' @{value='10.000000000001'}
 $saved=Get-Content -Raw (Join-Path $testRoot 'candidate.json') | ConvertFrom-Json
 if($saved.value -ne '10.000000000001'){throw 'Cache changed precise value'}
 Begin-ComparisonSourceAttempt $testRoot 'SH600030'
 $rejected=$false
 try{Begin-ComparisonSourceAttempt $testRoot 'SH600030'}catch{$rejected=$true}
 if(-not $rejected){throw 'Failed/uncertain source attempt was silently retried'}
 Write-Output 'Comparison cache locking, atomic save and one-attempt guard PASS'
}finally{
 if($null -ne $lock){$lock.Dispose()}
 # Only this newly created exact temp directory is removed, without recursion.
 Get-ChildItem -LiteralPath $testRoot -File | Remove-Item
 Remove-Item -LiteralPath $testRoot
}
