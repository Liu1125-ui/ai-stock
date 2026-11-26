#requires -Version 7.0
[CmdletBinding()]
param([switch]$NoBrowser, [ValidateRange(30,1800)][int]$TimeoutSeconds = 600)

function Test-ContainerIdentity($Project, $Service, $Expected) {
    return ($Project -eq 'aistock-sdd001' -and $Service -eq $Expected)
}

function Invoke-StartupSequence {
    param([scriptblock]$Start, [scriptblock]$Wait)
    $groups = @(
        @('mysql','nacos','algorithm'),
        @('stock-user-service','stock-market-service','stock-prediction-service','stock-scheduler-service','stock-notification-service'),
        @('stock-gateway'), @('frontend')
    )
    foreach ($group in $groups) {
        foreach ($service in $group) { & $Start $service }
        foreach ($service in $group) { & $Wait $service }
    }
}

function Invoke-BoundedProcess {
    param([string]$FileName, [string[]]$Arguments, [int]$TimeoutMilliseconds = 30000)
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $FileName
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    try {
        $null = $process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutMilliseconds)) {
            $process.Kill($true)
            throw 'Command timed out; startup stopped. Check Docker Desktop and retry.'
        }
        $output = $stdout.GetAwaiter().GetResult()
        $null = $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw "Command failed: $($Arguments[0]). Check Docker Desktop and retry." }
        return $output.Trim()
    } finally { $process.Dispose() }
}

function Invoke-DockerCommand {
    param([string[]]$Arguments)
    return (Invoke-BoundedProcess -FileName (Get-Command docker -ErrorAction Stop).Source -Arguments $Arguments)
}

function Get-SystemContainer($Service) {
    $name = "aistock-sdd001-$Service-1"
    $raw = Invoke-DockerCommand @('inspect','--format','{{json .State}}|{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}', $name)
    $parts = $raw -split '\|'
    if ($parts.Count -ne 3 -or -not (Test-ContainerIdentity $parts[1] $parts[2] $Service)) {
        throw "Container identity mismatch: $name. Startup stopped."
    }
    return ($parts[0] | ConvertFrom-Json)
}

function Test-SystemHttp($Service) {
    $ports = @{'algorithm'=18090; 'stock-user-service'=18081; 'stock-market-service'=18082; 'stock-prediction-service'=18083; 'stock-scheduler-service'=18084; 'stock-notification-service'=18085; 'stock-gateway'=18080; 'frontend'=15173}
    $path = if ($Service -eq 'frontend') { '/login' } elseif ($Service -eq 'algorithm') { '/health' } else { '/actuator/health' }
    try {
        $response = Invoke-WebRequest "http://127.0.0.1:$($ports[$Service])$path" -TimeoutSec 3 -SkipHttpErrorCheck
        if ($response.StatusCode -ne 200) { return $false }
        $content = if ($response.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($response.Content) } else { [string]$response.Content }
        if ($Service -eq 'frontend') { return ($content -match '<html') }
        $body = $content | ConvertFrom-Json
        return ($body.status -in @('UP','ok','healthy'))
    } catch { return $false }
}

function Test-FutuCollectorCommand([string]$CommandLine) {
    return $CommandLine -match '(?:^|[\s"/\\])scripts[/\\]futu-live-quote\.py(?:"|\s|$)'
}

function Start-LocalFutuCollector([string]$ProjectRoot) {
    $envPath = Join-Path $ProjectRoot '.env'
    if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) { return }
    $entries = @(Get-Content -LiteralPath $envPath | Where-Object { $_ -match '^\s*AISTOCK_MARKET_LIVE_PILOT_TOKEN\s*=' })
    if ($entries.Count -eq 0) { return }
    if ($entries.Count -ne 1) { throw '行情采集配置重复，请检查本机 .env。' }
    $quoteToken = ($entries[0] -split '=', 2)[1].Trim()
    if (-not $quoteToken) { return }
    if ($quoteToken -notmatch '^[A-Za-z0-9_-]{32,128}$') { throw '本机行情采集令牌格式无效。' }

    $runtime = Join-Path $ProjectRoot 'runtime'
    $sdk = Join-Path $runtime 'futu-live-sdk'
    $python = Join-Path $ProjectRoot '../../.venv-model/Scripts/python.exe'
    if (-not (Test-Path -LiteralPath $python -PathType Leaf) -or
        -not (Test-Path -LiteralPath (Join-Path $sdk 'futu/__init__.py') -PathType Leaf)) {
        throw '未找到本机行情采集使用的 Python 或富途 SDK。'
    }

    $projectKey = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData(
        [Text.Encoding]::UTF8.GetBytes($ProjectRoot.ToLowerInvariant())))
    $startupLock = [Threading.Mutex]::new($false, "AiStockFutuCollector-$projectKey")
    $lockHeld = $false
    try {
        try { $lockHeld = $startupLock.WaitOne([TimeSpan]::FromSeconds(5)) }
        catch [Threading.AbandonedMutexException] { $lockHeld = $true }
        if (-not $lockHeld) { throw '另一个启动过程正在检查行情采集器。' }
        $pidFile = Join-Path $runtime 'futu-live-collector.pid'
        $trackedId = 0
        if ((Test-Path -LiteralPath $pidFile -PathType Leaf) -and
            [int]::TryParse((Get-Content -LiteralPath $pidFile -Raw).Trim(), [ref]$trackedId) -and $trackedId -gt 0) {
            $tracked = Get-CimInstance Win32_Process -Filter "ProcessId = $trackedId" -ErrorAction Stop
            if ($tracked -and (Test-FutuCollectorCommand $tracked.CommandLine)) {
                Write-Host '富途行情采集器已在运行。'
                return
            }
        }

        $oldToken = $env:AISTOCK_MARKET_LIVE_PILOT_TOKEN
        $oldPythonPath = $env:PYTHONPATH
        try {
            $env:AISTOCK_MARKET_LIVE_PILOT_TOKEN = $quoteToken
            $env:PYTHONPATH = $sdk
            $options = @{
                FilePath = $python
                ArgumentList = @('-u', 'scripts/futu-live-quote.py')
                WorkingDirectory = $ProjectRoot
                WindowStyle = 'Hidden'
                PassThru = $true
                RedirectStandardOutput = Join-Path $runtime 'futu-live-collector.log'
                RedirectStandardError = Join-Path $runtime 'futu-live-collector-error.log'
            }
            $collector = Start-Process @options
            [IO.File]::WriteAllText($pidFile, [string]$collector.Id)
            if ($collector.WaitForExit(1200)) { throw '采集器启动后立即退出，请检查运行日志。' }
            Write-Host '富途行情采集器已启动，等待 OpenD 行情。'
        } finally {
            $env:AISTOCK_MARKET_LIVE_PILOT_TOKEN = $oldToken
            $env:PYTHONPATH = $oldPythonPath
        }
    } finally {
        if ($lockHeld) { $startupLock.ReleaseMutex() }
        $startupLock.Dispose()
    }
}

