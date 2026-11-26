package com.aistock.market.realization;
import com.aistock.common.BusinessException;
import com.aistock.market.forecast.ForecastCandidate;
import com.aistock.market.quotation.RawBar;
import java.math.BigDecimal;import java.time.*;import java.util.*;
import org.springframework.http.HttpStatus;
import static com.aistock.market.realization.RealizationRecords.*;
public final class RealizationValidator {
 private final Clock clock;private final int hour;
 public RealizationValidator(Clock clock,int hour){this.clock=clock;this.hour=hour;if(hour<0||hour>23)throw new IllegalArgumentException("Invalid cutoff");}
 public void request(Request r){
  var now=ZonedDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai")));var cutoff=now.getHour()<hour?now.toLocalDate().minusDays(1):now.toLocalDate();
  if(r==null||r.operationId()==null||r.instrumentId()==null||!r.instrumentId().matches("(SH|SZ)[0-9]{6}")||r.originDate()==null||r.originDate().isBefore(LocalDate.of(1990,1,1))||r.targetDates()==null||r.targetDates().size()!=5||r.dueThrough()==null||r.dueThrough().isBefore(r.originDate())||r.dueThrough().isAfter(cutoff)||r.dueThrough().isAfter(r.originDate().plusDays(90))||r.leaseUntil()==null||r.leaseUntil().isAfter(clock.instant().plusSeconds(120)))throw error("REALIZATION_REQUEST_INVALID");
  var last=r.originDate();for(var d:r.targetDates()){if(d==null||!d.isAfter(last)||d.isAfter(r.originDate().plusDays(90)))throw error("REALIZATION_REQUEST_INVALID");last=d;}
 }
 public Evidence evidence(Request r,Head h,ForecastCandidate c){
  if(c==null||!r.operationId().toString().equals(c.operationId())||!r.instrumentId().equals(c.instrumentId())||!h.versionId().equals(c.rawVersionId())||!"baostock".equals(c.provider())||!DEFINITION.equals(c.definition())||c.fetchedAt()==null||c.fetchedAt().isAfter(clock.instant().plusSeconds(300))||c.sourcePayloadHash()==null||!c.sourcePayloadHash().matches("[a-f0-9]{64}")||c.bars()==null||c.bars().isEmpty()||c.bars().size()>96||c.calendar()==null||c.calendar().isEmpty()||c.calendar().size()>96)throw error("REALIZATION_SOURCE_INVALID");
  var raw=new HashMap<LocalDate,RawBar>();for(var b:h.bars())if(!b.date().isBefore(r.originDate())&&!b.date().isAfter(r.dueThrough()))raw.put(b.date(),b);
  var days=new HashMap<LocalDate,Boolean>();var next=r.originDate();
  for(var d:c.calendar()){if(d==null||!next.equals(d.date())||d.date().isAfter(r.originDate().plusDays(95)))throw error("REALIZATION_CALENDAR_INVALID");days.put(d.date(),d.open());next=next.plusDays(1);}
  if(!days.containsKey(r.dueThrough()))throw error("REALIZATION_CALENDAR_INVALID");
  var paired=new HashMap<LocalDate,ForecastCandidate.PairedBar>();LocalDate previous=null;
  for(var p:c.bars()){
   if(p==null||p.raw()==null||p.raw().date()==null)throw error("REALIZATION_SOURCE_INVALID");var b=p.raw();
   if(b.date().isBefore(r.originDate())||b.date().isAfter(r.dueThrough())||previous!=null&&!b.date().isAfter(previous)||!Boolean.TRUE.equals(days.get(b.date()))||b.sourceAdjustmentRatio()!=null||b.tradeStatus()!=0&&b.tradeStatus()!=1)throw error("REALIZATION_SOURCE_INVALID");
   positive(b.close());positive(p.backwardClose());var expected=raw.get(b.date());
   if(expected==null||!same(b.open(),expected.open())||!same(b.high(),expected.high())||!same(b.low(),expected.low())||!same(b.close(),expected.close())||!same(b.volumeShares(),expected.volumeShares())||!same(b.amountYuan(),expected.amountYuan())||b.tradeStatus()!=expected.tradeStatus())throw error("REALIZATION_RAW_MISMATCH");
   paired.put(b.date(),p);previous=b.date();
  }
  if(!paired.keySet().equals(raw.keySet()))throw error("REALIZATION_RAW_MISMATCH");
  var origin=paired.get(r.originDate());if(origin==null||origin.raw().tradeStatus()!=1)throw error("REALIZATION_ORIGIN_MISSING");
  var observations=new ArrayList<Day>();
  for(var date:r.targetDates()){if(date.isAfter(r.dueThrough()))continue;var p=paired.get(date);String state=!Boolean.TRUE.equals(days.get(date))?"CALENDAR_CHANGED":p==null?"MISSING":p.raw().tradeStatus()==0?"SUSPENDED":"VERIFIED";observations.add(new Day(date,state,p==null?null:decimal(p.raw().close()),p==null?null:decimal(p.backwardClose())));}
  return new Evidence(null,r.instrumentId(),"baostock",DEFINITION,r.originDate(),r.targetDates(),r.dueThrough(),h.versionId(),h.contentHash(),c.sourcePayloadHash(),null,null,c.fetchedAt(),decimal(origin.raw().close()),decimal(origin.backwardClose()),observations,c.calendar());
 }
 private void positive(BigDecimal n){if(n==null||n.signum()<=0||n.precision()>28||n.scale()>12||n.precision()-n.scale()>16)throw error("REALIZATION_SOURCE_INVALID");}
 private boolean same(BigDecimal a,BigDecimal b){return a!=null&&b!=null&&a.compareTo(b)==0;}
 private String decimal(BigDecimal n){return n.stripTrailingZeros().toPlainString();}
 public static BusinessException error(String code){return new BusinessException(code,"真实验证证据未通过检查，已有预测和验证保留",HttpStatus.UNPROCESSABLE_ENTITY);}
}
