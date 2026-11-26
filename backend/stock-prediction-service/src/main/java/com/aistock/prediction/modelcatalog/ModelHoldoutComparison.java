package com.aistock.prediction.modelcatalog;

import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;

final class ModelHoldoutComparison {
    private final ModelRepository repo;
    private final ObjectMapper mapper;
    private final String token;
    private final URI endpoint;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    ModelHoldoutComparison(ModelRepository repo,ObjectMapper mapper,String token,String algorithmUrl) {
        this.repo=repo;
        this.mapper=mapper;
        this.token=token;
        this.endpoint=URI.create(algorithmUrl).resolve("/internal/v1/training-executions/compare-models");
    }

    record Pair(ModelRecords.Version candidate, ModelRecords.Version reference, long headRevision) {}
    record Result(UUID candidateId, UUID referenceId, long headRevision, JsonNode metrics) {}

    Result compare(UUID id) {
        var pair=repo.locked(head->{
            var candidate=repo.find(id,false);
            if(candidate.evaluation().rolling()==null)throw conflict("该版本没有可复算的滚动留出集");
            if(head.currentModelId()==null||head.currentModelId().equals(id))
                throw conflict("请选择一款不同于当前推荐的滚动候选");
            var reference=repo.find(head.currentModelId(),false);
            if(!"PUBLISHED".equals(reference.state()))throw conflict("当前没有可用的已发布推荐模型");
            return new Pair(candidate,reference,head.revision());
        });
        var metrics=calculate(pair);
        if(repo.head().revision()!=pair.headRevision())throw conflict("推荐模型已变化，请刷新后重新对照");
        return new Result(id,pair.reference().modelId(),pair.headRevision(),metrics);
    }

    private JsonNode calculate(Pair pair) {
        if(token.isBlank())throw unavailable();
        try {
            var body=mapper.writeValueAsBytes(Map.of(
                "candidate",identity(pair.candidate()),"reference",identity(pair.reference())));
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                .header("X-Service-Token",token).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var subscriber=new AlgorithmArtifactInspector.LimitedBodySubscriber(16384);
            var pending=client.sendAsync(request,ignored->subscriber);
            try {
                var response=pending.get(15,TimeUnit.SECONDS);
                var envelope=mapper.readTree(response.body());
                if(response.statusCode()==409&&"MODEL_COMPARISON_BUSY".equals(envelope.path("code").asText()))
                    throw conflict("已有模型对照正在计算，请稍后重试");
                var data=envelope.path("data");
                var evaluation=pair.candidate().evaluation().rolling();
                if(response.statusCode()!=200||!"OK".equals(envelope.path("code").asText())
                    ||!evaluation.datasetHash().equals(data.path("datasetHash").textValue())
                    ||!pair.candidate().manifest().path("trainingData").path("holdout").equals(data.path("holdout"))
                    ||data.path("windows").intValue()!=evaluation.holdoutWindows()
                    ||!matches(data.path("candidate"),pair.candidate())
                    ||!matches(data.path("reference"),pair.reference())
                    ||!scores(data.path("baseline")))throw unavailable();
                return data;
            }finally{
                pending.cancel(true);
                subscriber.cancel();
            }
        }catch(BusinessException error){
            throw error;
        }catch(InterruptedException error){
            Thread.currentThread().interrupt();
            throw unavailable();
        }catch(Exception error){
            throw unavailable();
        }
    }

    private static Map<String,String> identity(ModelRecords.Version version) {
        return Map.of("artifactId",version.artifactId(),"manifestHash",version.manifestHash());
    }

    private static boolean matches(JsonNode score, ModelRecords.Version version) {
        return version.artifactId().equals(score.path("artifactId").textValue())
            &&version.manifestHash().equals(score.path("manifestHash").textValue())&&scores(score);
    }

    private static boolean scores(JsonNode score) {
        for(var field:java.util.List.of("mae","rmse","direction")){
            var value=score.path(field);
            if(!value.isNumber()||!Double.isFinite(value.doubleValue())||value.doubleValue()<0
                ||"direction".equals(field)&&value.doubleValue()>1)return false;
        }
        return true;
    }

    private static BusinessException conflict(String message) {
        return new BusinessException("MODEL_COMPARISON_NOT_READY",message,HttpStatus.CONFLICT);
    }

    private static BusinessException unavailable() {
        return new BusinessException("MODEL_COMPARISON_UNAVAILABLE","同样本对照暂不可用，请检查留出快照或稍后重试",HttpStatus.SERVICE_UNAVAILABLE);
    }
}
