package com.aistock.scheduler.admin;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.aistock.scheduler.admin.AdminTaskActionRecords.*;

/** One fail-closed policy shared by capability hints and locked command execution. */
@Component
public final class AdminTaskActionPolicy {
 private static final ZoneId SHANGHAI=ZoneId.of("Asia/Shanghai");
 private static final Set<String> TYPES=Set.of("MARKET_REFRESH","PREDICTION_VERIFY","COMPARISON_UPDATE");
 private static final Set<String> MARKET=Set.of("SOURCE_TIMEOUT","SOURCE_BUSY","SOURCE_UNAVAILABLE","ATTEMPT_EXPIRED","MARKET_REVISION_CONFLICT","REFRESH_RUNNING");
 private static final Set<String> VERIFY=Set.of("SOURCE_TIMEOUT","SOURCE_BUSY","SOURCE_UNAVAILABLE","ATTEMPT_EXPIRED","VERIFICATION_SOURCE_UNAVAILABLE","VERIFICATION_BUDGET_EXCEEDED","REALIZATION_RAW_UNAVAILABLE");
 private static final Set<String> COMPARISON=Set.of("SOURCE_TIMEOUT","SOURCE_BUSY","SOURCE_UNAVAILABLE","ATTEMPT_EXPIRED");
 public Decision evaluate(Action action,PolicySnapshot s,Instant now,boolean actionsEnabled) {
  if(!actionsEnabled)return deny("TASK_ACTIONS_DISABLED");
  if(action==null||s==null||now==null||s.taskType()==null||!TYPES.contains(s.taskType())||s.state()==null)return deny("TASK_ACTION_NOT_ALLOWED");
  if(s.activeLease()||s.unresolvedAttempt())return deny("TASK_RESULT_UNCONFIRMED");
  if(action==Action.CANCEL) {
   return Set.of("QUEUED","RETRY_WAIT").contains(s.state())?allow():deny("TASK_ACTION_NOT_ALLOWED");
  }
  if(!"FAILED".equals(s.state()))return deny("TASK_ACTION_NOT_ALLOWED");
  if(!s.workerEnabled())return deny("TASK_WORKER_DISABLED");
  Set<String> reasons=switch(s.taskType()) {case "MARKET_REFRESH"->MARKET;case "PREDICTION_VERIFY"->VERIFY;default->COMPARISON;};
  if(s.errorCode()==null||!reasons.contains(s.errorCode()))return deny("TASK_ACTION_NOT_ALLOWED");
  if("COMPARISON_UPDATE".equals(s.taskType())) {
   if(!s.contextValid()||s.comparisonCycle()==null||s.comparisonLimit()==null||s.comparisonLimit()<1||s.comparisonLimit()>3||s.totalAttempts()<0)return deny("COMPARISON_CONTEXT_INVALID");
   if(!s.comparisonCycle().equals(now.atZone(SHANGHAI).toLocalDate()))return deny("COMPARISON_CYCLE_EXPIRED");
   if(s.totalAttempts()>=s.comparisonLimit())return deny("COMPARISON_BUDGET_EXHAUSTED");
  }else if(s.retryAvailableAt()!=null&&now.isBefore(s.retryAvailableAt())) {
   return new Decision(false,"TASK_RETRY_COOLDOWN",message("TASK_RETRY_COOLDOWN"),s.retryAvailableAt());
  }
  return allow();
 }
 private Decision allow(){return new Decision(true,"ALLOWED","当前可以操作",null);}
 private Decision deny(String code){return new Decision(false,code,message(code),null);}
 public static String message(String code) {
  return switch(code) {
   case "TASK_ACTIONS_DISABLED"->"管理员任务操作入口尚未开启";
   case "TASK_WORKER_DISABLED"->"对应任务执行器未开启，暂不能重新排队";
   case "TASK_RESULT_UNCONFIRMED"->"任务仍有执行或待核对记录，请等待原结果确认";
   case "TASK_RETRY_COOLDOWN"->"距离上次人工重试过近，请稍后再试";
   case "COMPARISON_CONTEXT_INVALID"->"比较任务的原始上下文无法核验，暂不能重试";
   case "COMPARISON_CYCLE_EXPIRED"->"比较任务已超出原周期，不能重新排队";
   case "COMPARISON_BUDGET_EXHAUSTED"->"比较任务原有尝试次数已用完";
   case "TASK_REVISION_CONFLICT"->"任务状态已变化，请刷新后重新确认";
   case "TASK_RETRY_APPLIED"->"已重新排队，实际执行结果请查看任务状态";
   case "TASK_CANCEL_APPLIED"->"已取消当前任务，后续自动计划不受影响";
   default->"当前任务不符合此操作条件";
  };
 }
}
