package com.aistock.notification.alertmail;
import com.aistock.notification.alert.AlertEvaluationRecords.Snapshot;import java.math.*;import java.util.Set;
/** Pure fixed-snapshot text. Never reads current quotes or calls AI. */
public final class AlertMailTemplate {
 private AlertMailTemplate(){}
 public static String body(Snapshot s){try{return validatedBody(s);}catch(RuntimeException invalid){throw invalid();}}
 private static String validatedBody(Snapshot s){
  if(s==null||s.evidence()==null||s.instrumentId()==null||!s.instrumentId().matches("(SH|SZ)[0-9]{6}")||s.tradeDate()==null||s.createdAt()==null||s.threshold()==null||!s.threshold().matches("[0-9]{1,16}(\\.[0-9]{1,12})?")||new BigDecimal(s.threshold()).signum()<=0||!Set.of("PRICE_ABOVE","PRICE_BELOW","DAILY_RISE","DAILY_FALL").contains(s.condition()))throw invalid();
  var e=s.evidence();if(!s.instrumentId().equals(e.instrumentId())||!s.tradeDate().equals(e.tradeDate())||e.capturedAt()==null||e.rawClose()==null||e.provider()==null||!e.provider().matches("[a-zA-Z0-9_-]{1,32}"))throw invalid();
  String condition=switch(s.condition()){case "PRICE_ABOVE"->"未复权收盘价 > "+s.threshold()+" 元";case "PRICE_BELOW"->"未复权收盘价 < "+s.threshold()+" 元";case "DAILY_RISE"->"复权口径日涨幅 > "+s.threshold()+"%";default->"复权口径日跌幅 > "+s.threshold()+"%";};
  String rate="";if(s.condition().startsWith("DAILY_")){if(e.previousBackwardClose()==null||e.currentBackwardClose()==null)throw invalid();var p=new BigDecimal(e.previousBackwardClose());var c=new BigDecimal(e.currentBackwardClose());if(p.signum()<=0||c.signum()<=0)throw invalid();rate="\n前一交易日后复权收盘价："+p.toPlainString()+"\n当日后复权收盘价："+c.toPlainString()+"\n复权口径日变化（展示近似值）："+c.subtract(p).multiply(new BigDecimal("100")).divide(p,8,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+"%";}
  return "你好，你设置的日线预警已触发。\n股票："+s.instrumentId()+"\n行情日期："+s.tradeDate()+"\n条件："+condition+"\n未复权收盘价："+e.rawClose()+" 元"+rate+"\n数据来源："+e.provider()+"\n来源采集时间："+e.capturedAt()+"\n触发记录时间："+s.createdAt()+"\n请登录系统消息中心查看固定证据。该提醒保留触发时数据，不代表当前行情仍满足条件，仅供辅助参考。";
 }
 private static AlertMailPayloadException invalid(){return new AlertMailPayloadException();}
}
