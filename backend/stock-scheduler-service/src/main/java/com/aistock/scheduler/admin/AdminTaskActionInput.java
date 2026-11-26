package com.aistock.scheduler.admin;

import com.aistock.common.BusinessException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.http.HttpStatus;
import java.util.*;
import static com.aistock.scheduler.admin.AdminTaskActionRecords.*;

/** A bounded, exact JSON command; no free text, caller identity or execution tokens. */
public final class AdminTaskActionInput {
 private static final ObjectMapper MAPPER=JsonMapper.builder()
  .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
  .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private static final Set<String> FIELDS=Set.of("operationId","expectedRevision","reasonCode");
 private AdminTaskActionInput() {}
 public static Command parse(byte[] bytes,Action action) {
  if(bytes==null||bytes.length==0||bytes.length>4096||action==null)throw invalid();
  try {
   var node=MAPPER.readTree(bytes);
   if(node==null||!node.isObject()||node.size()!=3)throw invalid();
   var keys=node.fieldNames();while(keys.hasNext())if(!FIELDS.contains(keys.next()))throw invalid();
   for(String key:FIELDS)if(!node.path(key).isTextual())throw invalid();
   var operation=validateId(node.get("operationId").textValue());
   var version=node.get("expectedRevision").textValue();revision(version);
   var reason=node.get("reasonCode").textValue();
   var reasons=action==Action.RETRY?Set.of("RETRY_DEPENDENCY_RECOVERED","RETRY_TRANSIENT_FAILURE"):
    Set.of("CANCEL_NOT_NEEDED","CANCEL_DUPLICATE_REQUEST");
   if(!reasons.contains(reason))throw invalid();
   return new Command(operation,version,reason);
  }catch(BusinessException e){throw e;}catch(Exception e){throw invalid();}
 }
 public static UUID validateId(String id) {
  if(id==null||!id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))throw invalid();
  return UUID.fromString(id);
 }
 public static long revision(String value) {
  if(value==null||value.length()>19||!value.matches("0|[1-9][0-9]*"))throw invalid();
  try{return Long.parseLong(value);}catch(NumberFormatException e){throw invalid();}
 }
 public static BusinessException invalid() {
  return new BusinessException("TASK_ACTION_INVALID","任务操作参数不正确，请刷新后重试",HttpStatus.BAD_REQUEST);
 }
}
