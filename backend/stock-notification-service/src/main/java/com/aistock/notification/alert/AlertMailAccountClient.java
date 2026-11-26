package com.aistock.notification.alert;

import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;

/** Strict, bounded internal recipient protocol; credentials and bodies never enter errors. */
public final class AlertMailAccountClient {
 public record AccountContext(UUID userId,String status,long revision,boolean emailVerified,Instant checkedAt){}
 public record MailTarget(UUID userId,String status,long revision,boolean emailVerified,String email,Instant checkedAt){
  @Override public String toString(){return "MailTarget[userId="+userId+", revision="+revision+", recipient=redacted]";}
 }
 private final Supplier<String> base;private final String token;private final Clock clock;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
 public AlertMailAccountClient(String base,String token,Clock clock){this(()->base,token,clock);}
 public AlertMailAccountClient(Supplier<String> base,String token,Clock clock){this.base=base;this.token=token;this.clock=clock;}
 public AccountContext context(UUID id){
  if(id==null)throw AlertRemoteHttp.invalid();var n=read("/internal/v1/users/"+id+"/alert-context",false);validate(n,id,false);
  return new AccountContext(id,n.get("status").textValue(),n.get("revision").longValue(),n.get("emailVerified").booleanValue(),Instant.parse(n.get("checkedAt").textValue()));
 }
 public MailTarget target(UUID id,long revision){
  if(id==null||revision<0)throw AlertRemoteHttp.invalid();var n=read("/internal/v1/users/"+id+"/alert-mail-target?revision="+revision,true);validate(n,id,true);
  if(n.get("revision").longValue()!=revision)throw AlertRemoteHttp.invalid();boolean verified=n.get("emailVerified").booleanValue();var email=n.get("email");
  if(verified?(!email.isTextual()||!validAddress(email.textValue())):!email.isNull())throw AlertRemoteHttp.invalid();
  return new MailTarget(id,n.get("status").textValue(),revision,verified,email.isNull()?null:email.textValue(),Instant.parse(n.get("checkedAt").textValue()));
 }
 private void validate(JsonNode n,UUID id,boolean target){
  try{
   if(n.size()!=(target?6:5)||!n.path("userId").isTextual()||!AlertRemoteHttp.uuid(n.path("userId").textValue())||!id.equals(UUID.fromString(n.get("userId").textValue()))||!n.path("status").isTextual()||!Set.of("ENABLED","DISABLED").contains(n.get("status").textValue())||!n.path("revision").isIntegralNumber()||!n.path("revision").canConvertToLong()||n.get("revision").longValue()<0||!n.path("emailVerified").isBoolean()||!n.path("checkedAt").isTextual()||target&&!n.has("email"))throw AlertRemoteHttp.invalid();
   var at=Instant.parse(n.get("checkedAt").textValue());var now=clock.instant();if(at.isBefore(now.minusSeconds(5))||at.isAfter(now.plusSeconds(5)))throw AlertRemoteHttp.invalid();
  }catch(BusinessException error){throw error;}catch(RuntimeException error){throw AlertRemoteHttp.invalid();}
 }
 public static boolean validAddress(String email){
  if(email==null||email.length()>254||email.chars().anyMatch(c->c>127||Character.isWhitespace(c)||Character.isISOControl(c)))return false;
  int at=email.indexOf('@');if(at<1||at!=email.lastIndexOf('@')||at>64)return false;
  String local=email.substring(0,at),domain=email.substring(at+1);
  if(!local.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+")||local.startsWith(".")||local.endsWith(".")||local.contains(".."))return false;
  String[] labels=domain.split("\\.",-1);if(labels.length<2)return false;
  for(String label:labels)if(!label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"))return false;return true;
 }
 private JsonNode read(String path,boolean recipient){
  if(token==null||token.isBlank())throw AlertRemoteHttp.unavailable();var body=new LimitedBody();CompletableFuture<HttpResponse<byte[]>> pending=null;
  try{
   var uri=URI.create(base.get());if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath().isEmpty()||"/".equals(uri.getPath())))throw AlertRemoteHttp.invalid();
   var request=HttpRequest.newBuilder(URI.create(uri.toString().replaceAll("/$","")+path)).timeout(Duration.ofSeconds(10)).header("X-Service-Token",token).GET().build();
   pending=client.sendAsync(request,info->body);var response=pending.get(10,TimeUnit.SECONDS);
   if(recipient&&(response.statusCode()==404||response.statusCode()==409))throw new BusinessException("ALERT_RECIPIENT_CHANGED","收件人身份已失效",HttpStatus.CONFLICT);
   if(response.statusCode()!=200)throw AlertRemoteHttp.unavailable();
   try(var parser=mapper.createParser(response.body())){JsonNode root=mapper.readTree(parser);if(root==null||!root.isObject()||!root.path("code").isTextual()||!"OK".equals(root.get("code").textValue())||!root.path("data").isObject()||parser.nextToken()!=null)throw AlertRemoteHttp.invalid();return root.get("data");}
  }catch(BusinessException error){throw error;}catch(com.fasterxml.jackson.core.JacksonException error){throw AlertRemoteHttp.invalid();}catch(InterruptedException error){Thread.currentThread().interrupt();throw AlertRemoteHttp.unavailable();}catch(Exception error){throw AlertRemoteHttp.unavailable();}
  finally{if(pending!=null)pending.cancel(true);body.cancel();}
 }
 private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
  final ByteArrayOutputStream out=new ByteArrayOutputStream();final CompletableFuture<byte[]> future=new CompletableFuture<>();volatile Flow.Subscription subscription;
  public CompletionStage<byte[]> getBody(){return future;}
  public void onSubscribe(Flow.Subscription value){subscription=value;value.request(1);}
  public void onNext(List<ByteBuffer> chunks){for(var b:chunks){if(b.remaining()>65536-out.size()){subscription.cancel();future.completeExceptionally(new IOException("Internal response limit"));return;}var bytes=new byte[b.remaining()];b.get(bytes);out.writeBytes(bytes);}subscription.request(1);}
  public void onError(Throwable e){future.completeExceptionally(e);}public void onComplete(){future.complete(out.toByteArray());}void cancel(){if(subscription!=null)subscription.cancel();future.cancel(true);}
 }
}
