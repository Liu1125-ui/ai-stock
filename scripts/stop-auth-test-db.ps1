#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$containerName = 'aistock-sdd002-auth-test-mysql'
$label = & docker inspect --format '{{index .Config.Labels "aistock.sdd002.auth-test"}}' $containerName
if ($LASTEXITCODE -ne 0 -or $label -ne 'true') { throw 'No matching owned test container; refusing to operate' }
& docker stop $containerName
if ($LASTEXITCODE -ne 0) { throw 'Test database stop failed' }
Write-Output 'Stopped isolated temporary test database. Its tmpfs contents are disposable; production volumes were not touched.'
