package com.aistock.prediction.training;

import com.aistock.common.training.TrainingSubmission;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TrainingConfigurationCatalog {
    private final Map<String,TrainingAdmissionRepository.Configuration> configurations;

    public TrainingConfigurationCatalog(ObjectMapper mapper,String configured) {
        var entries=new LinkedHashMap<String,TrainingAdmissionRepository.Configuration>();
        var reader=mapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try {
            var root=reader.readTree(configured);
            if(root==null||!root.isObject()||root.isEmpty()||root.size()>64)
                throw new IllegalArgumentException("Training catalog requires 1 to 64 configurations");
            var fields=root.fields();
            while(fields.hasNext()) {
                var entry=fields.next();
                if(!TrainingSubmission.isConfigurationId(entry.getKey()))throw new IllegalArgumentException("Invalid training configuration ID");
                var node=entry.getValue();
                if(!node.isObject()||node.size()!=2)throw new IllegalArgumentException("Invalid training catalog entry");
                var item=new TrainingAdmissionRepository.Configuration(node.path("configurationHash").textValue(),node.path("datasetHash").textValue());
                if(entries.containsValue(item))throw new IllegalArgumentException("Duplicate atomic training configuration");
                entries.put(entry.getKey(),item);
            }
        } catch(IOException error) {
            throw new IllegalArgumentException("Invalid training configuration catalog");
        }
        configurations=Map.copyOf(entries);
    }

    public java.util.List<String> ids() {return configurations.keySet().stream().sorted().toList();}

    public TrainingAdmissionRepository.Configuration find(String id) {
        return configurations.get(id);
    }
}
