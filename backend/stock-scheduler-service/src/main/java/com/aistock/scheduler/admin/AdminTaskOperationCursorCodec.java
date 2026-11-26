package com.aistock.scheduler.admin;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.*;import java.time.*;import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import static com.aistock.scheduler.admin.AdminTaskActionRecords.*;
@Component
public final class AdminTaskOperationCursorCodec {
 private final ObjectMapper mapper=JsonMapper.builder().findAndAddModules()
  .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
  .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public String encode(HistoryCursor cursor) {
  try{return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(cursor));}
  catch(Exception e){throw AdminTaskActionInput.invalid();}
 }
 public HistoryCursor decode(String token,String task,int limit) {
  try {
   if(token==null||token.isEmpty()||token.length()>4096||!token.matches("[A-Za-z0-9_-]+"))throw AdminTaskActionInput.invalid();
   var bytes=Base64.getUrlDecoder().decode(token);if(bytes.length>3072)throw AdminTaskActionInput.invalid();
   var c=mapper.readValue(bytes,HistoryCursor.class);
   if(c.version()!=1||!AdminTaskActionInput.validateId(task).toString().equals(c.taskId())||limit<1||limit>50||c.limit()!=limit)throw AdminTaskActionInput.invalid();
   key(c.upper());key(c.after());if(compare(c.after(),c.upper())>0)throw AdminTaskActionInput.invalid();
   return c;
  }catch(Exception e){throw AdminTaskActionInput.invalid();}
 }
 private void key(HistoryKey key) {
  if(key==null||key.requestedAt()==null||!key.requestedAt().equals(key.requestedAt().truncatedTo(ChronoUnit.MILLIS))
   ||key.requestedAt().isBefore(Instant.parse("1000-01-01T00:00:00Z"))||key.requestedAt().isAfter(Instant.parse("9999-12-31T23:59:59.999Z")))throw AdminTaskActionInput.invalid();
  AdminTaskActionInput.validateId(key.operationId());
 }
 private int compare(HistoryKey a,HistoryKey b){int n=a.requestedAt().compareTo(b.requestedAt());return n==0?a.operationId().compareTo(b.operationId()):n;}
}
