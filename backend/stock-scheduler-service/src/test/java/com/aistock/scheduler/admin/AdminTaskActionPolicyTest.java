package com.aistock.scheduler.admin;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.scheduler.admin.AdminTaskActionRecords.*;

class AdminTaskActionPolicyTest {
 final Instant now=Instant.parse("2026-10-20T15:59:59Z");
 final AdminTaskActionPolicy policy=new AdminTaskActionPolicy();
 PolicySnapshot snapshot(String type,String state,String code,boolean lease,boolean unresolved,boolean worker,Instant available,LocalDate cycle,Integer limit,long total,boolean context) {
  return new PolicySnapshot(type,state,code,lease,unresolved,worker,available,cycle,limit,total,context);
 }
 PolicySnapshot ordinary(String state,String code) {return snapshot("MARKET_REFRESH",state,code,false,false,true,null,null,null,3,true);}
 Decision evaluate(Action a,PolicySnapshot s) {return policy.evaluate(a,s,now,true);}
 @ParameterizedTest @ValueSource(strings={"QUEUED","RUNNING","RECONCILING","RETRY_WAIT","SUCCEEDED","EMPTY","SKIPPED","CANCELLED","UNKNOWN"})
 void onlyFailedMayRetry(String state) {assertFalse(evaluate(Action.RETRY,ordinary(state,"SOURCE_TIMEOUT")).allowed());}
 @ParameterizedTest @ValueSource(strings={"RUNNING","RECONCILING","SUCCEEDED","EMPTY","SKIPPED","FAILED","CANCELLED","UNKNOWN"})
 void onlyQueuedAndRetryWaitMayCancel(String state) {assertFalse(evaluate(Action.CANCEL,ordinary(state,"SOURCE_TIMEOUT")).allowed());}
 @Test void cancellationDoesNotRequireWorkerButRetryDoes() {
  var s=snapshot("MARKET_REFRESH","QUEUED",null,false,false,false,null,null,null,0,true);
  assertTrue(evaluate(Action.CANCEL,s).allowed());
  assertTrue(evaluate(Action.CANCEL,ordinary("RETRY_WAIT","SOURCE_TIMEOUT")).allowed());
  assertFalse(evaluate(Action.RETRY,snapshot("MARKET_REFRESH","FAILED","SOURCE_TIMEOUT",false,false,false,null,null,null,3,true)).allowed());
 }
 @Test void disabledActionsOrUnknownTypesAlwaysDeny() {
  assertFalse(policy.evaluate(Action.RETRY,ordinary("FAILED","SOURCE_TIMEOUT"),now,false).allowed());
  assertFalse(evaluate(Action.RETRY,snapshot("NEW_TYPE","FAILED","SOURCE_TIMEOUT",false,false,true,null,null,null,1,true)).allowed());
  assertFalse(evaluate(Action.CANCEL,snapshot("NEW_TYPE","QUEUED",null,false,false,true,null,null,null,0,true)).allowed());
  assertFalse(evaluate(Action.RETRY,null).allowed());
 }
 @Test void everyTypeHasItsOwnFiniteRecoverableReasons() {
  for(String code:List.of("SOURCE_TIMEOUT","SOURCE_BUSY","SOURCE_UNAVAILABLE","ATTEMPT_EXPIRED","MARKET_REVISION_CONFLICT","REFRESH_RUNNING"))assertTrue(evaluate(Action.RETRY,ordinary("FAILED",code)).allowed(),code);
  for(String code:Arrays.asList(null,"SOURCE_INVALID","UNKNOWN","VERIFICATION_SOURCE_UNAVAILABLE","PERMISSION_DENIED"))assertFalse(evaluate(Action.RETRY,ordinary("FAILED",code)).allowed());
  for(String code:List.of("VERIFICATION_SOURCE_UNAVAILABLE","VERIFICATION_BUDGET_EXCEEDED","REALIZATION_RAW_UNAVAILABLE"))assertTrue(evaluate(Action.RETRY,snapshot("PREDICTION_VERIFY","FAILED",code,false,false,true,null,null,null,3,true)).allowed(),code);
  assertFalse(evaluate(Action.RETRY,snapshot("PREDICTION_VERIFY","FAILED","REFRESH_RUNNING",false,false,true,null,null,null,3,true)).allowed());
 }
 @Test void residualLeaseAndUnknownAttemptDenyBothActions() {
  for(Action action:Action.values())for(boolean lease:List.of(false,true)) {
   var s=snapshot("MARKET_REFRESH",action==Action.RETRY?"FAILED":"QUEUED","SOURCE_TIMEOUT",lease,!lease,true,null,null,null,3,true);
   assertFalse(evaluate(action,s).allowed());
  }
 }
 @Test void ordinaryCooldownBoundaryIsExactAndFirstAttemptHasNoCooldown() {
  assertTrue(evaluate(Action.RETRY,ordinary("FAILED","SOURCE_TIMEOUT")).allowed());
  for(long offset:new long[]{-1,0,1}) {
   var s=snapshot("MARKET_REFRESH","FAILED","SOURCE_TIMEOUT",false,false,true,now.plusSeconds(offset),null,null,3,true);
   var d=evaluate(Action.RETRY,s);assertEquals(offset<=0,d.allowed());
   if(offset>0){assertEquals("TASK_RETRY_COOLDOWN",d.reasonCode());assertEquals(now.plusSeconds(1),d.retryAvailableAt());}
  }
 }
 @Test void comparisonBudgetCycleAndContextRemainBindingAtMidnight() {
  var day=LocalDate.of(2026,10,20);
  var allowed=snapshot("COMPARISON_UPDATE","FAILED","SOURCE_TIMEOUT",false,false,true,null,day,3,2,true);
  assertTrue(evaluate(Action.RETRY,allowed).allowed());
  assertEquals("COMPARISON_CYCLE_EXPIRED",policy.evaluate(Action.RETRY,allowed,now.plusSeconds(1),true).reasonCode());
  assertEquals("COMPARISON_BUDGET_EXHAUSTED",evaluate(Action.RETRY,snapshot("COMPARISON_UPDATE","FAILED","SOURCE_TIMEOUT",false,false,true,null,day,3,3,true)).reasonCode());
  for(boolean context:List.of(false,true))assertFalse(evaluate(Action.RETRY,snapshot("COMPARISON_UPDATE","FAILED","SOURCE_TIMEOUT",false,false,true,null,day,context?null:3,2,context)).allowed());
  assertFalse(evaluate(Action.RETRY,snapshot("COMPARISON_UPDATE","FAILED","MARKET_REVISION_CONFLICT",false,false,true,null,day,3,2,true)).allowed());
 }
}
