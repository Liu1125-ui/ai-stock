package com.aistock.prediction.portfolioanalysis;
import com.aistock.common.BusinessException;import com.fasterxml.jackson.core.JsonParser;import com.fasterxml.jackson.databind.*;
import java.net.URI;import java.net.http.*;import java.nio.ByteBuffer;import java.time.Duration;import java.io.ByteArrayOutputStream;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
/** Bounded private POST transport; never logs credentials or response bodies. */
public final class PortfolioDependencyHttp {
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final String token;private final ObjectMapper mapper;
 public PortfolioDependencyHttp(String token,ObjectMapper mapper){this.token=token;this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);}
 public JsonNode call(String base,String path,Object body,int seconds){
  if(token==null||token.isBlank()||base==null||base.isBlank()||seconds<1||seconds>8)throw PortfolioErrors.unavailable();
  try{
   URI uri=URI.create(base);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getQuery()!=null||!path.startsWith("/internal/v1/"))throw PortfolioErrors.unavailable();
   var builder=HttpRequest.newBuilder(uri.resolve(path)).timeout(Duration.ofSeconds(seconds)).header("X-Service-Token",token).header("Accept","application/json");
   if("/internal/v1/market/valuation-quotes".equals(path))com.aistock.prediction.marketuse.MarketUseContext.headers().forEach(builder::header);
   if(body==null)builder.GET();else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
   var subscriber=new Limited();var future=client.sendAsync(builder.build(),i->subscriber);
   try{var response=future.get(seconds,TimeUnit.SECONDS);if(response.statusCode()>=300&&response.statusCode()<400)throw PortfolioErrors.unavailable();
    var envelope=mapper.readTree(response.body());if(envelope==null||!envelope.isObject())throw PortfolioErrors.unavailable();
    if(response.statusCode()!=200&&Set.of("MARKET_VERSION_USE_BLOCKED","MARKET_USE_NOT_READY","MARKET_USE_ALREADY_RELEASED","MARKET_VERSION_UNAVAILABLE","MARKET_USE_UNAVAILABLE").contains(envelope.path("code").asText()))throw new BusinessException(envelope.path("code").asText(),"行情使用暂不可用，已有持仓分析保留",HttpStatus.CONFLICT);
    if(response.statusCode()==401&&"AUTH_REQUIRED".equals(envelope.path("code").asText()))throw new BusinessException("AUTH_REQUIRED","请重新登录",HttpStatus.UNAUTHORIZED);
    if(response.statusCode()==404&&"RESOURCE_NOT_FOUND".equals(envelope.path("code").asText()))throw PortfolioErrors.missing();
    if(response.statusCode()!=200||!"OK".equals(envelope.path("code").asText())||!envelope.path("data").isObject())throw PortfolioErrors.unavailable();
    return envelope.get("data");
   }finally{future.cancel(true);subscriber.cancel();}
  }catch(BusinessException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw PortfolioErrors.unavailable();}catch(Exception e){throw PortfolioErrors.unavailable();}
 }
 private static final class Limited implements HttpResponse.BodySubscriber<byte[]> {
  private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private final CompletableFuture<byte[]> body=new CompletableFuture<>();private final AtomicReference<Flow.Subscription> subscription=new AtomicReference<>();
  public CompletionStage<byte[]> getBody(){return body;}
  public void onSubscribe(Flow.Subscription s){if(!subscription.compareAndSet(null,s)||body.isDone()){s.cancel();return;}s.request(1);}
  public void onNext(List<ByteBuffer> buffers){if(body.isDone())return;for(var b:buffers){if(b.remaining()>262144-bytes.size()){cancel();return;}var chunk=new byte[b.remaining()];b.get(chunk);bytes.writeBytes(chunk);}var s=subscription.get();if(s!=null&&!body.isDone())s.request(1);}
  public void onError(Throwable error){body.completeExceptionally(error);}public void onComplete(){body.complete(bytes.toByteArray());}
  void cancel(){body.completeExceptionally(new java.io.IOException("Portfolio source response canceled"));var s=subscription.get();if(s!=null)s.cancel();}
 }
}
