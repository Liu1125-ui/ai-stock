#requires -Version 7.0
param([string]$FixtureDirectory='runtime/sdd007a/live/acceptance-20261012',[string]$OutputDirectory='runtime/sdd007b/audit')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$fixture=(Resolve-Path (Join-Path $root $FixtureDirectory)).Path
$output=[IO.Path]::GetFullPath((Join-Path $root $OutputDirectory))
$runtime=[IO.Path]::GetFullPath((Join-Path $root 'runtime'))+[IO.Path]::DirectorySeparatorChar
if(-not $output.StartsWith($runtime,[StringComparison]::OrdinalIgnoreCase)){throw 'Audit output must remain inside project runtime'}
New-Item -ItemType Directory -Force $output | Out-Null
$jar=Join-Path $root 'backend/stock-prediction-service/target/stock-prediction-service-0.1.0-SNAPSHOT.jar'
if(-not (Test-Path -LiteralPath $jar)){throw 'Build backend before auditing'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[IO.Compression.ZipFile]::OpenRead($jar)
$libs=@()
try {
 foreach($entry in $archive.Entries){
  if($entry.FullName -match '^BOOT-INF/lib/(jackson[^/]*|spring-web-[^/]*)[.]jar$'){
   $target=Join-Path $output $entry.Name
   [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$target,$true)
   $libs+=$target
  }
 }
}finally{$archive.Dispose()}
$classpath=(@((Join-Path $root 'backend/stock-prediction-service/target/maven-classes'),(Join-Path $root 'backend/stock-common/target/maven-classes'))+$libs) -join [IO.Path]::PathSeparator
$java=@"
import com.aistock.prediction.comparison.*;
import static com.aistock.prediction.comparison.ComparisonRecords.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;import java.time.*;import java.util.*;
public class ComparisonSnapshotAudit {
 public static void main(String[] args)throws Exception {
  var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  var sources=new ArrayList<JsonNode>();
  for(String id:List.of("SH600030","SH600276","SZ000002"))sources.add(mapper.readTree(Path.of(args[0],"evidence-"+id+".json").toFile()));
  var end=sources.stream().map(n->LocalDate.parse(n.path("to").asText())).min(LocalDate::compareTo).orElseThrow();
  for(String period:List.of("3M","6M","1Y")){
   var from=period.equals("1Y")?end.minusYears(1):end.minusMonths(period.equals("3M")?3:6);
   var input=new Input(UUID.randomUUID(),sources.stream().map(n->n.path("instrumentId").asText()).toList(),from,end,sources.stream().map(n->UUID.fromString(n.path("evidenceId").asText())).toList());
   var calculated=new ComparisonCalculator().calculate(input,sources);
   mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[1],"calculation-"+period+".json").toFile(),calculated);
  }
 }
}
"@
$javaFile=Join-Path $output 'ComparisonSnapshotAudit.java'
[IO.File]::WriteAllText($javaFile,$java,[Text.UTF8Encoding]::new($false))
& javac -encoding UTF-8 -cp $classpath -d $output $javaFile
if($LASTEXITCODE -ne 0){throw 'Audit compiler failed'}
& java -cp ($output+[IO.Path]::PathSeparator+$classpath) ComparisonSnapshotAudit $fixture $output
if($LASTEXITCODE -ne 0){throw 'Actual comparison calculator rejected evidence'}
& python (Join-Path $PSScriptRoot 'verify-comparison-metrics.py') $fixture $output
if($LASTEXITCODE -ne 0){throw 'Independent metrics audit failed'}
Write-Output "Offline real-evidence audit saved in $output; no external API or database writes."
