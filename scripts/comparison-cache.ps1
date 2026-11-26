# Local cache coordination. No credentials, network calls or business database writes.
function Enter-ComparisonCacheLock([string]$directory){
 try{return [IO.File]::Open((Join-Path $directory 'execution.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)}
 catch{throw 'This comparison cache is already in use; no source request started'}
}
function Save-ComparisonCacheJson([string]$directory,[string]$name,[object]$value){
 $target=Join-Path $directory $name
 $temporary=$target+'.'+[guid]::NewGuid().ToString('N')+'.tmp'
 try{
  [IO.File]::WriteAllText($temporary,($value | ConvertTo-Json -Depth 20),[Text.UTF8Encoding]::new($false))
  [IO.File]::Move($temporary,$target,$true)
 }finally{
  if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary}
 }
}
function Begin-ComparisonSourceAttempt([string]$directory,[string]$instrument){
 $path=Join-Path $directory ("attempt-$instrument.json")
 try{$file=[IO.File]::Open($path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)}
 catch{throw 'A source attempt for this request already exists; no automatic retry'}
 try{
  $bytes=[Text.Encoding]::UTF8.GetBytes('{"state":"ATTEMPT_RESERVED"}')
  $file.Write($bytes,0,$bytes.Length)
 }finally{$file.Dispose()}
}