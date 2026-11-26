package com.aistock.market.comparison;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import java.net.*;import java.net.http.*;import java.nio.ByteBuffer;import java.io.*;import java.time.*;import java.util.*;import java.util.concurrent.*;
public final class ComparisonCollectorClient implements ComparisonCollector {
 private final String base,token;private final ObjectMapper mapper;private final Duration timeout;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 public ComparisonCollectorClient(String base,String token,ObjectMapper mapper){this(base,token,mapper,Duration.ofSeconds(30));}
 ComparisonCollectorClient(String base,String token,ObjectMapper mapper,Duration timeout){
  this.base=base;this.token=token;this.timeout=timeout;
  this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
   .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
   .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
 }
 public Collected collect(ComparisonUpdateRecords.Request r){
  if(token==null||token.isBlank())return failure("SOURCE_UNAVAILABLE");
  CompletableFuture<HttpResponse<byte[]>> pending=null;var subscriber=new LimitedBody();
  try{
   var body=Map.of("operationId",r.operationId().toString(),"instrumentId",r.instrumentId(),"rawVersionId",r.rawVersionId().toString(),
    "start",r.start().toString(),"end",r.end().toString());
   var request=HttpRequest.newBuilder(URI.create(base).resolve("/internal/v1/market/comparison-collect")).timeout(timeout)
    .header("X-Service-Token",token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build();
   pending=client.sendAsync(request,info->subscriber);var response=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
   if(response.statusCode()!=200)return failure("SOURCE_UNAVAILABLE");
   var root=mapper.readTree(response.body());
   if(!root.isObject()||!fields(root).equals(Set.of("state","candidate","errorCode"))||!root.path("state").isTextual())return failure("SOURCE_INVALID");
   var state=root.path("state").textValue();
   if(!"AVAILABLE".equals(state)){
    if(!Set.of("SOURCE_BUSY","SOURCE_TIMEOUT","SOURCE_UNAVAILABLE","SOURCE_INVALID").contains(state)||!root.path("candidate").isNull()
      ||!state.equals(root.path("errorCode").textValue()))return failure("SOURCE_INVALID");
    return failure(state);
   }
   var c=root.path("candidate");
   if(!c.isObject()||!root.path("errorCode").isNull()||!c.path("points").isArray()||!c.path("calendar").isArray())return failure("SOURCE_INVALID");
   for(var d:c.path("calendar"))if(!d.path("date").isTextual()||!d.path("open").isBoolean())return failure("SOURCE_INVALID");
   for(var p:c.path("points")){
    if(!p.path("date").isTextual()||!p.path("tradeStatus").isIntegralNumber()||!number(p.path("rawClose"))||!number(p.path("backwardClose"))
      ||!(p.path("volumeShares").isNull()||number(p.path("volumeShares"))))return failure("SOURCE_INVALID");
   }
   var candidate=mapper.treeToValue(c,ComparisonEvidenceRecords.Candidate.class);
   if(!r.operationId().toString().equals(candidate.operationId())||!r.instrumentId().equals(candidate.instrumentId())||!r.rawVersionId().equals(candidate.rawVersionId())
     ||!r.start().equals(candidate.from())||!r.end().equals(candidate.to())||!"baostock".equals(candidate.provider())||!r.definition().equals(candidate.definition()))return failure("SOURCE_INVALID");
   return new Collected("AVAILABLE",candidate,null);
  }catch(TimeoutException e){return failure("SOURCE_TIMEOUT");}
  catch(InterruptedException e){Thread.currentThread().interrupt();return failure("SOURCE_UNAVAILABLE");}
  catch(ExecutionException e){
   for(Throwable cause=e;cause!=null;cause=cause.getCause()){
    if(cause instanceof HttpTimeoutException)return failure("SOURCE_TIMEOUT");
    if(cause instanceof PayloadLimit)return failure("SOURCE_INVALID");
   }return failure("SOURCE_UNAVAILABLE");
  }catch(com.fasterxml.jackson.core.JsonProcessingException e){return failure("SOURCE_INVALID");}
  catch(Exception e){return failure("SOURCE_INVALID");}
  finally{if(pending!=null)pending.cancel(true);subscriber.cancel();}
 }
 private static boolean number(JsonNode n){return n.isNumber()||n.isTextual()&&n.textValue().matches("-?[0-9]+(?:\\.[0-9]+)?");}
 private static Set<String> fields(JsonNode n){var out=new HashSet<String>();n.fieldNames().forEachRemaining(out::add);return out;}
 private static Collected failure(String code){return new Collected(code,null,code);}
 private static final class PayloadLimit extends IOException {}
 private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
  private final ByteArrayOutputStream out=new ByteArrayOutputStream();private final CompletableFuture<byte[]> body=new CompletableFuture<>();
  private volatile Flow.Subscription subscription;
  public CompletionStage<byte[]> getBody(){return body;}
  public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
  public void onNext(List<ByteBuffer> chunks){
   for(var chunk:chunks){
    if(chunk.remaining()>2097152-out.size()){subscription.cancel();body.completeExceptionally(new PayloadLimit());return;}
    var b=new byte[chunk.remaining()];chunk.get(b);out.writeBytes(b);
   }subscription.request(1);
  }
  public void onError(Throwable e){body.completeExceptionally(e);}
  public void onComplete(){body.complete(out.toByteArray());}
  void cancel(){var s=subscription;if(s!=null)s.cancel();body.cancel(true);}
 }
}
