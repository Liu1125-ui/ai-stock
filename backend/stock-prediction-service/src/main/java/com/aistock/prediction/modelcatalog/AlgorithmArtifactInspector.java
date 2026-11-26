package com.aistock.prediction.modelcatalog;
import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
public final class AlgorithmArtifactInspector implements ArtifactInspector {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final URI endpoint;private final String token;private final ObjectMapper mapper;
    public AlgorithmArtifactInspector(String url,String token,ObjectMapper mapper){this.endpoint=URI.create(url).resolve("/internal/v1/model-artifacts/inspect");this.token=token;this.mapper=mapper;}
    public JsonNode inspect(String id,String hash){
        if(token.isBlank())throw unavailable();
        try{
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15)).header("X-Service-Token",token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(ModelRecords.json(mapper,Map.of("artifactId",id,"manifestHash",hash)))).build();
            var subscriber=new LimitedBodySubscriber(131072);
            var pending=client.sendAsync(request,info->subscriber);
            try{
                // Future completion includes the whole bounded body, not just response headers.
                var response=pending.get(15,TimeUnit.SECONDS);
                var bytes=response.body();if(response.statusCode()!=200)throw unavailable();
                var body=mapper.readTree(bytes);var result=body.path("result");var manifest=result.path("manifest");
                if(!"AVAILABLE".equals(body.path("state").asText())||!result.path("inspectionOnly").asBoolean()||!id.equals(result.path("artifactId").asText())||!hash.equals(result.path("manifestHash").asText())||!manifest.path("checkpointHash").equals(result.path("checkpointHash"))||result.has("points"))throw unavailable();
                ModelRecords.checkManifest(id,manifest);return manifest;
            }finally{pending.cancel(true);subscriber.cancel();}
        }catch(BusinessException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}catch(Exception e){throw unavailable();}
    }
    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body=new CompletableFuture<>();
        private final AtomicReference<Flow.Subscription> subscription=new AtomicReference<>();
        LimitedBodySubscriber(int limit){this.limit=limit;}
        @Override public CompletionStage<byte[]> getBody(){return body;}
        @Override public void onSubscribe(Flow.Subscription incoming){
            if(!subscription.compareAndSet(null,incoming)||body.isDone()){incoming.cancel();return;}incoming.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers){
            if(body.isDone())return;
            for(var buffer:buffers){
                if(buffer.remaining()>limit-bytes.size()){cancel();return;}
                var chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }
            var active=subscription.get();if(active!=null&&!body.isDone())active.request(1);
        }
        @Override public void onError(Throwable error){body.completeExceptionally(error);}
        @Override public void onComplete(){body.complete(bytes.toByteArray());}
        void cancel(){var active=subscription.get();if(active!=null)active.cancel();body.completeExceptionally(new java.io.IOException("Inspection body cancelled or oversized"));}
    }
    private BusinessException unavailable(){return new BusinessException("MODEL_ARTIFACT_UNAVAILABLE","模型检查暂不可用，已有发布状态保留",HttpStatus.SERVICE_UNAVAILABLE);}
}
