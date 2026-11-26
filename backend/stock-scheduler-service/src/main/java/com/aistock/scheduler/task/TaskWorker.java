package com.aistock.scheduler.task;
import java.time.*;import java.util.*;
import org.springframework.beans.factory.annotation.Value;import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
@Component
public class TaskWorker {
 private static final Set<String> OLD_TYPES=Set.of("MARKET_REFRESH","PREDICTION_VERIFY");
 private final TaskRepository repository;private final MarketRefreshClient market;private final MarketReceiptClient receipts;
 private final PredictionTaskClient predictions;private final ComparisonTaskClient comparisons;private final Clock clock;
 private final String token;private final boolean enabled,comparisonEnabled;
 public TaskWorker(TaskRepository repository,MarketRefreshClient market,MarketReceiptClient receipts,PredictionTaskClient predictions,String token,boolean enabled){
  this(repository,market,receipts,predictions,null,Clock.systemUTC(),token,enabled,false);
 }
 @Autowired
 public TaskWorker(TaskRepository repository,MarketRefreshClient market,MarketReceiptClient receipts,PredictionTaskClient predictions,ComparisonTaskClient comparisons,Clock clock,
  @Value("${aistock.internal.token:}")String token,@Value("${aistock.scheduler.worker-enabled:false}")boolean enabled,
  @Value("${aistock.comparison-update.worker-enabled:false}")boolean comparisonEnabled){
  this.repository=repository;this.market=market;this.receipts=receipts;this.predictions=predictions;this.comparisons=comparisons;this.clock=clock;
  this.token=token;this.enabled=enabled;this.comparisonEnabled=comparisonEnabled;
 }
 @Scheduled(fixedDelayString="${aistock.scheduler.poll-ms:1000}")
 public synchronized void tick(){
  if(token==null||token.isBlank())return;var allowed=new HashSet<String>();
  if(enabled)allowed.addAll(OLD_TYPES);if(comparisonEnabled)allowed.add("COMPARISON_UPDATE");
  if(!allowed.isEmpty())runTypes(allowed);
 }
 public synchronized void runOnce(){runTypes(OLD_TYPES);}
 public synchronized void runComparisonOnce(){runTypes(Set.of("COMPARISON_UPDATE"));}
 private void runTypes(Set<String> allowed){
  for(var pending:repository.pendingReceipts(allowed))recover(pending);
  var claim=repository.claim(clock.instant(),allowed);if(claim==null)return;
  try{
   MarketRefreshClient.Result result;
   if("COMPARISON_UPDATE".equals(claim.taskType()))result=comparisons.execute(claim,repository.comparisonContext(claim.taskId()));
   else if("PREDICTION_VERIFY".equals(claim.taskType()))result=predictions.execute(claim);
   else if("MARKET_REFRESH".equals(claim.taskType())){
    com.aistock.common.adminrefresh.AdminMarketRefreshRecords.Constraint constraint;
    try{constraint=repository.adminContext(claim);}catch(com.aistock.common.BusinessException invalid){repository.finish(claim,new MarketRefreshClient.Result(claim.attemptId(),"FAILED",null,"ADMIN_REFRESH_CONTEXT_INVALID"),clock.instant());return;}
    var response=market.refresh(new MarketRefreshClient.Request(claim.attemptId(),claim.instrumentId(),claim.targetDate(),claim.leaseUntil(),constraint),token);
    result=response!=null&&"OK".equals(response.code())?response.data():null;
   }else result=unsupported(claim);
   if(result!=null&&!"RUNNING".equals(result.state())){if(!repository.finish(claim,result,clock.instant()))recover(claim);}
   else {if(result==null&&"COMPARISON_UPDATE".equals(claim.taskType()))repository.markComparisonUnknown(claim,clock.instant());recover(claim);}
  }catch(Exception unknown){if("COMPARISON_UPDATE".equals(claim.taskType()))repository.markComparisonUnknown(claim,clock.instant());recover(claim);}
 }
 private void recover(TaskRepository.Claim claim){
  try{
   if("COMPARISON_UPDATE".equals(claim.taskType()))repository.reconcile(claim,comparisons.receipt(claim),clock.instant());
   else if("PREDICTION_VERIFY".equals(claim.taskType()))repository.reconcile(claim,predictions.receipt(claim),clock.instant());
   else if("MARKET_REFRESH".equals(claim.taskType())){
    var response=receipts.get(claim.attemptId(),token);
    if(response!=null&&"OK".equals(response.code())&&response.data()!=null)repository.reconcile(claim,response.data(),clock.instant());
   }else repository.reconcile(claim,unsupported(claim),clock.instant());
  }catch(ComparisonTaskClient.MissingReceipt|PredictionTaskClient.MissingReceipt e){expired(claim);}
  catch(feign.FeignException e){if(e.status()==404)expired(claim);}
  catch(Exception unknown){/* Keep the original attempt visible; never blindly repost. */}
 }
 private MarketRefreshClient.Result unsupported(TaskRepository.Claim c){return new MarketRefreshClient.Result(c.attemptId(),"FAILED",null,"TASK_TYPE_UNSUPPORTED");}
 private void expired(TaskRepository.Claim claim){
  var now=clock.instant();if(!now.isBefore(claim.leaseUntil()))repository.reconcile(claim,new MarketRefreshClient.Result(claim.attemptId(),"FAILED",null,"ATTEMPT_EXPIRED"),now);
 }
}
