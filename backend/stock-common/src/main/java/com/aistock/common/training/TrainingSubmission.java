package com.aistock.common.training;

import com.aistock.common.BusinessException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;

public record TrainingSubmission(String requestId,List<String> configurationIds) {
    private static final int MAX_BODY_BYTES=2048;
    private static final ObjectMapper JSON=new ObjectMapper()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public TrainingSubmission {
        if(requestId==null||!requestId.matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")
            ||configurationIds==null||configurationIds.isEmpty()||configurationIds.size()>3)throw invalid();
        var sorted=new TreeSet<String>();
        for(String id:configurationIds) {
            if(!isConfigurationId(id)||!sorted.add(id))throw invalid();
        }
        configurationIds=List.copyOf(sorted);
    }

    public static boolean isConfigurationId(String id) {
        return id!=null&&id.matches("[a-z0-9][a-z0-9-]{0,63}");
    }

    public static TrainingSubmission parse(byte[] body) {
        if(body==null||body.length>MAX_BODY_BYTES)throw invalid();
        try {
            var node=JSON.readTree(body);
            if(node==null||!node.isObject()||node.size()!=2||!node.path("requestId").isTextual())throw invalid();
            var configs=node.path("configurationIds");
            if(!configs.isArray()||configs.isEmpty()||configs.size()>3)throw invalid();
            var ids=new ArrayList<String>();
            for(var value:configs) {
                if(!value.isTextual())throw invalid();
                ids.add(value.textValue());
            }
            return new TrainingSubmission(node.path("requestId").textValue(),ids);
        } catch(BusinessException error) {throw error;}
        catch(java.io.IOException error) {throw invalid();}
    }

    private static BusinessException invalid() {
        return new BusinessException("TRAINING_REQUEST_INVALID","训练请求格式不正确",HttpStatus.BAD_REQUEST);
    }
}
