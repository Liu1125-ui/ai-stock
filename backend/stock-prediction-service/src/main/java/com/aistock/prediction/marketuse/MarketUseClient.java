package com.aistock.prediction.marketuse;

import com.aistock.common.BusinessException;
import com.aistock.common.marketlifecycle.*;
import com.aistock.common.marketlifecycle.MarketUseRecords.UseIdentity;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;

/** Fixed market endpoint, strict bounded responses, and no implicit mutation retry. */
public class MarketUseClient {
    public record Request(String operationId,String generation,String requestId,String purpose,MarketUseRecords.Selector selector) {
        public Request {
            MarketLifecycleContract.identity(new UseIdentity("prediction",purpose,operationId,generation));MarketLifecycleContract.id(requestId);selector=MarketLifecycleContract.selector(selector);
            boolean valid=switch(purpose){case "STOCK_ANALYSIS"->"RAW_VERSIONS".equals(selector.kind())&&selector.versionIds().size()==1;case "FORECAST","PREDICTION_VERIFICATION"->("RAW_VERSIONS".equals(selector.kind())&&selector.versionIds().size()==1)||("CURRENT_HEADS".equals(selector.kind())&&selector.instruments().size()==1);case "PORTFOLIO_VALUATION"->"CURRENT_HEADS".equals(selector.kind());case "COMPARISON"->"COMPARISON_EVIDENCE".equals(selector.kind())&&selector.evidence().size()>=2&&selector.evidence().size()<=3;default->false;};if(!valid)throw invalid();
        }
        public Request(String operationId,String generation,String requestId,String rawVersionId){this(operationId,generation,requestId,"STOCK_ANALYSIS",new MarketUseRecords.Selector("RAW_VERSIONS",List.of(rawVersionId),null,null));}
        public String rawVersionId(){return "RAW_VERSIONS".equals(selector.kind())?selector.versionIds().get(0):null;}
        public Map<String,String> identity(){return Map.of("owner","prediction","purpose",purpose,"ownerOperationId",operationId,"generation",generation);}
    }
    public record Receipt(String bundleId,String state,List<String> versionIds) {
        public Receipt {MarketLifecycleContract.id(bundleId);if(!Set.of("ACTIVE","RELEASED").contains(state))throw invalid();versionIds=MarketLifecycleContract.versionSet(versionIds);}
    }
    private static final String BASE="/internal/v1/market-version-uses";
    private final URI base;private final String token;private final Duration timeout;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public MarketUseClient(String base,String token,Duration timeout){
        try{this.base=URI.create(base);}catch(RuntimeException e){throw new IllegalArgumentException("Invalid fixed market URL");}
        if(!Set.of("http","https").contains(this.base.getScheme())||this.base.getHost()==null||this.base.getUserInfo()!=null||this.base.getQuery()!=null||this.base.getFragment()!=null||!Set.of("","/").contains(this.base.getPath())||timeout==null||timeout.isZero()||timeout.isNegative()||timeout.compareTo(Duration.ofSeconds(4))>0)throw new IllegalArgumentException("Invalid market use configuration");
        this.token=token;this.timeout=timeout;
    }
    public void requireReady(){
        var n=call(BASE+"/capabilities",null,false);
        if(!MarketLifecycleContract.PROTOCOL.equals(n.path("protocolVersion").asText())||!n.path("protocolEnabled").isBoolean()||!n.path("protocolEnabled").booleanValue()||!n.path("evidenceGatesEnabled").isBoolean()||!n.path("evidenceGatesEnabled").booleanValue())throw notReady();
    }
    public Receipt register(Request r){var body=new HashMap<String,Object>(r.identity());body.put("requestId",r.requestId());body.put("selector",r.selector());return receipt(call(BASE,body,false),r,true);}
    public Optional<Receipt> find(Request r){var n=call(BASE+"/requests/"+r.requestId()+query(r),null,true);return n==null?Optional.empty():Optional.of(receipt(n,r,true));}
    public Receipt complete(Request r,Receipt original){var result=receipt(call(BASE+"/"+original.bundleId()+"/completion",r.identity(),false),r,false);if(!original.bundleId().equals(result.bundleId())||!original.versionIds().equals(result.versionIds())||!"RELEASED".equals(result.state()))throw invalid();return result;}
    private String query(Request r){return "?owner=prediction&purpose="+r.purpose()+"&ownerOperationId="+r.operationId()+"&generation="+r.generation();}
    private Receipt receipt(JsonNode n,Request r,boolean request){
        fields(n,Set.of("requestId","protocolVersion","bundleId","owner","purpose","ownerOperationId","generation","state","versionIds","uses"));
        for(var e:r.identity().entrySet())if(!n.path(e.getKey()).isTextual()||!e.getValue().equals(n.path(e.getKey()).asText()))throw invalid();
        if(!MarketLifecycleContract.PROTOCOL.equals(text(n,"protocolVersion"))||request&&!r.requestId().equals(text(n,"requestId"))||!request&&!n.path("requestId").isNull())throw invalid();
        int expected=switch(r.selector().kind()){case "RAW_VERSIONS"->r.selector().versionIds().size();case "CURRENT_HEADS"->r.selector().instruments().size();default->r.selector().evidence().size();};
        if(!n.path("versionIds").isArray()||n.path("versionIds").size()!=expected)throw invalid();var values=new ArrayList<String>();for(var value:n.path("versionIds")){if(!value.isTextual())throw invalid();values.add(value.asText());}var versions=MarketLifecycleContract.versionSet(values);if(r.selector().versionIds()!=null&&!r.selector().versionIds().equals(versions))throw invalid();
        if(!n.path("uses").isArray()||n.path("uses").size()!=expected)throw invalid();var used=new TreeSet<String>();var ids=new HashSet<String>();for(var use:n.path("uses")){fields(use,Set.of("useId","versionId","lifecycleRevision"));var useId=text(use,"useId");MarketLifecycleContract.id(useId);if(!ids.add(useId)||!used.add(text(use,"versionId")))throw invalid();MarketLifecycleContract.revision(text(use,"lifecycleRevision"));}if(!versions.equals(List.copyOf(used)))throw invalid();
        return new Receipt(text(n,"bundleId"),text(n,"state"),versions);
    }

