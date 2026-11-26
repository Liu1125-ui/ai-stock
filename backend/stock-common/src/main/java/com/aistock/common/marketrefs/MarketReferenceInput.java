package com.aistock.common.marketrefs;
import com.aistock.common.adminmarket.AdminMarketQuery;
import com.fasterxml.jackson.core.*;import com.fasterxml.jackson.databind.*;import java.io.IOException;import java.util.*;
import static com.aistock.common.marketrefs.MarketReferenceRecords.*;
public final class MarketReferenceInput {
 public static final String INTERNAL_BASE="/internal/v1/market-version-references";
 public static final Set<String> OWNERS=Set.of("prediction","scheduler","notification");
 public static final Set<String> REASONS=Set.of("REFERENCE_QUERY_BUSY","REFERENCE_QUERY_UNAVAILABLE","REFERENCE_QUERY_BUDGET","REFERENCE_ROW_LIMIT","REFERENCE_JSON_LIMIT","REFERENCE_JSON_INVALID","REFERENCE_SERVICE_UNAVAILABLE","REFERENCE_RESPONSE_INVALID");
 private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).findAndRegisterModules().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
 private MarketReferenceInput(){}
 public static String requireVersion(String v){if(v==null||!v.matches(AdminMarketQuery.UUID_PATTERN))throw AdminMarketQuery.invalid();return v;}
 public static String requireStock(String s){if(s==null||!s.matches(AdminMarketQuery.INSTRUMENT))throw AdminMarketQuery.invalid();return s;}
 public static boolean publicPath(String p){return p!=null&&p.matches(AdminMarketQuery.BASE+"/"+AdminMarketQuery.INSTRUMENT+"/versions/"+AdminMarketQuery.UUID_PATTERN+"/references");}
 public static boolean internalPath(String p){return p!=null&&p.matches(INTERNAL_BASE+"/"+AdminMarketQuery.UUID_PATTERN);}
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid reference response");}
 private static boolean text(String v,int max){return v!=null&&!v.isBlank()&&v.length()<=max&&v.codePoints().noneMatch(Character::isISOControl);}
 public static OwnerResult validateOwner(OwnerResult v,String owner,String version){
  if(v==null||!OWNERS.contains(owner)||!owner.equals(v.owner())||!Objects.equals(version,v.versionId())||v.queriedAt()==null||v.groups()==null||v.groups().size()>32)throw invalid();
  if("UNKNOWN".equals(v.state())){if(!v.groups().isEmpty()||!REASONS.contains(v.reasonCode()))throw invalid();return v;}
  if(v.groups().isEmpty()||!Set.of("COMPLETE","PARTIAL").contains(Objects.toString(v.state(),"")))throw invalid();
  var seen=new HashSet<String>();boolean all=true;
  for(var g:v.groups()){
   if(g==null||g.category()==null||!g.category().matches("[a-z][a-z0-9_]{0,63}")||!seen.add(g.category())||!text(g.label(),80))throw invalid();
   if("COMPLETE".equals(g.state())){if(g.rows()==null||!g.rows().matches("0|[1-9][0-9]{0,3}")||Integer.parseInt(g.rows())>1000||g.reasonCode()!=null)throw invalid();}
   else if("UNKNOWN".equals(g.state())){all=false;if(g.rows()!=null||!REASONS.contains(g.reasonCode()))throw invalid();}else throw invalid();
  }
  if(all?!"COMPLETE".equals(v.state())||v.reasonCode()!=null:!"PARTIAL".equals(v.state())||!"REFERENCE_PARTIAL".equals(v.reasonCode()))throw invalid();return v;
 }
 public static Report validateReport(Report v,String stock,String version){
  if(v==null||!Objects.equals(stock,v.instrumentId())||!Objects.equals(version,v.versionId())||!text(v.provider(),32)||!text(v.priceBasis(),32)||!Set.of("AVAILABLE","STAGING","REJECTED","UNAVAILABLE").contains(Objects.toString(v.versionState(),""))||v.startedAt()==null||v.finishedAt()==null||v.finishedAt().isBefore(v.startedAt())||v.owners()==null||v.owners().size()!=3)throw invalid();
  var seen=new HashSet<String>();boolean all=true;for(var o:v.owners()){if(o==null||!seen.add(o.owner()))throw invalid();validateOwner(o,o.owner(),version);all&="COMPLETE".equals(o.state());}if(!seen.equals(OWNERS)||!(all?"COMPLETE":"PARTIAL").equals(v.state()))throw invalid();return v;
 }
 public static OwnerResult decodeOwner(byte[] bytes,String owner,String version){try{var n=tree(bytes);ownerShape(n);return validateOwner(JSON.treeToValue(n,OwnerResult.class),owner,version);}catch(IOException|RuntimeException e){throw invalid();}}
 public static Report decodeReport(byte[] bytes,String stock,String version){try{var n=tree(bytes);if(!n.path("currentHead").isBoolean()||!n.path("startedAt").isTextual()||!n.path("finishedAt").isTextual()||!n.path("owners").isArray())throw invalid();for(var o:n.path("owners"))ownerShape(o);return validateReport(JSON.treeToValue(n,Report.class),stock,version);}catch(IOException|RuntimeException e){throw invalid();}}
 private static JsonNode tree(byte[] bytes)throws IOException{if(bytes==null||bytes.length>65536)throw invalid();var n=JSON.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);if(n==null||!n.isObject())throw invalid();return n;}
 private static void ownerShape(JsonNode n){if(!n.isObject()||!n.path("owner").isTextual()||!n.path("versionId").isTextual()||!n.path("queriedAt").isTextual()||!n.path("state").isTextual()||!nullableText(n.get("reasonCode"))||!n.path("groups").isArray())throw invalid();for(var g:n.path("groups")){if(!g.isObject()||!g.path("category").isTextual()||!g.path("label").isTextual()||!g.path("state").isTextual()||!nullableText(g.get("rows"))||!nullableText(g.get("reasonCode")))throw invalid();}}
 private static boolean nullableText(JsonNode n){return n!=null&&(n.isNull()||n.isTextual());}
}
