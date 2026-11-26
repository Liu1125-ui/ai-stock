package com.aistock.market.marketlifecycle;

import com.aistock.common.marketlifecycle.*;
import com.aistock.common.marketlifecycle.MarketUseRecords.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.aistock.market.marketlifecycle.MarketLifecycleRecords.*;

/** Fixed owner URLs and bounded transport. Missing/unknown proofs never authorize release. */
public class MarketUseProofClient {
    private final Map<String,URI> urls;private final String token;private final Duration timeout;
    private final ObjectMapper json=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(1)).build();
    public MarketUseProofClient(Map<String,String> bases,String token,Duration timeout) {
        if(!bases.keySet().equals(Set.of("prediction","notification","market"))||timeout==null||timeout.isNegative()||timeout.isZero()||timeout.compareTo(Duration.ofSeconds(4))>0)throw new IllegalArgumentException("Invalid proof client configuration");
        var map=new HashMap<String,URI>();bases.forEach((owner,text)->{var uri=URI.create(text);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!Set.of("","/").contains(uri.getPath()))throw new IllegalArgumentException("Invalid fixed owner URL");map.put(owner,uri);});urls=Map.copyOf(map);this.token=token;this.timeout=timeout;
    }
    public Optional<TerminalProof> read(Bundle b) {
        if(token==null||token.isBlank())return Optional.empty();
        CompletableFuture<HttpResponse<byte[]>> pending=null;var body=new LimitedBody();
        try {
            MarketLifecycleContract.identity(b.identity());var expected=MarketLifecycleContract.versionSet(b.versionIds());
            var id=b.identity();var path="/internal/v1/market-version-use-proofs/"+id.purpose()+"/"+id.ownerOperationId()+"?generation="+id.generation();
            var request=HttpRequest.newBuilder(urls.get(id.owner()).resolve(path)).timeout(timeout).header("X-Service-Token",token).header("Accept","application/json").GET().build();
            pending=http.sendAsync(request,info->body);var response=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
            if(response.statusCode()!=200)return Optional.empty();var root=json.readTree(response.body());
            if(root==null||!root.isObject()||!"OK".equals(root.path("code").asText()))return Optional.empty();
            var n=root.path("data");MarketUseProtocol.fields(n,Set.of("owner","purpose","ownerOperationId","generation","state","terminalAt","fencingRevision","resultIdentity","versionIds"));
            var actual=MarketUseProtocol.identity(n);if(!id.equals(actual))return Optional.empty();
            var state=MarketUseProtocol.text(n,"state");if(!Set.of("SUCCEEDED","FAILED","CANCELLED").contains(state))return Optional.empty();
            var at=Instant.parse(MarketUseProtocol.text(n,"terminalAt"));var fence=MarketUseProtocol.text(n,"fencingRevision");MarketLifecycleContract.generation(fence);
            var result=MarketUseProtocol.text(n,"resultIdentity");MarketLifecycleContract.id(result);
            if(!n.path("versionIds").isArray())return Optional.empty();var values=new ArrayList<String>();for(var value:n.get("versionIds")){if(!value.isTextual())return Optional.empty();values.add(value.asText());}
            var versions=MarketLifecycleContract.versionSet(values);if(!expected.equals(versions))return Optional.empty();
            return Optional.of(new TerminalProof(actual,state,at,fence,result,versions));
        }catch(InterruptedException e){Thread.currentThread().interrupt();return Optional.empty();}
        catch(IOException|ExecutionException|TimeoutException|RuntimeException e){return Optional.empty();}
        finally{body.cancel();if(pending!=null)pending.cancel(true);}
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final ByteArrayOutputStream out=new ByteArrayOutputStream();private final CompletableFuture<byte[]> result=new CompletableFuture<>();private volatile Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> chunks){for(var chunk:chunks){if(chunk.remaining()>65536-out.size()){subscription.cancel();result.completeExceptionally(new IOException("Proof exceeds limit"));return;}var bytes=new byte[chunk.remaining()];chunk.get(bytes);out.writeBytes(bytes);}subscription.request(1);}
        public void onError(Throwable error){result.completeExceptionally(error);}public void onComplete(){result.complete(out.toByteArray());}
        void cancel(){var s=subscription;if(s!=null)s.cancel();result.cancel(true);}
    }
}