    private JsonNode call(String path,Object payload,boolean missing){
        if(token==null||token.isBlank())throw unavailable();CompletableFuture<HttpResponse<byte[]>> future=null;var body=new LimitedBody();
        try{
            var request=HttpRequest.newBuilder(base.resolve(path)).timeout(timeout).header("X-Service-Token",token).header("Accept","application/json");
            if(payload==null)request.GET();else{byte[] bytes=json.writeValueAsBytes(payload);if(bytes.length>65536)throw invalid();request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(bytes));}
            future=http.sendAsync(request.build(),info->body);var response=future.get(timeout.toMillis(),TimeUnit.MILLISECONDS);var root=json.readTree(response.body());
            if(root==null||!root.isObject()||!root.path("code").isTextual())throw unavailable();
            if(response.statusCode()!=200){String code=root.path("code").asText();if(missing&&response.statusCode()==404&&"MARKET_VERSION_RESOURCE_NOT_FOUND".equals(code))return null;if(Set.of("MARKET_VERSION_USE_BLOCKED","MARKET_USE_NOT_READY","MARKET_USE_ALREADY_RELEASED","MARKET_VERSION_UNAVAILABLE").contains(code))throw new BusinessException(code,"行情版本暂不接受本次使用，已有结果保留",HttpStatus.CONFLICT);throw unavailable();}
            if(!"OK".equals(root.path("code").asText())||!root.path("data").isObject())throw unavailable();return root.get("data");
        }catch(BusinessException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}catch(Exception e){throw unavailable();}
        finally{body.cancel();if(future!=null)future.cancel(true);}
    }
    private static String text(JsonNode n,String key){if(!n.path(key).isTextual())throw invalid();return n.get(key).asText();}
    private static void fields(JsonNode n,Set<String> expected){if(n==null||!n.isObject())throw invalid();var keys=new HashSet<String>();n.fieldNames().forEachRemaining(keys::add);if(!keys.equals(expected))throw invalid();}
    public static BusinessException unavailable(){return new BusinessException("MARKET_USE_UNAVAILABLE","行情使用状态暂不能核实，原请求保留",HttpStatus.SERVICE_UNAVAILABLE);}
    public static BusinessException notReady(){return new BusinessException("MARKET_USE_NOT_READY","行情使用保护尚未就绪",HttpStatus.SERVICE_UNAVAILABLE);}
    public static BusinessException invalid(){return new BusinessException("MARKET_USE_RESPONSE_INVALID","行情使用回执未通过核对",HttpStatus.SERVICE_UNAVAILABLE);}
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private final CompletableFuture<byte[]> result=new CompletableFuture<>();private final AtomicReference<Flow.Subscription> subscription=new AtomicReference<>();
        public CompletionStage<byte[]> getBody(){return result;}public void onSubscribe(Flow.Subscription s){if(!subscription.compareAndSet(null,s)||result.isDone()){s.cancel();return;}s.request(1);}
        public void onNext(List<ByteBuffer> chunks){if(result.isDone())return;for(var chunk:chunks){if(chunk.remaining()>65536-bytes.size()){cancel();return;}var data=new byte[chunk.remaining()];chunk.get(data);bytes.writeBytes(data);}var s=subscription.get();if(s!=null&&!result.isDone())s.request(1);}
        public void onError(Throwable error){result.completeExceptionally(error);}public void onComplete(){result.complete(bytes.toByteArray());}void cancel(){var s=subscription.get();if(s!=null)s.cancel();result.completeExceptionally(new IOException("Bounded market response cancelled"));}
    }
}
