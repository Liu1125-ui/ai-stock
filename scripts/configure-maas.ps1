#requires -Version 5.1
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Local .env is required' }
$endpoint = (Read-Host '粘贴华为云完整Chat API调用地址（不是控制台网址）').Trim()
$uri = $null
if (-not [Uri]::TryCreate($endpoint,[UriKind]::Absolute,[ref]$uri) -or $uri.Scheme -ne 'https' -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or $uri.Port -ne 443 -or ($uri.Host -ne 'api.modelarts-maas.com' -and $uri.Host -notmatch '\.(modelarts-maas\.com|myhuaweicloud\.com)$')) { throw '需要华为云HTTPS API地址，无用户信息、查询参数或片段；未保存' }
$model = (Read-Host '粘贴控制台示例中的model名称（按原样）').Trim()
if ($model -notmatch '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$') { throw '模型名称格式不正确；未保存' }
$secret = Read-Host '粘贴MaaS API Key（输入隐藏）' -AsSecureString
$pointer = [IntPtr]::Zero
$plain = $null
try {
 $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
 $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer).Trim()
 if ($plain -notmatch '^[A-Za-z0-9._/-]{8,512}$') { throw 'Key格式不符合单行配置要求；未保存，不显示Key' }
 $values = [ordered]@{MAAS_CHAT_URL=$uri.AbsoluteUri;MAAS_MODEL=$model;MAAS_API_KEY=$plain;MAAS_ENABLED='true'}
 $lines = [Collections.Generic.List[string]]::new()
 foreach ($line in [IO.File]::ReadAllLines($envFile)) { $key=($line -split '=',2)[0];if(-not $values.Contains($key)){$lines.Add($line)} }
 foreach ($key in $values.Keys) {$lines.Add("$key=$($values[$key])")}
 [IO.File]::WriteAllLines($envFile,$lines,[Text.UTF8Encoding]::new($false))
 Write-Output 'MaaS配置已保存到本机忽略文件，Key未显示。请告诉我“配置好了”。'
} finally {
 if ($pointer -ne [IntPtr]::Zero) {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)}
 if ($secret) {$secret.Dispose()}
 $plain=$null
}
