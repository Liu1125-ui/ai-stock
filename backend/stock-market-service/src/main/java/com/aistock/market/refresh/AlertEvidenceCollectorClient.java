package com.aistock.market.refresh;
import com.aistock.market.alert.*;
import com.aistock.market.forecast.ForecastCandidate;
import com.fasterxml.jackson.databind.*;
import java.net.URI;import java.net.http.*;import java.time.*;import java.util.*;
import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Component;
@Component public class AlertEvidenceCollectorClient implements AlertEvidenceCollector {
 private final String base,token;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
 public AlertEvidenceCollectorClient(@Value("${aistock.algorithm.url:http://127.0.0.1:18090}")String base,@Value("${aistock.internal.token:}")String token){this.base=base;this.token=token;}
 public AlertEvidenceRecords.Source collect(AlertEvidenceRecords.Request input,LocalDate previous){
  if(token==null||token.isBlank())return failure("SOURCE_UNAVAILABLE");
  try{
   var payload=Map.of("operationId",input.operationId().toString(),"instrumentId",input.instrumentId(),"rawVersionId",input.rawVersionId().toString(),"start",previous.toString(),"end",input.tradeDate().toString(),"calendarThrough",input.tradeDate().toString());
   var request=HttpRequest.newBuilder(URI.create(base).resolve("/internal/v1/market/alert-collect")).timeout(Duration.ofSeconds(30)).header("X-Service-Token",token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
   var subscriber=new AlgorithmCollectorClient.LimitedBody(131072);
   var pending=client.sendAsync(request,info->subscriber);
   HttpResponse<byte[]> response;
   try{response=pending.get(30,java.util.concurrent.TimeUnit.SECONDS);}finally{pending.cancel(true);subscriber.cancel();}
   if(response.statusCode()!=200)return failure("SOURCE_UNAVAILABLE");
   var root=mapper.readTree(response.body());if(!root.isObject()||!fields(root).equals(Set.of("state","candidate","errorCode"))||!root.path("state").isTextual())return failure("SOURCE_INVALID");
   var state=root.path("state").textValue();
   if(!"AVAILABLE".equals(state)){if(!Set.of("SOURCE_BUSY","SOURCE_TIMEOUT","SOURCE_UNAVAILABLE","SOURCE_INVALID").contains(state)||!root.path("candidate").isNull()||!state.equals(root.path("errorCode").textValue()))return failure("SOURCE_INVALID");return failure(state);}
   var c=root.path("candidate");if(!c.isObject()||!root.path("errorCode").isNull()||!c.path("calendar").isArray()||!c.path("bars").isArray())return failure("SOURCE_INVALID");
   for(var day:c.path("calendar"))if(!day.path("date").isTextual()||!day.path("open").isBoolean())return failure("SOURCE_INVALID");
   for(var point:c.path("bars")){var b=point.path("raw");if(!b.path("date").isTextual()||!b.path("tradeStatus").isIntegralNumber()||!b.path("sourceAdjustmentRatio").isNull())return failure("SOURCE_INVALID");for(var field:List.of("open","high","low","close","volumeShares","amountYuan"))if(!number(b.path(field)))return failure("SOURCE_INVALID");if(!number(point.path("backwardClose")))return failure("SOURCE_INVALID");}
   return new AlertEvidenceRecords.Source("AVAILABLE",mapper.treeToValue(c,ForecastCandidate.class),null);
  }catch(java.util.concurrent.TimeoutException error){return failure("SOURCE_TIMEOUT");}catch(HttpTimeoutException error){return failure("SOURCE_TIMEOUT");}catch(InterruptedException error){Thread.currentThread().interrupt();return failure("SOURCE_UNAVAILABLE");}catch(Exception error){return failure("SOURCE_INVALID");}
 }
 private boolean number(JsonNode node){return node.isNumber()||node.isTextual()&&node.textValue().matches("-?[0-9]+(?:\\.[0-9]+)?");}
 private Set<String> fields(JsonNode node){var fields=new HashSet<String>();node.fieldNames().forEachRemaining(fields::add);return fields;}
 private AlertEvidenceRecords.Source failure(String state){return new AlertEvidenceRecords.Source(state,null,state);}
}
