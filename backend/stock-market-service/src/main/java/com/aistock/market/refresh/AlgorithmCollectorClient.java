package com.aistock.market.refresh;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.ByteArrayOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component public class AlgorithmCollectorClient implements MarketCollector {
    private final String url,token;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    public AlgorithmCollectorClient(@Value("${aistock.algorithm.url:http://127.0.0.1:18090}")String url,@Value("${aistock.internal.token:}")String token){this.url=url;this.token=token;}
    public Collected collect(String operationId,String instrumentId,LocalDate start,LocalDate end) {
        try {
            var payload=mapper.writeValueAsString(Map.of("operationId",operationId,"provider","baostock","instrumentId",instrumentId,"start",start.toString(),"end",end.toString()));
            var request=HttpRequest.newBuilder(URI.create(url+"/internal/v1/market/collect")).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").header("X-Service-Token",token).POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            var response=client.send(request,info->new LimitedBody(2097152));
            if(response.statusCode()!=200)return new Collected(operationId,"SOURCE_UNAVAILABLE",null,"SOURCE_UNAVAILABLE");
            var root=mapper.readTree(response.body());
            if(!root.isObject() || !Set.of("operationId","state","candidate","errorCode","retryAt","valuation").containsAll(fieldNames(root)))return new Collected(operationId,"SOURCE_INVALID",null,"SOURCE_INVALID");
            return new Collected(root.path("operationId").asText(null),root.path("state").asText(null),root.path("candidate").isNull()?null:mapper.treeToValue(root.path("candidate"),com.aistock.market.ingestion.MarketCandidate.class),root.path("errorCode").asText(null),parseValuation(root.path("valuation")));
        } catch(java.net.http.HttpTimeoutException e){return new Collected(operationId,"SOURCE_TIMEOUT",null,"SOURCE_TIMEOUT");}
        catch(InterruptedException e){Thread.currentThread().interrupt();return new Collected(operationId,"SOURCE_UNAVAILABLE",null,"SOURCE_UNAVAILABLE");}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){return new Collected(operationId,"SOURCE_INVALID",null,"SOURCE_INVALID");}
        catch(java.io.IOException e){return new Collected(operationId,hasPayloadLimit(e)?"SOURCE_INVALID":"SOURCE_UNAVAILABLE",null,hasPayloadLimit(e)?"SOURCE_INVALID":"SOURCE_UNAVAILABLE");}
        catch(Exception e){return new Collected(operationId,"SOURCE_INVALID",null,"SOURCE_INVALID");}
    }
    private com.aistock.market.quotation.StockValuationStore.Sample parseValuation(JsonNode node) {
        if (!node.isObject() || !fieldNames(node).equals(Set.of("tradeDate","peTtm","pbMrq"))
                || !node.path("tradeDate").isTextual()) return null;
        try {
            return new com.aistock.market.quotation.StockValuationStore.Sample(
                    LocalDate.parse(node.get("tradeDate").textValue()),
                    node.path("peTtm").textValue(), node.path("pbMrq").textValue());
        } catch (java.time.format.DateTimeParseException invalid) {
            return null;
        }
    }

    private boolean hasPayloadLimit(Throwable e){for(var cause=e;cause!=null;cause=cause.getCause())if(cause instanceof PayloadLimitException)return true;return false;}
    private static final class PayloadLimitException extends java.io.IOException {}
    private Set<String> fieldNames(JsonNode node){var set=new HashSet<String>();node.fieldNames().forEachRemaining(set::add);return set;}
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;private final ByteArrayOutputStream output=new ByteArrayOutputStream();private final CompletableFuture<byte[]> future=new CompletableFuture<>();private volatile Flow.Subscription subscription;
        LimitedBody(int limit){this.limit=limit;}
        public CompletionStage<byte[]> getBody(){return future;}
        public void onSubscribe(Flow.Subscription value){subscription=value;value.request(1);}
        public void onNext(List<ByteBuffer> chunks){
            for(var chunk:chunks){if(chunk.remaining()>limit-output.size()){subscription.cancel();future.completeExceptionally(new PayloadLimitException());return;}var bytes=new byte[chunk.remaining()];chunk.get(bytes);output.writeBytes(bytes);}subscription.request(1);
        }
        void cancel(){var active=subscription;if(active!=null)active.cancel();future.cancel(true);}
        public void onError(Throwable error){future.completeExceptionally(error);}
        public void onComplete(){future.complete(output.toByteArray());}
    }
}
