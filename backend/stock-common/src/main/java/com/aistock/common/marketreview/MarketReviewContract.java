package com.aistock.common.marketreview;

import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;

/** Version review is an operator's progress note, never a data availability decision. */
public final class MarketReviewContract {
 private MarketReviewContract(){}
 public record Command(String operationId,String expectedRevision,String reviewState,String note){}
 public record Receipt(String operationId,String instrumentId,String versionId,String revision,String reviewState,String note,Instant createdAt){}
 public record Page(String instrumentId,String versionId,String revision,Receipt current,List<Receipt> history,boolean hasMore,Instant queriedAt){}
 private static final String UUID="[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}";
 private static final String BASE="/api/v1/admin/market-data/(SH|SZ)[0-9]{6}/versions/"+UUID+"/reviews";
 private static final ObjectMapper MAPPER=new ObjectMapper().findAndRegisterModules().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 public static boolean readPath(String p){return p!=null&&p.matches(BASE+"(/"+UUID+")?");}
 public static boolean writePath(String p){return p!=null&&p.matches(BASE);}
 public static void identity(String stock,String version){if(stock==null||!stock.matches("(SH|SZ)[0-9]{6}"))throw invalid();id(version);}
 public static void id(String id){if(id==null||!id.matches(UUID))throw invalid();}
 public static long revision(String s){try{if(s==null||!s.matches("0|[1-9][0-9]{0,18}"))throw invalid();long n=Long.parseLong(s);if(n==Long.MAX_VALUE)throw invalid();return n;}catch(NumberFormatException e){throw invalid();}}
 public static void command(Command c){if(c==null)throw invalid();id(c.operationId());revision(c.expectedRevision());stateNote(c.reviewState(),c.note());}
 private static void stateNote(String state,String note){if(state==null||!Set.of("UNREVIEWED","REVIEWED","NEEDS_REVIEW").contains(state)||note==null||note.isBlank()||note.length()>300||!note.equals(note.strip())||note.chars().anyMatch(n->n<32&&n!=10&&n!=9||n==127))throw invalid();}
 public static Command parse(byte[] body){try{if(body==null||body.length>4096)throw invalid();var n=MAPPER.readTree(body);if(n==null||!n.isObject()||n.size()!=4)throw invalid();for(String f:List.of("operationId","expectedRevision","reviewState","note"))if(!n.path(f).isTextual())throw invalid();var c=MAPPER.treeToValue(n,Command.class);command(c);return c;}catch(BusinessException e){throw e;}catch(Exception e){throw invalid();}}
 public static Receipt receipt(Receipt r,String stock,String version){identity(stock,version);if(r==null||!stock.equals(r.instrumentId())||!version.equals(r.versionId())||r.createdAt()==null)throw invalid();id(r.operationId());if(revision(r.revision())<1)throw invalid();stateNote(r.reviewState(),r.note());return r;}
 public static Page page(Page p,String stock,String version){identity(stock,version);if(p==null||!stock.equals(p.instrumentId())||!version.equals(p.versionId())||p.queriedAt()==null||p.history()==null||p.history().size()>20)throw invalid();long rev=revision(p.revision());if(rev==0){if(p.current()!=null||!p.history().isEmpty()||p.hasMore())throw invalid();return p;}receipt(p.current(),stock,version);if(!p.revision().equals(p.current().revision())||p.history().isEmpty()||!p.current().equals(p.history().get(0)))throw invalid();long prev=rev+1;var ops=new HashSet<String>();for(var r:p.history()){receipt(r,stock,version);long n=revision(r.revision());if(n!=prev-1||!ops.add(r.operationId()))throw invalid();prev=n;}if(p.hasMore()!=(prev>1))throw invalid();return p;}
 public static Receipt decodeReceipt(byte[] b,String stock,String version){try{var n=MAPPER.readTree(b);textFields(n,List.of("operationId","instrumentId","versionId","revision","reviewState","note","createdAt"));return receipt(MAPPER.treeToValue(n,Receipt.class),stock,version);}catch(Exception e){throw invalid();}}
 public static Page decodePage(byte[] b,String stock,String version){try{var n=MAPPER.readTree(b);textFields(n,List.of("instrumentId","versionId","revision","queriedAt"));if(!n.path("hasMore").isBoolean()||!n.path("history").isArray())throw invalid();for(var r:n.path("history"))decodeReceipt(MAPPER.writeValueAsBytes(r),stock,version);if(!n.path("current").isNull())decodeReceipt(MAPPER.writeValueAsBytes(n.path("current")),stock,version);return page(MAPPER.treeToValue(n,Page.class),stock,version);}catch(Exception e){throw invalid();}}
 private static void textFields(JsonNode n,List<String> fields){if(n==null||!n.isObject())throw invalid();for(String f:fields)if(!n.path(f).isTextual())throw invalid();}
 public static BusinessException invalid(){return new BusinessException("REVIEW_REQUEST_INVALID","审核请求格式不正确",HttpStatus.BAD_REQUEST);}
}
