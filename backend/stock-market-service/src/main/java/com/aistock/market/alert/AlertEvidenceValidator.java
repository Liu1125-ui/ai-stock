package com.aistock.market.alert;
import com.aistock.common.BusinessException;
import com.aistock.market.forecast.ForecastCandidate;
import com.aistock.market.quotation.*;
import java.math.BigDecimal;import java.time.*;import java.util.*;
import org.springframework.http.HttpStatus;
public final class AlertEvidenceValidator {
 private final Clock clock;
 public AlertEvidenceValidator(Clock clock){this.clock=clock;}
 public AlertEvidenceRecords.Pair pair(String operation,String instrument,UUID rawVersion,LocalDate previous,LocalDate day,List<RawBar> raw,List<TradingDay> calendar,ForecastCandidate source){
  if(source==null||!operation.equals(source.operationId())||!instrument.equals(source.instrumentId())||!rawVersion.toString().equals(source.rawVersionId())||!"baostock".equals(source.provider())||!"baostock-backward-close-over-raw-v1".equals(source.definition())||source.fetchedAt()==null||source.fetchedAt().isBefore(clock.instant().minusSeconds(300))||source.fetchedAt().isAfter(clock.instant().plusSeconds(5))||source.sourcePayloadHash()==null||!source.sourcePayloadHash().matches("[a-f0-9]{64}")||source.bars()==null||source.bars().size()!=2||source.calendar()==null||source.calendar().size()!=java.time.temporal.ChronoUnit.DAYS.between(previous,day)+1)throw invalid();
  var authoritative=new TreeMap<LocalDate,Boolean>();for(var d:calendar){if(d==null||d.date()==null||authoritative.put(d.date(),d.open())!=null)throw invalid();}
  if(!Boolean.TRUE.equals(authoritative.get(previous))||!Boolean.TRUE.equals(authoritative.get(day))||!previous.equals(authoritative.headMap(day,false).entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).reduce((a,b)->b).orElse(null)))throw invalid();
  LocalDate expected=previous;
  for(var d:source.calendar()){
   if(d==null||!expected.equals(d.date())||!d.date().isAfter(day)&&!Objects.equals(authoritative.get(d.date()),d.open()))throw invalid();expected=expected.plusDays(1);
  }
  var fixed=new HashMap<LocalDate,RawBar>();for(var b:raw)if(b!=null&&b.date()!=null){if(fixed.put(b.date(),b)!=null)throw invalid();}
  for(int i=0;i<2;i++){
   var p=source.bars().get(i);var date=i==0?previous:day;
   if(p==null||p.raw()==null||!date.equals(p.raw().date()))throw invalid();var b=p.raw();var f=fixed.get(date);
   if(f==null||f.tradeStatus()!=1||b.tradeStatus()!=1||b.sourceAdjustmentRatio()!=null||!same(b.open(),f.open())||!same(b.high(),f.high())||!same(b.low(),f.low())||!same(b.close(),f.close())||!same(b.volumeShares(),f.volumeShares())||!same(b.amountYuan(),f.amountYuan()))throw invalid();
   positive(b.open());positive(b.high());positive(b.low());positive(b.close());positive(p.backwardClose());
  }
  return new AlertEvidenceRecords.Pair(decimal(source.bars().get(0).backwardClose()),decimal(source.bars().get(1).backwardClose()));
 }
 private static boolean same(BigDecimal a,BigDecimal b){return a!=null&&b!=null&&a.compareTo(b)==0;}
 private static void positive(BigDecimal value){if(value==null||value.signum()<=0||value.precision()>28||value.scale()>12||value.precision()-value.scale()>16)throw invalid();}
 public static String decimal(BigDecimal value){return value.stripTrailingZeros().toPlainString();}
 public static BusinessException invalid(){return new BusinessException("ALERT_SOURCE_INVALID","预警复权证据未通过核验，原有数据保留",HttpStatus.UNPROCESSABLE_ENTITY);}
}
