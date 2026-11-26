package com.aistock.scheduler.task;
import java.time.*;import java.util.*;
import org.springframework.stereotype.Component;import org.springframework.beans.factory.annotation.Value;import org.springframework.scheduling.annotation.Scheduled;
@Component
public final class ComparisonUpdatePlan {
 private final TaskRepository records;private final ComparisonTaskClient client;private final Clock clock;
 private final String token;private final boolean enabled;private final List<String> instruments;private final int maxAttempts;
 public ComparisonUpdatePlan(TaskRepository records,ComparisonTaskClient client,Clock clock,
  @Value("${aistock.internal.token:}")String token,@Value("${aistock.comparison-update.plan-enabled:false}")boolean enabled,
  @Value("${aistock.comparison-update.instruments:SH600030,SH600276,SZ000002}")String instruments,
  @Value("${aistock.comparison-update.max-attempts:3}")int maxAttempts){
  this.records=records;this.client=client;this.clock=clock;this.token=token;this.enabled=enabled;
  if(instruments==null||maxAttempts<1||maxAttempts>3)throw new IllegalArgumentException("Invalid comparison plan configuration");
  this.instruments=Arrays.stream(instruments.split(",",-1)).map(String::trim).toList();
  try{HttpComparisonTaskClient.validateIds(this.instruments);}catch(Exception e){throw new IllegalArgumentException("Invalid comparison instruments");}
  this.maxAttempts=maxAttempts;
 }
 @Scheduled(fixedDelayString="${aistock.comparison-update.scan-poll-ms:60000}")
 public synchronized void tick(){runOnce();}
 public synchronized boolean runOnce(){
  if(!enabled||token==null||token.isBlank())return false;
  var before=clock.instant().atZone(ZoneId.of("Asia/Shanghai"));if(before.getHour()<20)return false;
  try{
   var options=client.options(instruments);var now=clock.instant();var current=now.atZone(ZoneId.of("Asia/Shanghai"));
   if(current.getHour()<20||!before.toLocalDate().equals(current.toLocalDate())||!options.asOf().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().equals(current.toLocalDate()))return false;
   HttpComparisonTaskClient.validateOptions(options,instruments,now);boolean queued=false;
   for(var i:options.items()){
    if(!"READY".equals(i.state()))continue;
    records.createComparison(new ComparisonTaskRecords.Snapshot(i.instrumentId(),i.rawVersionId(),i.rawContentHash(),i.rangeStart(),i.targetDate(),options.definition(),options.asOf()),
     current.toLocalDate(),maxAttempts,now);queued=true;
   }return queued;
  }catch(Exception unavailable){return false;}
 }
}
