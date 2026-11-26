package com.aistock.scheduler.admin;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.core.JsonParser;import java.util.*;import java.time.temporal.ChronoUnit;
import static com.aistock.scheduler.admin.AdminTaskRecords.*;
@org.springframework.stereotype.Component
public final class AdminTaskCursorCodec {
 private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
 public String encodeList(ListCursor c){return encode(c);}
 public String encodeAttempts(AttemptCursor c){return encode(c);}
 public ListCursor decodeList(String value,TaskQuery query){
  try{AdminTaskQuery.validate(query);var c=decode(value,ListCursor.class);if(c.version()!=1||!query.equals(c.query()))throw AdminTaskQuery.invalid();key(c.upper(),query);key(c.after(),query);if(compare(c.after(),c.upper())>0)throw AdminTaskQuery.invalid();return c;}catch(RuntimeException e){throw AdminTaskQuery.invalid();}
 }
 public AttemptCursor decodeAttempts(String value,String taskId,int limit){
  try{var id=AdminTaskQuery.validateId(taskId);var c=decode(value,AttemptCursor.class);if(c.version()!=1||!id.equals(c.taskId())||limit<1||limit>50||c.limit()!=limit||c.afterOrdinal()<0||c.upperOrdinal()<0||c.afterOrdinal()>c.upperOrdinal())throw AdminTaskQuery.invalid();return c;}catch(RuntimeException e){throw AdminTaskQuery.invalid();}
 }
 public static int compare(SortKey a,SortKey b){int n=a.createdAt().compareTo(b.createdAt());return n==0?a.taskId().compareTo(b.taskId()):n;}
 private void key(SortKey key,TaskQuery q){if(key==null||key.createdAt()==null||!AdminTaskQuery.validateId(key.taskId()).equals(key.taskId())||!key.createdAt().equals(key.createdAt().truncatedTo(ChronoUnit.MILLIS))||key.createdAt().isBefore(AdminTaskQuery.start(q))||!key.createdAt().isBefore(AdminTaskQuery.end(q)))throw AdminTaskQuery.invalid();}
 private String encode(Object o){try{return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(o));}catch(Exception e){throw AdminTaskQuery.invalid();}}
 private <T>T decode(String s,Class<T> type){try{if(s==null||s.isEmpty()||s.length()>4096||!s.matches("[A-Za-z0-9_-]+"))throw AdminTaskQuery.invalid();var bytes=Base64.getUrlDecoder().decode(s);if(bytes.length>3072)throw AdminTaskQuery.invalid();return mapper.readValue(bytes,type);}catch(Exception e){throw AdminTaskQuery.invalid();}}
}
