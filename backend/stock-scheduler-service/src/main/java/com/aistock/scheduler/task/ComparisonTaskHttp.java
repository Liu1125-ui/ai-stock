package com.aistock.scheduler.task;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.core.JsonParser;
import java.net.*;import java.net.http.*;import java.time.Duration;import java.nio.ByteBuffer;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
/** Dedicated bounded transport; does not change any existing client timeouts. */
final class ComparisonTaskHttp {
 private final String base,token;private final ObjectMapper mapper;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 ComparisonTaskHttp(String base,String token,ObjectMapper mapper){
  this.base=base;this.token=token;this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
   .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 }
 JsonNode call(String path,Object body,Duration timeout){
  if(token==null||token.isBlank())throw new Remote(-1,"COMPARISON_SOURCE_UNAVAILABLE");
  var subscriber=new Limited();CompletableFuture<HttpResponse<byte[]>> pending=null;
  try{
   var builder=HttpRequest.newBuilder(URI.create(base).resolve(path)).timeout(timeout).header("X-Service-Token",token).header("Accept","application/json");
   if(body==null)builder.GET();else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
   pending=client.sendAsync(builder.build(),info->subscriber);var response=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
   if(response.statusCode()>=300&&response.statusCode()<400)throw new Remote(response.statusCode(),"COMPARISON_SOURCE_UNAVAILABLE");
   var root=mapper.readTree(response.body());if(root==null||!root.isObject())throw invalid();
   if(response.statusCode()!=200){var code=root.path("code").asText("");throw new Remote(response.statusCode(),code.matches("[A-Z][A-Z0-9_]{0,63}")?code:"COMPARISON_SOURCE_INVALID");}
   return root;
  }catch(Remote e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new Remote(-1,"COMPARISON_SOURCE_UNAVAILABLE");}
  catch(Exception e){throw new Remote(-1,"COMPARISON_SOURCE_UNAVAILABLE");}
  finally{if(pending!=null)pending.cancel(true);subscriber.cancel();}
 }
 static Remote invalid(){return new Remote(-1,"COMPARISON_SOURCE_INVALID");}
 static final class Remote extends RuntimeException {
  final int status;final String code;Remote(int status,String code){super("Comparison dependency unavailable");this.status=status;this.code=code;}
 }
 private static final class Limited implements HttpResponse.BodySubscriber<byte[]> {
  private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private final CompletableFuture<byte[]> body=new CompletableFuture<>();
  private final AtomicReference<Flow.Subscription> subscription=new AtomicReference<>();
  public CompletionStage<byte[]> getBody(){return body;}
  public void onSubscribe(Flow.Subscription s){if(!subscription.compareAndSet(null,s)||body.isDone()){s.cancel();return;}s.request(1);}
  public void onNext(List<ByteBuffer> chunks){
   if(body.isDone())return;
   for(var b:chunks){
    if(b.remaining()>262144-bytes.size()){cancel();return;}
    var data=new byte[b.remaining()];b.get(data);bytes.writeBytes(data);
   }var s=subscription.get();if(s!=null&&!body.isDone())s.request(1);
  }
  public void onError(Throwable error){body.completeExceptionally(error);}
  public void onComplete(){body.complete(bytes.toByteArray());}
  void cancel(){var s=subscription.get();if(s!=null)s.cancel();body.completeExceptionally(new IOException("Bounded body cancelled"));}
 }
}
