package com.aistock.prediction.training;

import com.aistock.common.BusinessException;
import com.aistock.common.training.TrainingProgress;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class TrainingProgressService {
    public record Status(String taskId,String configurationId,String state,Instant startedAt,Instant finishedAt,
                         UUID modelId) {}

    private final TrainingProgressClients.Scheduler scheduler;
    private final TrainingProgressClients.Algorithm algorithm;
    private final String token;

    public TrainingProgressService(TrainingProgressClients.Scheduler scheduler,TrainingProgressClients.Algorithm algorithm,String token) {
        this.scheduler=scheduler;
        this.algorithm=algorithm;
        this.token=token;
    }

    public Status status(TrainingAdmissionRepository.OwnedItem item) {
        String state=switch(item.dispatchState()) {
            case "PENDING" -> "AWAITING_DISPATCH";
            case "CONFLICT" -> "DISPATCH_CONFLICT";
            default -> "STATUS_UNAVAILABLE";
        };
        if("CONFIRMED".equals(item.dispatchState())) {
            try {
                var result=readStatus(item);
                return new Status(item.taskId(),item.configurationId(),result.state(),result.startedAt(),result.finishedAt(),result.registeredModelId());
            } catch(RuntimeException ignored) { /* Keep the saved submission visible. */ }
        }
        return new Status(item.taskId(),item.configurationId(),state,null,null,null);
    }

    public JsonNode metrics(TrainingAdmissionRepository.OwnedItem item) {
        if(!"CONFIRMED".equals(item.dispatchState()))throw unavailable();
        try {
            var status=readStatus(item);
            if(!"SUCCEEDED".equals(status.state())||status.checkpointHash()==null)throw unavailable();
            var response=algorithm.metrics(item.taskId(),token);
            if(response==null||!"OK".equals(response.code())||response.data()==null)throw unavailable();
            var report=response.data();
            if(!item.datasetHash().equals(report.path("datasetHash").textValue())
                ||!status.checkpointHash().equals(report.path("checkpointHash").textValue())
                ||!report.path("epochHistory").isArray()||report.path("epochHistory").isEmpty()
                ||report.path("epochHistory").size()>6)throw unavailable();
            return publicMetrics(report);
        } catch(RuntimeException error) {throw unavailable();}
    }

    private JsonNode publicMetrics(JsonNode report) {
        var result=JsonNodeFactory.instance.objectNode();
        result.put("seed",integer(report,"seed",0));
        int selectedEpoch=integer(report,"selectedEpoch",1);
        result.put("selectedEpoch",selectedEpoch);
        result.put("trainingSamples",integer(report,"trainingSamples",1));
        result.put("elapsedSeconds",number(report,"elapsedSeconds"));
        var history=result.putArray("epochHistory");
        boolean foundSelected=false;
        int expectedEpoch=1;
        for(var source:report.path("epochHistory")) {
            int epoch=integer(source,"epoch",1);
            if(epoch!=expectedEpoch++)throw unavailable();
            foundSelected|=epoch==selectedEpoch;
            var row=history.addObject();
            row.put("epoch",epoch);
            row.put("trainingMse",number(source,"trainingMse"));
            row.set("validation",evaluation(source.path("validation")));
        }
        if(!foundSelected)throw unavailable();
        if(report.has("holdout")) {
            var span=report.path("trainingData").path("holdout");
            var start=reportDate(span.path("from").textValue());
            var end=reportDate(span.path("through").textValue());
            if(start.isAfter(end)||!report.path("datasetHash").equals(report.path("trainingData").path("datasetHash"))
                ||!report.path("seed").equals(report.path("trainingData").path("seed")))throw unavailable();
            var holdout=evaluation(report.path("holdout"));
            holdout.put("from",start.toString()).put("through",end.toString());
            result.set("holdout",holdout);
        } else {
            result.set("heldout2025",evaluation(report.path("heldout2025")));
            result.set("heldout2026",evaluation(report.path("heldout2026")));
        }
        return result;
    }

    private static java.time.LocalDate reportDate(String value) {
        if(value==null||!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw unavailable();
        return java.time.LocalDate.parse(value);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode evaluation(JsonNode source) {
        var evaluation=JsonNodeFactory.instance.objectNode();
        evaluation.put("mae",number(source,"mae"));
        evaluation.put("rmse",number(source,"rmse"));
        evaluation.put("baselineMae",number(source,"baselineMae"));
        double direction=number(source,"direction");
        if(direction>1)throw unavailable();
        evaluation.put("direction",direction);
        evaluation.put("samples",integer(source,"samples",1));
        return evaluation;
    }

    private static int integer(JsonNode source,String field,int minimum) {
        var value=source.path(field);
        if(!value.isIntegralNumber()||!value.canConvertToInt()||value.intValue()<minimum)throw unavailable();
        return value.intValue();
    }

    private static double number(JsonNode source,String field) {
        var value=source.path(field);
        if(!value.isNumber()||!Double.isFinite(value.doubleValue())||value.doubleValue()<0)throw unavailable();
        return value.doubleValue();
    }

    private TrainingProgress readStatus(TrainingAdmissionRepository.OwnedItem item) {
        if(token==null||token.isBlank())throw unavailable();
        var response=scheduler.status(item.taskId(),token);
        if(response==null||!"OK".equals(response.code())||response.data()==null)throw unavailable();
        var result=response.data();
        if(!item.taskId().equals(result.taskId())||!Set.of("QUEUED","RUNNING","SUCCEEDED","FAILED","CANCELLED").contains(result.state()))
            throw unavailable();
        return result;
    }

    private static BusinessException unavailable() {
        return new BusinessException("TRAINING_STATUS_UNAVAILABLE","训练状态或指标暂不可查",HttpStatus.SERVICE_UNAVAILABLE);
    }
}
