package com.aistock.scheduler.task;
import com.fasterxml.jackson.databind.*;
import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;
import static com.aistock.scheduler.task.ComparisonTaskRecords.*;
public final class HttpComparisonTaskClient implements ComparisonTaskClient {
 static final String DEFINITION="baostock-comparison-backward-close-v1";
 private final ComparisonTaskHttp http;private final ObjectMapper mapper;private final Clock clock;private final Duration executeTimeout,readTimeout,optionsTimeout;
 public HttpComparisonTaskClient(String base,String token,ObjectMapper mapper,Clock clock){this(base,token,mapper,clock,Duration.ofSeconds(45),Duration.ofSeconds(3),Duration.ofSeconds(5));}
 HttpComparisonTaskClient(String base,String token,ObjectMapper mapper,Clock clock,Duration execute,Duration read,Duration options){
  this.http=new ComparisonTaskHttp(base,token,mapper);this.mapper=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  this.clock=clock;this.executeTimeout=execute;this.readTimeout=read;this.optionsTimeout=options;
 }
 public Options options(List<String> ids){
  validateIds(ids);
  try{
   var n=data(http.call("/internal/v1/market/comparison-update-options?ids="+String.join(",",ids),null,optionsTimeout));
   var result=mapper.treeToValue(n,Options.class);validateOptions(result,ids,clock.instant());return result;
  }catch(ComparisonTaskHttp.Remote e){throw e;}catch(Exception e){throw ComparisonTaskHttp.invalid();}
 }
 static void validateIds(List<String> ids){
  if(ids==null||ids.isEmpty()||ids.size()>50||new HashSet<>(ids).size()!=ids.size()||ids.stream().anyMatch(id->id==null||!id.matches("(SH|SZ)[0-9]{6}")))throw ComparisonTaskHttp.invalid();
 }
 static void validateOptions(Options result,List<String> ids,Instant now){
  validateIds(ids);
  if(result==null||result.asOf()==null||!result.asOf().equals(result.asOf().truncatedTo(ChronoUnit.MILLIS))||result.asOf().isBefore(now.minusSeconds(5))
   ||result.asOf().isAfter(now.plusSeconds(5))||!DEFINITION.equals(result.definition())||result.items().size()!=ids.size())throw ComparisonTaskHttp.invalid();
  var seen=new HashSet<String>();var local=result.asOf().atZone(ZoneId.of("Asia/Shanghai"));var cutoff=local.toLocalDate().minusDays(local.getHour()<20?1:0);
  for(var i:result.items()){
   if(i==null||!ids.contains(i.instrumentId())||!seen.add(i.instrumentId())||i.state()==null
    ||!Set.of("READY","CURRENT","NO_RAW_VERSION","RAW_LAGGING","CALENDAR_UNAVAILABLE","INSUFFICIENT_HISTORY","SOURCE_INCOMPATIBLE").contains(i.state()))throw ComparisonTaskHttp.invalid();
   if(Set.of("READY","CURRENT").contains(i.state())){
    if(i.rawVersionId()==null||i.rawContentHash()==null||!i.rawContentHash().matches("[a-f0-9]{64}")||i.rangeStart()==null||i.targetDate()==null
     ||!i.rangeStart().equals(i.targetDate().minusYears(1))||i.rangeStart().isBefore(LocalDate.of(1990,1,1))||i.targetDate().isAfter(cutoff)||i.reason()!=null)throw ComparisonTaskHttp.invalid();
    if("CURRENT".equals(i.state())&&(i.evidenceId()==null||!i.targetDate().equals(i.sourceTo())))throw ComparisonTaskHttp.invalid();
   }else if(i.reason()==null||!i.reason().matches("[A-Z][A-Z0-9_]{0,63}"))throw ComparisonTaskHttp.invalid();
  }
 }
 public MarketRefreshClient.Result execute(TaskRepository.Claim c,Context context){
  if(!"COMPARISON_UPDATE".equals(c.taskType())||!c.taskId().equals(context.taskId())||!c.instrumentId().equals(context.snapshot().instrumentId())
   ||!c.targetDate().equals(context.snapshot().end())||!uuid(c.attemptId()))throw ComparisonTaskHttp.invalid();
  var s=context.snapshot();var body=Map.of("operationId",c.attemptId(),"instrumentId",s.instrumentId(),"rawVersionId",s.rawVersionId(),
   "rawContentHash",s.rawContentHash(),"start",s.start(),"end",s.end(),"definition",s.definition(),"leaseUntil",c.leaseUntil());
  try{return result(data(http.call("/internal/v1/market/comparison-refresh",body,executeTimeout)),c);}
  catch(ComparisonTaskHttp.Remote e){
   if(MarketUseTaskErrors.definitive(e.status,e.code))return new MarketRefreshClient.Result(c.attemptId(),"FAILED",null,e.code);
   if((e.status==401||e.status==403))return new MarketRefreshClient.Result(c.attemptId(),"FAILED",null,"COMPARISON_ACCESS_DENIED");
   if(e.status>=400&&e.status<500&&e.status!=409&&Set.of("COMPARISON_REQUEST_INVALID","COMPARISON_RAW_INCOMPATIBLE",
     "COMPARISON_CALENDAR_UNAVAILABLE","COMPARISON_RAW_UNAVAILABLE","RESOURCE_NOT_FOUND").contains(e.code))
    return new MarketRefreshClient.Result(c.attemptId(),"FAILED",null,e.code);
   throw e;
  }
 }
 public MarketRefreshClient.Result receipt(TaskRepository.Claim c){
  if(!uuid(c.attemptId()))throw ComparisonTaskHttp.invalid();
  try{return result(data(http.call("/internal/v1/market/comparison-refresh/"+c.attemptId(),null,readTimeout)),c);}
  catch(ComparisonTaskHttp.Remote e){if(e.status==404&&e.code.equals("COMPARISON_REFRESH_NOT_FOUND"))throw new MissingReceipt();throw e;}
 }
 private static MarketRefreshClient.Result result(JsonNode n,TaskRepository.Claim c){
  if(!fields(n).equals(Set.of("operationId","state","versionId","errorCode"))||!n.path("operationId").isTextual()||!n.path("state").isTextual()
    ||!c.attemptId().equals(n.path("operationId").textValue()))throw ComparisonTaskHttp.invalid();
  String state=n.path("state").textValue();
  var version=n.path("versionId");var error=n.path("errorCode");
  if("AVAILABLE".equals(state)){if(!version.isTextual()||!uuid(version.textValue())||!error.isNull())throw ComparisonTaskHttp.invalid();}
  else if("FAILED".equals(state)){if(!version.isNull()||!error.isTextual()||!error.textValue().matches("[A-Z][A-Z0-9_]{0,63}"))throw ComparisonTaskHttp.invalid();}
  else if("RUNNING".equals(state)){if(!version.isNull()||!error.isNull())throw ComparisonTaskHttp.invalid();}
  else throw ComparisonTaskHttp.invalid();
  return new MarketRefreshClient.Result(c.attemptId(),state,version.isNull()?null:version.textValue(),error.isNull()?null:error.textValue());
 }
 private static JsonNode data(JsonNode n){
  if(!fields(n).equals(Set.of("code","message","data","traceId"))||!"OK".equals(n.path("code").asText())||!n.path("data").isObject())throw ComparisonTaskHttp.invalid();
  return n.path("data");
 }
 private static Set<String> fields(JsonNode n){var fields=new HashSet<String>();n.fieldNames().forEachRemaining(fields::add);return fields;}
 private static boolean uuid(String s){return s!=null&&s.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}");}
}
