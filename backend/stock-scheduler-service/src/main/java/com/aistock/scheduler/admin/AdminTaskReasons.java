package com.aistock.scheduler.admin;
import java.util.*;import static com.aistock.scheduler.admin.AdminTaskRecords.*;
public final class AdminTaskReasons {
 private AdminTaskReasons(){}
 private static final Map<String,String> MESSAGES=messages();
 private static Map<String,String> messages(){var m=new HashMap<String,String>();
  add(m,"行情版本当前不接受本次使用，任务已停止", "MARKET_VERSION_USE_BLOCKED","MARKET_VERSION_UNAVAILABLE","MARKET_USE_ALREADY_RELEASED");
  add(m,"行情使用保护未就绪或凭据不匹配，请核对配置", "MARKET_USE_NOT_READY","MARKET_USE_BINDING_MISMATCH");
  add(m,"数据源响应超时，按任务计划处理", "SOURCE_TIMEOUT");
  add(m,"数据源繁忙或暂不可用", "SOURCE_BUSY","SOURCE_UNAVAILABLE","COMPARISON_SOURCE_UNAVAILABLE","VERIFICATION_SOURCE_UNAVAILABLE");
  add(m,"正在核对上次执行结果", "RESULT_UNKNOWN");
  add(m,"执行租约已过期", "ATTEMPT_EXPIRED");
  add(m,"该比较更新周期已经结束", "COMPARISON_CYCLE_EXPIRED");
  add(m,"已达到本周期尝试上限", "COMPARISON_BUDGET_EXHAUSTED","VERIFICATION_BUDGET_EXCEEDED");
  add(m,"行情来源或数据格式不满足要求", "SOURCE_INVALID","SOURCE_INCOMPATIBLE","COMPARISON_SOURCE_INVALID","COMPARISON_RAW_INCOMPATIBLE","VERIFICATION_SOURCE_INVALID","MARKET_DATA_INCOMPLETE");
  add(m,"可用行情版本或历史范围不足", "NO_RAW_VERSION","RAW_LAGGING","INSUFFICIENT_HISTORY","COMPARISON_RAW_UNAVAILABLE","REALIZATION_RAW_UNAVAILABLE");
  add(m,"交易日历暂不可用", "CALENDAR_UNAVAILABLE","COMPARISON_CALENDAR_UNAVAILABLE");
  add(m,"行情版本发生变化或更新仍在进行", "MARKET_REVISION_CONFLICT","REFRESH_RUNNING");
  add(m,"任务依据或请求不满足执行条件", "COMPARISON_CONTEXT_INVALID","COMPARISON_REQUEST_INVALID","COMPARISON_REFRESH_NOT_FOUND","VERIFICATION_PREDICTION_INVALID","VERIFICATION_RECORD_INVALID","VERIFICATION_REQUEST_INVALID","TASK_REQUEST_INVALID","TASK_TYPE_UNSUPPORTED");
  add(m,"下游服务访问校验未通过", "COMPARISON_ACCESS_DENIED","VERIFICATION_ACCESS_DENIED","INTERNAL_AUTH_REQUIRED","INTERNAL_AUTH_NOT_CONFIGURED","ACCESS_DENIED","AUTH_REQUIRED");
  return Map.copyOf(m);
 }
 private static void add(Map<String,String> map,String message,String... codes){for(var code:codes)map.put(code,message);}
 public static Reason safe(String code){if(code==null||code.isBlank())return new Reason(null,"暂无原因记录");var message=MESSAGES.get(code);return message==null?new Reason(null,"未识别原因，请联系管理员核对"):new Reason(code,message);}
}
