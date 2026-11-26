package com.aistock.market.realization;
import com.fasterxml.jackson.databind.*;import java.net.URI;import java.net.http.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicReference;import java.nio.ByteBuffer;import java.io.ByteArrayOutputStream;
import static com.aistock.market.realization.RealizationRecords.*;
public final class RealizationSourceClient implements RealizationCollector {
 private final URI endpoint;private final String token;private final Duration timeout;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
 public RealizationSourceClient(String base,String token,Duration timeout){endpoint=URI.create(base).resolve("/internal/v1/market/forecast-collect");this.token=token;this.timeout=timeout;}
 public Source collect(Request r,Head h){if(token==null||token.isBlank())return new Source("SOURCE_UNAVAILABLE",null,"SOURCE_UNAVAILABLE");
  try{
   var body=Map.of("operationId",r.operationId().toString(),"instrumentId",r.instrumentId(),"rawVersionId",h.versionId(),"start",r.originDate().toString(),"end",r.dueThrough().toString(),"calendarThrough",r.dueThrough().plusDays(5).toString());
   var request=HttpRequest.newBuilder(endpoint).timeout(timeout).header("X-Service-Token",token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
   var subscriber=new Limited();var pending=client.sendAsync(request,i->subscriber);
   try{var response=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);if(response.statusCode()!=200)return new Source("SOURCE_UNAVAILABLE",null,"SOURCE_UNAVAILABLE");var root=mapper.readTree(response.body());var keys=new HashSet<String>();root.fieldNames().forEachRemaining(keys::add);if(!root.isObject()||!keys.equals(Set.of("state","candidate","errorCode")))return invalid();return mapper.treeToValue(root,Source.class);}finally{pending.cancel(true);subscriber.cancel();}
  }catch(TimeoutException e){return new Source("SOURCE_TIMEOUT",null,"SOURCE_TIMEOUT");}catch(InterruptedException e){Thread.currentThread().interrupt();return new Source("SOURCE_UNAVAILABLE",null,"SOURCE_UNAVAILABLE");}catch(Exception e){return invalid();}
 }
 private Source invalid(){return new Source("SOURCE_INVALID",null,"SOURCE_INVALID");}
 private static final class Limited implements HttpResponse.BodySubscriber<byte[]> {
  private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private final CompletableFuture<byte[]> body=new CompletableFuture<>();private final AtomicReference<Flow.Subscription> subscription=new AtomicReference<>();
  public CompletionStage<byte[]> getBody(){return body;}public void onSubscribe(Flow.Subscription s){if(!subscription.compareAndSet(null,s)||body.isDone()){s.cancel();return;}s.request(1);}public void onNext(List<ByteBuffer> buffers){if(body.isDone())return;for(var b:buffers){if(b.remaining()>131072-bytes.size()){cancel();return;}var chunk=new byte[b.remaining()];b.get(chunk);bytes.writeBytes(chunk);}var s=subscription.get();if(s!=null&&!body.isDone())s.request(1);}public void onError(Throwable t){body.completeExceptionally(t);}public void onComplete(){body.complete(bytes.toByteArray());}void cancel(){var s=subscription.get();if(s!=null)s.cancel();body.completeExceptionally(new java.io.IOException("Evidence body cancelled"));}
 }
}