function Start-StockSystem {
    $ErrorActionPreference = 'Stop'
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker CLI not found. Install Docker Desktop first.' }
    $engineReady = $false
    try { $null = Invoke-DockerCommand @('info','--format','{{.ServerVersion}}'); $engineReady = $true } catch { }
    if (-not $engineReady) {
        $desktop = Join-Path $env:ProgramFiles 'Docker/Docker/Docker Desktop.exe'
        if (-not (Test-Path -LiteralPath $desktop)) { throw 'Docker Desktop not found. Start Docker Desktop manually.' }
        Write-Host 'Starting Docker Desktop...'
        Start-Process -FilePath $desktop -WindowStyle Hidden
        $dockerDeadline = [DateTime]::UtcNow.AddSeconds(120)
        do {
            Start-Sleep -Seconds 3
            try { $null = Invoke-DockerCommand @('info','--format','{{.ServerVersion}}'); $engineReady = $true; break } catch { }
        } while ([DateTime]::UtcNow -lt $dockerDeadline)
        if (-not $engineReady) { throw 'Docker engine did not become ready within 120 seconds. Check Docker Desktop; do not reset its data.' }
    }
    $services = @('mysql','nacos','algorithm','stock-user-service','stock-market-service','stock-prediction-service','stock-scheduler-service','stock-notification-service','stock-gateway','frontend')
    foreach ($service in $services) {
        try { $null = Get-SystemContainer $service } catch { throw "Original container missing or invalid: $service. Use the existing deployment environment; this launcher will not create an empty database. $($_.Exception.Message)" }
    }
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $start = {
        param($service)
        $state = Get-SystemContainer $service
        if (-not $state.Running) {
            Write-Host "Starting $service..."
            $null = Invoke-DockerCommand @('start',"aistock-sdd001-$service-1")
        }
    }
    $wait = {
        param($service)
        $restarted = $false
        while ([DateTime]::UtcNow -lt $deadline) {
            $state = Get-SystemContainer $service
            if (-not $state.Running) {
                if ($restarted -or $service -in @('mysql','nacos')) { throw "Service exited: $service. Inspect its Docker logs. Existing data is retained." }
                $restarted = $true
                $null = Invoke-DockerCommand @('start',"aistock-sdd001-$service-1")
            }
            $ready = $false
            if ($service -in @('mysql','nacos')) {
                $ready = ($state.Health.Status -eq 'healthy')
                if ($ready -and $service -eq 'nacos') {
                    try { $null = Invoke-DockerCommand @('exec','aistock-sdd001-nacos-1','bash','-c','exec 3<>/dev/tcp/127.0.0.1/9848'); $ready = $true } catch { $ready = $false }
                }
            } elseif ($state.Running) { $ready = Test-SystemHttp $service }
            if ($ready) { Write-Host "Ready: $service"; return }
            Start-Sleep -Seconds 2
        }
        throw "Startup timed out waiting for $service. Inspect Docker logs and rerun this launcher. Existing data is retained."
    }
    Invoke-StartupSequence -Start $start -Wait $wait
    try { Start-LocalFutuCollector (Resolve-Path (Join-Path $PSScriptRoot '..')).Path }
    catch { Write-Warning ("实时行情采集器暂未启动。"+$_.Exception.Message) }
    $url = 'http://127.0.0.1:15173/'
    Write-Host "System ready: $url"
    if (-not $NoBrowser) { Start-Process $url }
}

if ($MyInvocation.InvocationName -ne '.') {
    try { Start-StockSystem } catch { Write-Host "STARTUP FAILED: $($_.Exception.Message)" -ForegroundColor Red; exit 1 }
}
