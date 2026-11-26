package com.aistock.prediction.training;

import com.aistock.prediction.training.TrainingAdmissionRepository.Item;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class TrainingSchedulerClient implements TrainingDispatchWorker.Scheduler {
    private static final String PATH="/internal/v1/tasks/training-inbox";
    private final URI base;
    private final String token;
    private final Duration timeout;
    private final ObjectMapper json=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public TrainingSchedulerClient(String base,String token,Duration timeout) {
        this.base=URI.create(base);
        if(this.base.getScheme()==null||!Set.of("http","https").contains(this.base.getScheme())||this.base.getHost()==null
            ||this.base.getUserInfo()!=null||this.base.getQuery()!=null||this.base.getFragment()!=null
            ||!Set.of("","/").contains(this.base.getPath())||token==null||token.isBlank()
            ||timeout==null||timeout.toMillis()<1||timeout.compareTo(Duration.ofSeconds(4))>0)
            throw new IllegalArgumentException("Invalid training scheduler settings");
        this.token=token;
        this.timeout=timeout;
    }

    @Override public Optional<Item> find(Item item) {
        return call(item,false);
    }

    @Override public Item receive(Item item) {
        return call(item,true).orElseThrow(TrainingSchedulerClient::unavailable);
    }

    private Optional<Item> call(Item item,boolean post) {
        if(item==null||item.taskId()==null||!item.taskId().matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}"))
            throw unavailable();
        var subscriber=new BoundedBody();
        CompletableFuture<HttpResponse<byte[]>> future=null;
        try {
            var request=HttpRequest.newBuilder(base.resolve(post?PATH:PATH+"/"+item.taskId()))
                .timeout(timeout).header("X-Service-Token",token).header("Accept","application/json");
            if(post)request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(item)));
            else request.GET();
            future=http.sendAsync(request.build(),ignored->subscriber);
            var response=future.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
            var envelope=json.readTree(response.body());
            if(envelope==null||!envelope.isObject()||!envelope.path("code").isTextual())throw unavailable();
            String code=envelope.path("code").textValue();
            if(!post&&response.statusCode()==404&&"TRAINING_DISPATCH_NOT_FOUND".equals(code)&&envelope.path("data").isNull())
                return Optional.empty();
            if(post&&response.statusCode()==409&&"TRAINING_DISPATCH_CONFLICT".equals(code)&&envelope.path("data").isNull())
                throw new TrainingDispatchWorker.Conflict();
            var data=envelope.path("data");
            if(response.statusCode()!=200||!"OK".equals(code)||!data.isObject()||data.size()!=4)throw unavailable();
            var fields=List.of("taskId","configurationId","configurationHash","datasetHash");
            for(String field:fields)if(!data.path(field).isTextual())throw unavailable();
            var receipt=json.treeToValue(data,Item.class);
            if(!item.equals(receipt))throw unavailable();
            return Optional.of(receipt);
        } catch(TrainingDispatchWorker.Conflict error) {throw error;}
        catch(InterruptedException error) {Thread.currentThread().interrupt();throw unavailable();}
        catch(Exception error) {throw unavailable();}
        finally {
            subscriber.cancel();
            if(future!=null)future.cancel(true);
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Training handoff receipt unavailable");
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final AtomicReference<Flow.Subscription> subscription=new AtomicReference<>();
        @Override public CompletionStage<byte[]> getBody(){return result;}
        @Override public void onSubscribe(Flow.Subscription next) {
            if(!subscription.compareAndSet(null,next)||result.isDone()){next.cancel();return;}
            next.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if(result.isDone())return;
            for(var buffer:buffers) {
                if(buffer.remaining()>4096-bytes.size()){cancel();return;}
                var block=new byte[buffer.remaining()];
                buffer.get(block);
                bytes.writeBytes(block);
            }
            var current=subscription.get();
            if(current!=null&&!result.isDone())current.request(1);
        }
        @Override public void onError(Throwable error){result.completeExceptionally(error);}
        @Override public void onComplete(){result.complete(bytes.toByteArray());}
        void cancel() {
            var current=subscription.get();
            if(current!=null)current.cancel();
            result.completeExceptionally(new IOException("Training scheduler response cancelled"));
        }
    }
}
