package com.aistock.scheduler.task;

import com.aistock.scheduler.training.TrainingExecutionQueue.Claim;
import com.aistock.scheduler.training.TrainingExecutionWorker;
import com.aistock.scheduler.training.TrainingExecutionWorker.Receipt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class HttpTrainingExecutionClient implements TrainingExecutionWorker.Algorithm {
    private static final String PATH="/internal/v1/training-executions";
    private final String base;
    private final PredictionTaskHttp http;

    public HttpTrainingExecutionClient(String base,String token,ObjectMapper mapper) {
        var uri=URI.create(base);
        if(uri.getScheme()==null||!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null
            ||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null
            ||!Set.of("","/").contains(uri.getPath())||token==null||token.isBlank())
            throw new IllegalArgumentException("Invalid training algorithm settings");
        this.base=base;
        this.http=new PredictionTaskHttp(token,mapper);
    }

    @Override public Optional<Receipt> find(Claim claim) {
        try {return Optional.of(call(claim,false));}
        catch(PredictionTaskHttp.Remote error) {
            if(error.status==404&&"TRAINING_EXECUTION_NOT_FOUND".equals(error.code))return Optional.empty();
            throw error;
        }
    }

    @Override public Receipt submit(Claim claim) {
        return call(claim,true);
    }

    private Receipt call(Claim claim,boolean write) {
        var item=claim.item();
        var fields=Map.of("taskId",item.taskId(),"token",claim.token(),"configurationId",item.configurationId(),
            "configurationHash",item.configurationHash(),"datasetHash",item.datasetHash());
        var reply=http.call(base,write?PATH:PATH+"/"+item.taskId(),write?fields:null,Duration.ofSeconds(4));
        var data=reply.path("data");
        if(!"OK".equals(reply.path("code").textValue())||!data.isObject()||data.size()!=7)
            throw new IllegalStateException("Invalid training execution receipt");
        for(var field:fields.entrySet())
            if(!field.getValue().equals(data.path(field.getKey()).textValue()))
                throw new IllegalStateException("Training execution receipt does not match the original claim");
        String state=data.path("state").asText();
        var checkpoint=data.path("checkpointHash");
        if(!List.of("RUNNING","FAILED","SUCCEEDED").contains(state)
            ||("SUCCEEDED".equals(state)?(!checkpoint.isTextual()||!checkpoint.textValue().matches("[a-f0-9]{64}")):!checkpoint.isNull()))
            throw new IllegalStateException("Invalid training execution state");
        return new Receipt(claim,state,checkpoint.textValue());
    }
}
