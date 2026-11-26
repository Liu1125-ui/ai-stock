package com.aistock.notification.alert;
import com.aistock.common.BusinessException;import com.fasterxml.jackson.databind.*;import java.net.*;import java.net.http.*;import java.time.*;import java.io.*;import java.nio.ByteBuffer;import java.util.*;import java.util.concurrent.*;import org.springframework.http.HttpStatus;
/** Bounded internal GET transport; credentials never enter exceptions or logs. */
public final class AlertRemoteHttp {
 private final java.util.function.Function<String,String> resolver;private final String token;private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
 public AlertRemoteHttp(String base,String token){this(service->base,token);}
 public AlertRemoteHttp(java.util.function.Function<String,String> resolver,String token){this.resolver=resolver;this.token=token;}
 public JsonNode get(String service,String path){
  if(token==null||token.isBlank()||!path.startsWith("/internal/v1/"))throw unavailable();
  LimitedBody body=new LimitedBody();CompletableFuture<HttpResponse<byte[]>> pending=null;
  try{var base=URI.create(resolver.apply(service));if(!Set.of("http","https").contains(base.getScheme())||base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null||!(base.getPath().isEmpty()||base.getPath().equals("/")))throw invalid();
   var uri=URI.create(base.toString().replaceAll("/$","")+path);var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("X-Service-Token",token);if("stock-market-service".equals(service)&&path.matches("/internal/v1/market/alert-evidence/versions/[a-f0-9-]{36}"))AlertMarketUseContext.headers().forEach(builder::header);var request=builder.GET().build();pending=client.sendAsync(request,info->body);var response=pending.get(30,TimeUnit.SECONDS);
   if(response.statusCode()!=200)throw unavailable();var root=mapper.readTree(response.body());if(root==null||!root.isObject()||!"OK".equals(root.path("code").asText())||!root.path("data").isObject())throw invalid();return root.get("data");
  }catch(BusinessException error){throw error;}catch(InterruptedException error){Thread.currentThread().interrupt();throw unavailable();}catch(com.fasterxml.jackson.core.JsonProcessingException error){throw invalid();}catch(Exception error){throw unavailable();}
  finally{if(pending!=null)pending.cancel(true);body.cancel();}
 }
 static BusinessException invalid(){return new BusinessException("ALERT_DEPENDENCY_INVALID","预警依据未通过核验",HttpStatus.SERVICE_UNAVAILABLE);}
 static BusinessException unavailable(){return new BusinessException("ALERT_DEPENDENCY_UNAVAILABLE","预警依赖暂不可用，请稍后重试",HttpStatus.SERVICE_UNAVAILABLE);}
 static boolean uuid(String value){return value!=null&&value.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");}
 static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]>{
  private final ByteArrayOutputStream output=new ByteArrayOutputStream();private final CompletableFuture<byte[]> future=new CompletableFuture<>();private volatile Flow.Subscription subscription;
  public CompletionStage<byte[]> getBody(){return future;}public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
  public void onNext(List<ByteBuffer> chunks){for(var chunk:chunks){if(chunk.remaining()>131072-output.size()){subscription.cancel();future.completeExceptionally(new IOException("Internal payload limit exceeded"));return;}byte[] bytes=new byte[chunk.remaining()];chunk.get(bytes);output.writeBytes(bytes);}subscription.request(1);}
  public void onError(Throwable error){future.completeExceptionally(error);}public void onComplete(){future.complete(output.toByteArray());}void cancel(){if(subscription!=null)subscription.cancel();future.cancel(true);}
 }
}
