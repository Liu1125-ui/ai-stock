package com.aistock.prediction.modelcatalog;

import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.List;

import static com.aistock.prediction.modelcatalog.ModelRecords.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelPoolTest {
    @Test void poolRegistrationMustMatchEveryStockAndItsSavedEvidence() throws Exception {
        var id="transformer-s3-pool-"+"a".repeat(16);
        var metrics=List.of(
            new ExpansionEvaluation("SH601633","2025-01-02","2026-11-19",79,"0.0194","0.0196","0.5"),
            new ExpansionEvaluation("SZ002304","2025-01-02","2026-11-19",79,"0.0177","0.0178","0.5"));
        var eval=new Evaluation("a".repeat(64),"STOCK_POOL",43,null,null,null,null,null,null,null,null,metrics);
        var input=new Registration(id,"b".repeat(64),eval);
        var mapper=new ObjectMapper();
        assertEquals(input,mapper.readValue(mapper.writeValueAsBytes(input),Registration.class));
        var manifest=(ObjectNode)ModelCatalogMysqlTest.manifest(ModelCatalogMysqlTest.candidate(43).artifactId());
        manifest.put("artifactId",id).put("schemaVersion",4);
        manifest.putArray("instruments").add("SH601633").add("SZ002304");
        var pool=manifest.putObject("pool").put("parentArtifactId","transformer-s3-2023-seed43")
            .put("parentManifestHash","c".repeat(64)).put("evidenceHash","a".repeat(64));
        var stocks=pool.putArray("stocks");
        for(var entry:metrics){
            var node=mapper.<ObjectNode>valueToTree(entry).put("evidenceHash","d".repeat(64));
            stocks.add(node);
        }

        validate(input);
        checkManifest(id,manifest);
        checkEvaluationBinding(input,manifest);
        ((ObjectNode)stocks.get(1)).put("windows",70);
        assertThrows(BusinessException.class,()->checkEvaluationBinding(input,manifest));
        manifest.withArray("instruments").add("SZ002216");
        assertThrows(BusinessException.class,()->checkManifest(id,manifest));
    }
}
