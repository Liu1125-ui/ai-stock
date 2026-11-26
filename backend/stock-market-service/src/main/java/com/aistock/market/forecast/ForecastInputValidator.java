package com.aistock.market.forecast;
import com.aistock.common.BusinessException;
import com.aistock.market.quotation.RawBar;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
final class ForecastInputValidator {
 static final String DEFINITION="baostock-backward-close-over-raw-v1";
 private final Clock clock;
 ForecastInputValidator(Clock clock){this.clock=clock;}
 void validate(ForecastCandidate c){
  if(c==null || c.operationId()==null || !c.operationId().matches("[A-Za-z0-9_-]{1,128}")
   || c.instrumentId()==null || !c.instrumentId().matches("(SH|SZ)[0-9]{6}") || !uuid(c.rawVersionId())
   || !"baostock".equals(c.provider()) || !DEFINITION.equals(c.definition()) || c.fetchedAt()==null
   || c.fetchedAt().isAfter(clock.instant().plusSeconds(300)) || c.sourcePayloadHash()==null
   || !c.sourcePayloadHash().matches("[a-f0-9]{64}") || c.bars()==null || c.bars().size()!=60 || c.calendar()==null)throw error("FORECAST_INPUT_INVALID");
  var today=c.fetchedAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();LocalDate previous=null;
  for(var p:c.bars()){
   if(p==null || p.raw()==null || p.raw().date()==null || p.raw().date().isBefore(LocalDate.of(1990,1,1))
    || p.raw().date().isAfter(today) || previous!=null && !p.raw().date().isAfter(previous)
    || !decimal(p.raw().open(),20,6) || !decimal(p.raw().high(),20,6) || !decimal(p.raw().low(),20,6)
    || !decimal(p.raw().close(),20,6) || !decimal(p.raw().volumeShares(),20,4) || !decimal(p.raw().amountYuan(),24,6)
    || !decimal(p.backwardClose(),28,12) || p.raw().sourceAdjustmentRatio()!=null)throw error("FORECAST_INPUT_INVALID");
   previous=p.raw().date();
  }
  if(c.calendar().isEmpty() || c.calendar().size()>1600)throw error("FORECAST_CALENDAR_INVALID");
  var expected=c.bars().get(0).raw().date();var limit=today.plusDays(90);var historical=new ArrayList<LocalDate>();
  for(var d:c.calendar()){
   if(d==null || !expected.equals(d.date()) || d.date().isAfter(limit))throw error("FORECAST_CALENDAR_INVALID");
   if(d.open() && !d.date().isAfter(previous))historical.add(d.date());expected=expected.plusDays(1);
  }
  if(!historical.equals(c.bars().stream().map(p->p.raw().date()).toList()) || targets(c).size()!=5)throw error("FORECAST_CALENDAR_INVALID");
 }
 List<LocalDate> targets(ForecastCandidate c){var origin=c.bars().get(59).raw().date();return c.calendar().stream().filter(d->d.open() && d.date().isAfter(origin)).limit(5).map(d->d.date()).toList();}
 void against(ForecastCandidate c,List<RawBar> all){
  if(all.size()<60)throw error("FORECAST_WINDOW_INCOMPLETE");var expected=all.subList(all.size()-60,all.size());
  for(int i=0;i<60;i++){
   var a=c.bars().get(i).raw();var b=expected.get(i);
   if(!a.date().equals(b.date()) || a.tradeStatus()!=b.tradeStatus()
    || !same(a.open(),b.open()) || !same(a.high(),b.high()) || !same(a.low(),b.low()) || !same(a.close(),b.close())
    || !same(a.volumeShares(),b.volumeShares()) || !same(a.amountYuan(),b.amountYuan()))throw error("FORECAST_RAW_MISMATCH");
   if(b.tradeStatus()!=1 || b.volumeShares()==null || b.amountYuan()==null || b.volumeShares().signum()<=0 || b.amountYuan().signum()<=0)throw error("FORECAST_WINDOW_INCOMPLETE");
   var vwap=b.amountYuan().divide(b.volumeShares(),12,RoundingMode.HALF_UP);
   if(vwap.compareTo(b.low().subtract(new BigDecimal("0.01")))<0 || vwap.compareTo(b.high().add(new BigDecimal("0.01")))>0)throw error("FORECAST_WINDOW_INCOMPLETE");
   ratio(c.bars().get(i));
  }
 }
 BigDecimal ratio(ForecastCandidate.PairedBar p){
  if(p.raw().close()==null || p.raw().close().signum()<=0)throw error("FORECAST_INPUT_INVALID");
  var r=p.backwardClose().divide(p.raw().close(),12,RoundingMode.HALF_UP);
  if(!decimal(r,24,12))throw error("FORECAST_INPUT_INVALID");return r;
 }
 static boolean uuid(String value){return value!=null && value.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}");}
 private boolean decimal(BigDecimal v,int precision,int scale){if(v==null || v.signum()<=0)return false;try{var n=v.stripTrailingZeros();return n.scale()<=scale && (long)n.precision()-n.scale()<=precision-scale;}catch(ArithmeticException e){return false;}}
 private boolean same(BigDecimal a,BigDecimal b){return a==null?b==null:b!=null && a.compareTo(b)==0;}
 static BusinessException error(String code){return new BusinessException(code,"预测输入的版本、来源、历史窗口或交易日历不满足条件",HttpStatus.UNPROCESSABLE_ENTITY);}
}
