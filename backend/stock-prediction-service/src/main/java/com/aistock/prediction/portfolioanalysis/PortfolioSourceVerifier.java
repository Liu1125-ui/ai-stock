package com.aistock.prediction.portfolioanalysis;
import java.time.LocalDate;import java.util.*;import java.math.BigDecimal;
import static com.aistock.prediction.portfolioanalysis.PortfolioRecords.*;
public final class PortfolioSourceVerifier {
 public void verifySnapshot(PositionSnapshot s,UUID owner){
  if(s==null||owner==null||!owner.equals(s.ownerId())||!"stock-user-service".equals(s.sourceService())||!integer(s.bookRevision(),true)||s.items()==null||s.items().size()>100)throw PortfolioErrors.invalid();
  var ids=new HashSet<String>();var positions=new HashSet<UUID>();
  for(var p:s.items())if(p==null||p.positionId()==null||!positions.add(p.positionId())||!code(p.instrumentId())||!ids.add(p.instrumentId())||!integer(p.quantity(),false)||!decimal(p.averageCost(),false)||!integer(p.revision(),false))throw PortfolioErrors.invalid();
 }
 public void verify(PositionSnapshot s,QuoteBatch batch,UUID owner,LocalDate asOf){
  verifySnapshot(s,owner);
  if(batch==null||asOf==null||!asOf.equals(batch.asOfDate())||batch.capturedAt()==null||batch.items()==null||batch.items().size()!=s.items().size())throw PortfolioErrors.invalid();
  var ids=new HashSet<String>();s.items().forEach(p->ids.add(p.instrumentId()));
  var quoted=new HashSet<String>();
  for(var q:batch.items()){
   if(q==null||!ids.contains(q.instrumentId())||!quoted.add(q.instrumentId())||!"baostock".equals(q.provider())||!"RAW".equals(q.priceBasis())||!Set.of("FRESH","STALE","UNKNOWN").contains(q.freshness()==null?"":q.freshness()))throw PortfolioErrors.invalid();
   boolean calendar=q.calendarVersionId()!=null||q.calendarHash()!=null||q.checkedThrough()!=null;
   if(calendar&&(q.calendarVersionId()==null||!hash(q.calendarHash())||!asOf.equals(q.checkedThrough())))throw PortfolioErrors.invalid();
   if(q.priceDate()!=null&&(q.priceDate().isAfter(asOf)||q.priceDate().isBefore(LocalDate.of(1990,1,1))))throw PortfolioErrors.invalid();
   if("AVAILABLE".equals(q.status())){
    if(q.reason()!=null||!decimal(q.close(),true)||q.priceDate()==null||q.versionId()==null||!hash(q.contentHash())||(!"UNKNOWN".equals(q.freshness())&&!calendar))throw PortfolioErrors.invalid();
   }else if("UNAVAILABLE".equals(q.status())){
    if(q.close()!=null||!Set.of("NO_DATA","NO_VALID_CLOSE","CALENDAR_CONFLICT").contains(q.reason()==null?"":q.reason())||!"UNKNOWN".equals(q.freshness()))throw PortfolioErrors.invalid();
    if("NO_DATA".equals(q.reason())){if(q.versionId()!=null||q.contentHash()!=null||q.priceDate()!=null||calendar||q.retainedPrice())throw PortfolioErrors.invalid();}
    else if(q.versionId()==null||!hash(q.contentHash()))throw PortfolioErrors.invalid();
    if("CALENDAR_CONFLICT".equals(q.reason())&&(q.priceDate()==null||!calendar))throw PortfolioErrors.invalid();
    if("NO_VALID_CLOSE".equals(q.reason())&&(q.priceDate()!=null||calendar||q.retainedPrice()))throw PortfolioErrors.invalid();
   }else throw PortfolioErrors.invalid();
  }
  if(!quoted.equals(ids))throw PortfolioErrors.invalid();
 }
 private boolean code(String s){return s!=null&&s.matches("(SH|SZ)[0-9]{6}");}
 private boolean hash(String s){return s!=null&&s.matches("[a-f0-9]{64}");}
 private boolean integer(String s,boolean zero){try{return s!=null&&s.matches(zero?"(?:0|[1-9][0-9]{0,18})":"[1-9][0-9]{0,18}")&&Long.parseLong(s)>=0;}catch(NumberFormatException e){return false;}}
 private boolean decimal(String s,boolean positive){if(s==null||!s.matches("(?:0|[1-9][0-9]{0,13})(?:\\.[0-9]{1,6})?"))return false;var d=new BigDecimal(s);return positive?d.signum()>0:d.signum()>=0;}
}
