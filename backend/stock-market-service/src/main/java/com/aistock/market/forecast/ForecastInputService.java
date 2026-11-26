package com.aistock.market.forecast;
import com.aistock.market.version.MarketVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
@Service
public class ForecastInputService {
 public record Publication(String inputVersionId,String inputHash,boolean replayed){}
 public record InputBar(String date,String open,String high,String low,String close,String volumeShares,String amountYuan,String sourceAdjustmentRatio){}
 public record Window(String instrumentId,String inputVersionId,String rawVersionId,String rawContentHash,String inputHash,String calendarHash,String definition,String provider,String priceBasis,String originDate,String originRawClose,String capturedAt,String freshness,List<InputBar> bars,List<String> targetDates){}
 private final MarketVersionRepository market;private final ForecastInputRepository repository;private final ForecastInputValidator validator;private final Clock clock;private final int updateHour;
 private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 public ForecastInputService(MarketVersionRepository market,Clock clock,@Value("${aistock.market.update-hour:20}")int updateHour){if(updateHour<0||updateHour>23)throw new IllegalArgumentException("Invalid update hour");this.market=market;this.repository=new ForecastInputRepository(market);this.validator=new ForecastInputValidator(clock);this.clock=clock;this.updateHour=updateHour;}
 public record RawWindow(String instrumentId,String state,String versionId,String provider,String priceBasis,String rawContentHash,String freshness,List<InputBar> bars){}
 public RawWindow rawWindow(String instrument){return com.aistock.market.marketlifecycle.MarketEvidenceGate.local(()->{
  if(instrument==null || !instrument.matches("(SH|SZ)[0-9]{6}"))throw ForecastInputValidator.error("FORECAST_REQUEST_INVALID");
  var raw=repository.rawHead(instrument);var id=(String)raw.get("version_id");var all=market.bars(id);
  if(all.size()<60)throw ForecastInputValidator.error("FORECAST_WINDOW_INCOMPLETE");
  String fresh=freshness(all.get(all.size()-1).date(),market.calendar(id));
  if("STALE".equals(fresh))throw ForecastInputValidator.error("FORECAST_INPUT_STALE");
  var bars=all.subList(all.size()-60,all.size()).stream().map(b->new InputBar(b.date().toString(),decimal(b.open()),decimal(b.high()),decimal(b.low()),decimal(b.close()),decimal(b.volumeShares()),decimal(b.amountYuan()),null)).toList();
  return new RawWindow(instrument,"AVAILABLE",id,"baostock","RAW",(String)raw.get("content_hash"),fresh,bars);
 });}
 public Publication publish(ForecastCandidate c){
  validator.validate(c);String requestHash=hash(c);
  var points=c.bars().stream().map(p->{var b=p.raw();return List.of(b.date().toString(),decimal(b.open()),decimal(b.high()),decimal(b.low()),decimal(b.close()),decimal(b.volumeShares()),decimal(b.amountYuan()),b.tradeStatus(),decimal(p.backwardClose()));}).toList();
  var days=c.calendar().stream().map(d->List.of(d.date().toString(),d.open())).toList();
  return repository.publish(c,requestHash,hash(List.of(c.rawVersionId(),c.instrumentId(),c.provider(),c.definition(),points,days)),hash(days),validator,clock);
 }
 public Window window(String instrument,String inputId,int length,int horizon){return com.aistock.market.marketlifecycle.MarketEvidenceGate.local(()->{
  if(instrument==null || !instrument.matches("(SH|SZ)[0-9]{6}") || inputId!=null && !ForecastInputValidator.uuid(inputId) || length!=60 || horizon!=5)throw ForecastInputValidator.error("FORECAST_REQUEST_INVALID");
  var version=repository.find(instrument,inputId);String id=(String)version.get("input_version_id"),rawId=(String)version.get("raw_version_id");
  var raw=repository.raw(rawId,false);repository.compatible(raw,instrument);
  var all=market.bars(rawId);var ratios=repository.adjustments(id);if(all.size()<60 || ratios.size()!=60)throw ForecastInputValidator.error("FORECAST_WINDOW_INCOMPLETE");
  var bars=all.subList(all.size()-60,all.size());var input=new ArrayList<InputBar>();
  for(int i=0;i<60;i++){var b=bars.get(i);var p=ratios.get(i);if(!b.date().equals(((java.sql.Date)p.get("trade_date")).toLocalDate()))throw ForecastInputValidator.error("FORECAST_RAW_MISMATCH");
   input.add(new InputBar(b.date().toString(),decimal(b.open()),decimal(b.high()),decimal(b.low()),decimal(b.close()),decimal(b.volumeShares()),decimal(b.amountYuan()),decimal((BigDecimal)p.get("source_adjustment_ratio"))));}
  var origin=bars.get(59);var calendar=repository.calendar(id);var target=calendar.stream().filter(d->d.open()&&d.date().isAfter(origin.date())).limit(5).map(d->d.date().toString()).toList();
  if(target.size()!=5)throw ForecastInputValidator.error("FORECAST_CALENDAR_INVALID");String fresh=freshness(origin.date(),calendar);
  if(inputId==null && !"FRESH".equals(fresh))throw ForecastInputValidator.error("FORECAST_INPUT_"+("STALE".equals(fresh)?"STALE":"FRESHNESS_UNKNOWN"));
  return new Window(instrument,id,rawId,(String)raw.get("content_hash"),(String)version.get("content_hash"),(String)version.get("calendar_hash"),(String)version.get("definition_code"),"baostock","RAW",origin.date().toString(),decimal(origin.close()),((LocalDateTime)version.get("fetched_at")).toInstant(ZoneOffset.UTC).toString(),fresh,List.copyOf(input),target);
 });}
 public String readiness(String instrument){
  try{
   window(instrument,null,60,5);
   return "READY";
  }catch(com.aistock.common.BusinessException problem){
   if(!"FORECAST_INPUT_NOT_AVAILABLE".equals(problem.code()))return readinessError(problem.code());
  }
  try{
   var raw=rawWindow(instrument);
   if(raw.bars().stream().anyMatch(b->new BigDecimal(b.volumeShares()).signum()<=0
     ||new BigDecimal(b.amountYuan()).signum()<=0))return "INSUFFICIENT_HISTORY";
   return "CHECK_ON_SUBMIT";
  }catch(com.aistock.common.BusinessException problem){
   return readinessError(problem.code());
  }
 }
 private String readinessError(String code){
  return switch(code){
   case "RESOURCE_NOT_FOUND" -> "NO_DATA";
   case "FORECAST_WINDOW_INCOMPLETE","FORECAST_INPUT_INVALID" -> "INSUFFICIENT_HISTORY";
   case "FORECAST_INPUT_STALE" -> "STALE";
   case "FORECAST_INPUT_FRESHNESS_UNKNOWN","FORECAST_CALENDAR_INVALID" -> "CALENDAR_UNAVAILABLE";
   default -> "UNAVAILABLE";
  };
 }
 private String freshness(LocalDate origin,List<com.aistock.market.quotation.TradingDay> days){
  var now=ZonedDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai")));var cutoff=now.getHour()<updateHour?now.toLocalDate().minusDays(1):now.toLocalDate();
  if(days.isEmpty() || cutoff.isBefore(days.get(0).date()) || cutoff.isAfter(days.get(days.size()-1).date()))return "UNKNOWN";
  var expected=days.stream().filter(d->d.open()&&!d.date().isAfter(cutoff)).map(d->d.date()).reduce((a,b)->b).orElse(null);
  return expected==null?"UNKNOWN":expected.isAfter(origin)?"STALE":"FRESH";
 }
 private String decimal(BigDecimal n){if(n==null)throw ForecastInputValidator.error("FORECAST_INPUT_INVALID");return n.stripTrailingZeros().toPlainString();}
 private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)));}catch(Exception e){throw new IllegalStateException("Input serialization failed",e);}}
}
