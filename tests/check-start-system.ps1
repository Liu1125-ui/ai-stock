$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../scripts/start-system.ps1')
function Assert($condition, $message) { if (-not $condition) { throw $message } }
$script:events = [System.Collections.Generic.List[string]]::new()
Invoke-StartupSequence -Start { param($s) $script:events.Add("start:$s") } -Wait { param($s) $script:events.Add("ready:$s") }
Assert ($events.IndexOf('ready:nacos') -lt $events.IndexOf('start:stock-user-service')) 'Nacos must be ready before business services start'
Assert ($events.IndexOf('ready:stock-notification-service') -lt $events.IndexOf('start:stock-gateway')) 'All business services must be ready before gateway'
Assert ($events.IndexOf('ready:stock-gateway') -lt $events.IndexOf('start:frontend')) 'Gateway must be ready before frontend'
$script:events.Clear()
$failed = $false
try { Invoke-StartupSequence -Start { param($s) $script:events.Add($s) } -Wait { param($s) if ($s -eq 'nacos') { throw 'test dependency unavailable' } } } catch { $failed = $true }
Assert $failed 'Dependency failure must propagate'
Assert (-not $events.Contains('stock-user-service')) 'Dependency failure must stop downstream startup'
Assert (Test-ContainerIdentity 'aistock-sdd001' 'mysql' 'mysql') 'Original container should be accepted'
Assert (-not (Test-ContainerIdentity 'other' 'mysql' 'mysql')) 'Other project must be rejected'
Assert (-not (Test-ContainerIdentity 'aistock-sdd001' 'frontend' 'mysql')) 'Wrong service must be rejected'
'PASS: startup order, dependency failure gating, container identity'
function Invoke-WebRequest { return [pscustomobject]@{ StatusCode=200; Content=[Text.Encoding]::UTF8.GetBytes('{"status":"UP"}') } }
Assert (Test-SystemHttp 'stock-user-service') 'Binary JSON health response must be decoded as UTF-8'
function Invoke-WebRequest { return [pscustomobject]@{ StatusCode=200; Content=[Text.Encoding]::UTF8.GetBytes('<html><body>login</body></html>') } }
Assert (Test-SystemHttp 'frontend') 'Binary HTML response must be decoded as UTF-8'
Remove-Item Function:Invoke-WebRequest
'PASS: binary HTTP health and frontend response'
$timedOut = $false
$timer = [Diagnostics.Stopwatch]::StartNew()
try { $null = Invoke-BoundedProcess -FileName (Get-Process -Id $PID).Path -Arguments @('-NoProfile','-Command','Start-Sleep -Seconds 20') -TimeoutMilliseconds 300 } catch { $timedOut = $_.Exception.Message -like '*timed out*' }
Assert $timedOut 'Hung child process must time out'
Assert ($timer.Elapsed.TotalSeconds -lt 5) 'Timeout must terminate the child without waiting for its sleep'
$nonzero = $false
try { $null = Invoke-BoundedProcess -FileName (Get-Process -Id $PID).Path -Arguments @('-NoProfile','-Command','exit 4') } catch { $nonzero = $true }
Assert $nonzero 'Nonzero native exit must propagate'
'PASS: hung CLI timeout and nonzero exit'


Assert (Test-FutuCollectorCommand 'C:\Python\python.exe -u scripts/futu-live-quote.py') 'Existing relative collector command must be recognized'
Assert (Test-FutuCollectorCommand '"C:\Python\python.exe" -u "E:\aiStock\Project Files\scripts\futu-live-quote.py"') 'Absolute collector command must be recognized'
Assert (-not (Test-FutuCollectorCommand 'python.exe scripts/futu-live-quote.py.bak')) 'A different script must not count as the collector'
Assert (-not (Test-FutuCollectorCommand $null)) 'A missing process command must not count as the collector'
'PASS: collector process identity'
