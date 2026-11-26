#requires -Version 5.1
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Run init-local-env.ps1 first' }
$smtpAddress = (Read-Host '输入发件 QQ 邮箱').Trim()
if ($smtpAddress -notmatch '^[0-9]{5,12}@qq\.com$') { throw '请输入有效的 QQ 邮箱；未保存' }
$secret = Read-Host '粘贴QQ邮箱SMTP授权码（输入隐藏，不是QQ密码）' -AsSecureString
$pointer = [IntPtr]::Zero
$plain = $null
try {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer).Trim()
    if ($plain -notmatch '^[A-Za-z0-9]{16}$') { throw '授权码应为16位字母或数字，请重新运行；未保存' }
    $values = [ordered]@{
        SMTP_HOST='smtp.qq.com'; SMTP_PORT='465'; SMTP_USERNAME=$smtpAddress;
        SMTP_PASSWORD=$plain; SMTP_FROM=$smtpAddress; SMTP_SSL_ENABLED='true'; SMTP_STARTTLS_REQUIRED='false'
    }
    $lines = [Collections.Generic.List[string]]::new()
    foreach ($line in [IO.File]::ReadAllLines($envFile)) {
        $key = ($line -split '=',2)[0]
        if (-not $values.Contains($key)) { $lines.Add($line) }
    }
    foreach ($key in $values.Keys) { $lines.Add("$key=$($values[$key])") }
    [IO.File]::WriteAllLines($envFile, $lines)
    Write-Output 'SMTP配置已保存到本机忽略文件，授权码未显示。请告诉我“配置好了”。'
} finally {
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    if ($secret) { $secret.Dispose() }
    $plain = $null
}
