package com.aistock.scheduler.alert;

import static com.aistock.scheduler.alert.AlertMarketClient.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;

public final class HttpAlertMarketClient implements AlertMarketClient {
 private final String market,notification;private final AlertTaskHttp http;private final Clock clock;
 public HttpAlertMarketClient(String market,String notification,String token,ObjectMapper mapper,Clock clock){this.market=market;this.notification=notification;this.http=new AlertTaskHttp(token,mapper);this.clock=clock;}
 public CalendarSnapshot calendar(){var today=clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();return parseCalendar(data(http.call(market,"/internal/v1/market/calendar?start="+today.minusDays(366)+"&end="+today,null,Duration.ofSeconds(10))));}
 public CalendarSnapshot refresh(UUID operation,LocalDate start,LocalDate end){
  if(operation==null||start==null||end==null||end.isBefore(start))throw invalid();
  var c=parseCalendar(data(http.call(market,"/internal/v1/market/calendar/refresh",Map.of("operationId",operation,"start",start,"end",end),Duration.ofSeconds(30))));
  if(!start.equals(c.start())||!end.equals(c.end()))throw invalid();return c;
 }
 public Boundary boundary(String instrument){
  if(!AlertScanRepository.stock(instrument))throw invalid();var n=data(http.call(market,"/internal/v1/market/alert-boundary/"+instrument,null,Duration.ofSeconds(10)));
  if(!instrument.equals(text(n,"instrumentId"))||!"baostock".equals(text(n,"provider"))||!"alert-boundary-v1".equals(text(n,"definition"))||!text(n,"calendarHash").matches("[a-f0-9]{64}"))throw invalid();
  var latest=date(n,"latestPublishedTradeDate");var expected=date(n,"expectedCompletedTradeDate");var effective=date(n,"effectiveAfterTradeDate");
  var today=clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
  if(latest.isAfter(today)||expected.isAfter(today)||!effective.equals(latest.isAfter(expected)?latest:expected))throw invalid();
  var asOf=instant(n,"asOf");if(asOf.isBefore(clock.instant().minusSeconds(5))||asOf.isAfter(clock.instant().plusSeconds(5)))throw invalid();
  return new Boundary(instrument,uuid(n,"rawVersionId"),latest,expected,asOf);
 }
 public Page due(String instrument,String after,int limit){
  if(instrument!=null&&!AlertScanRepository.stock(instrument)||after!=null&&!AlertScanRepository.uuid(after)||limit<1||limit>50)throw invalid();
  String path="/internal/v1/alerts/due?limit="+limit+(instrument==null?"":"&instrumentId="+instrument)+(after==null?"":"&after="+after);
  var n=data(http.call(notification,path,null,Duration.ofSeconds(10)));var items=n.path("items");
  if(!items.isArray()||items.size()>limit||!n.has("nextCursor"))throw invalid();var result=new ArrayList<RuleRef>();String previous=after==null?"":after;
  for(var r:items){
   UUID id=uuid(r,"ruleId"),owner=uuid(r,"ownerId");String stock=text(r,"instrumentId");var revision=r.path("revision");var effective=date(r,"effectiveAfterTradeDate");
   if(id.toString().compareTo(previous)<=0||!AlertScanRepository.stock(stock)||instrument!=null&&!instrument.equals(stock)||!revision.isIntegralNumber()||!revision.canConvertToLong()||revision.longValue()<0||effective.isAfter(clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()))throw invalid();
   result.add(new RuleRef(id,owner,stock,revision.longValue(),effective));previous=id.toString();
  }
  String next=n.path("nextCursor").isNull()?null:text(n,"nextCursor");if(next!=null&&(!AlertScanRepository.uuid(next)||result.isEmpty()||!previous.equals(next)))throw invalid();return new Page(result,next);
 }
 private CalendarSnapshot parseCalendar(JsonNode n){
  var version=uuid(n,"versionId");var c=n.path("candidate");if(!c.isObject())throw invalid();
  String provider=text(c,"provider"),hash=text(c,"sourcePayloadHash");if(!"baostock".equals(provider)||!hash.matches("[a-f0-9]{64}"))throw invalid();
  var list=c.path("days");if(!list.isArray()||list.size()>400)throw invalid();var days=new ArrayList<Day>();
  for(var d:list){if(!d.path("open").isBoolean())throw invalid();days.add(new Day(date(d,"date"),d.path("open").booleanValue()));}
  return new CalendarSnapshot(version,provider,date(c,"start"),date(c,"end"),instant(c,"fetchedAt"),hash,days);
 }
 private JsonNode data(JsonNode n){if(!"OK".equals(n.path("code").asText())||!n.path("data").isObject())throw invalid();return n.path("data");}
 private static String text(JsonNode n,String name){var v=n.path(name);if(!v.isTextual())throw invalid();return v.textValue();}
 private static UUID uuid(JsonNode n,String name){String value=text(n,name);if(!AlertScanRepository.uuid(value))throw invalid();return UUID.fromString(value);}
 private static LocalDate date(JsonNode n,String name){try{String value=text(n,name);if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid();var d=LocalDate.parse(value);if(d.isBefore(LocalDate.of(1990,1,1))||d.isAfter(LocalDate.of(2200,1,1)))throw invalid();return d;}catch(java.time.DateTimeException e){throw invalid();}}
 private static Instant instant(JsonNode n,String name){try{return Instant.parse(text(n,name));}catch(java.time.DateTimeException e){throw invalid();}}
 private static AlertTaskHttp.Remote invalid(){return AlertTaskHttp.error("ALERT_SCAN_SOURCE_INVALID");}
}
