package com.aistock.market.comparison;
import com.aistock.common.BusinessException;
import java.time.Clock;
import static com.aistock.market.comparison.ComparisonUpdateRecords.*;
public final class ComparisonRefreshService {
 private final com.aistock.market.marketlifecycle.MarketComparisonUseOwner uses;private final ComparisonRefreshRepository records;private final ComparisonUpdateQuery query;private final ComparisonCollector collector;private final Clock clock;
 public ComparisonRefreshService(ComparisonRefreshRepository records,ComparisonUpdateQuery query,ComparisonCollector collector,Clock clock){this(records,query,collector,clock,null);}
 public ComparisonRefreshService(ComparisonRefreshRepository records,ComparisonUpdateQuery query,ComparisonCollector collector,Clock clock,com.aistock.market.marketlifecycle.MarketComparisonUseOwner uses){this.uses=uses;
  this.records=records;this.query=query;this.collector=collector;this.clock=clock;
 }
 public Result refresh(Request r){
  ComparisonRefreshRepository.validate(r);var old=records.replay(r);if(old!=null)return old;
  if(!clock.instant().isBefore(r.leaseUntil())||r.leaseUntil().isAfter(clock.instant().plusSeconds(120)))throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");
  boolean local=uses!=null&&uses.enabled();if(!local)query.fixedWindow(r);var claim=records.begin(r);if(!claim.execute())return claim.receipt();
  try(var scope=local?uses.acquire(r):com.aistock.market.marketlifecycle.MarketEvidenceGate.passthrough()){
  if(local)query.fixedWindow(r);ComparisonCollector.Collected source;
  com.aistock.market.marketlifecycle.MarketEvidenceGate.checkpoint();
  if(local)records.marketUseInput(r);
  else if(!clock.instant().isBefore(r.leaseUntil()))return records.complete(r,null,"ATTEMPT_EXPIRED");
  try{source=collector.collect(r);}catch(Exception e){return records.complete(r,null,"SOURCE_UNAVAILABLE");}
  if(source==null)return records.complete(r,null,"SOURCE_INVALID");
  if(!"AVAILABLE".equals(source.state())){
   var code=java.util.Set.of("SOURCE_BUSY","SOURCE_TIMEOUT","SOURCE_UNAVAILABLE","SOURCE_INVALID").contains(source.state())?source.state():"SOURCE_INVALID";
   return records.complete(r,null,code);
  }
  try{return records.complete(r,source.candidate(),null);}
  catch(BusinessException e){return records.complete(r,null,e.code());}
  }catch(BusinessException error){return records.complete(r,null,error.code());}
  finally{if(local)uses.notifyCompleted(r.operationId());}
  // Database/transaction errors remain unknown; do not pretend a failure receipt was saved.
 }
}
