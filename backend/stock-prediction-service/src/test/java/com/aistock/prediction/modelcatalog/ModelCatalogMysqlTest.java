package com.aistock.prediction.modelcatalog;
import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.prediction.modelcatalog.ModelRecords.*;
@Tag("mysql")
class ModelCatalogMysqlTest {
    static final ObjectMapper MAPPER=new ObjectMapper().findAndRegisterModules();
    static final Clock NOW=Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"),ZoneOffset.UTC);
    static Registration candidate(int seed){return new Registration("transformer-s3-2023-seed"+seed,"a".repeat(64),new Evaluation("b".repeat(64),"RETROSPECTIVE_DEVELOPMENT",seed,2024,"0.032","0.0321","0.0237","0.0311","0.4775"));}
    static JsonNode manifest(String id){
        try{return MAPPER.readTree("""
            {"schemaVersion":1,"artifactId":"%s","status":"CANDIDATE","checkpointHash":"%s","trainingCutoff":"2023-12-31","epoch":3,
             "config":{"seq_len":60,"label_len":30,"pred_len":5,"enc_in":6,"dec_in":6,"c_out":1},
             "instruments":["SH600030","SH600276","SZ000002"],"features":["open","high","low","close","vwap","volume"],
             "featureDefinition":"baostock-six-price-volume-v1","sourceDefinition":"baostock-backward-close-over-raw-v1","targetDefinition":"adjusted-close-relative-origin-v1"}
            """.formatted(id,"c".repeat(64)));}catch(Exception e){throw new AssertionError(e);}
    }
    static ModelCatalogService service(ModelDatabaseFixture f){return new ModelCatalogService(new ModelRepository(f.jdbc,MAPPER),ModelCatalogMysqlTest::inspect,NOW,MAPPER);}
    static JsonNode inspect(String id,String hash){return manifest(id);}
    static void code(String expected,Runnable action){assertEquals(expected,assertThrows(BusinessException.class,action::run).code());}
    @Test void registrationIsImmutableAndPendingAcrossNewServiceInstance(){
        var f=new ModelDatabaseFixture();var s=service(f);var first=s.register(candidate(42));
        assertEquals(first.modelId(),s.register(candidate(42)).modelId());assertEquals("PENDING",first.state());assertEquals(0,first.revision());
        assertTrue(s.published().isEmpty());assertNull(s.head().currentModelId());assertEquals(1,service(f).list().size());
        assertEquals(1,f.jdbc.queryForObject("select count(*) from model_release_event",Integer.class));
    }
    @Test void differentEvaluationCannotOverwriteRegisteredVersion(){
        var f=new ModelDatabaseFixture();var s=service(f);var c=candidate(42);var first=s.register(c);
        var e=c.evaluation();var changed=new Evaluation(e.sourceHash(),e.scope(),42,2024,"0.001",e.validationBaselineMae(),e.mae2025(),e.mae2026(),e.direction2026());
        code("MODEL_REGISTRATION_CONFLICT",()->s.register(new Registration(c.artifactId(),c.manifestHash(),changed)));
        assertEquals(first,s.list().get(0));assertEquals(1,f.jdbc.queryForObject("select count(*) from model_version",Integer.class));
    }
    @Test void badReportNumbersAndSeedRejectedBeforeNetwork(){
        var f=new ModelDatabaseFixture();var calls=new AtomicInteger();var s=new ModelCatalogService(new ModelRepository(f.jdbc,MAPPER),(id,h)->{calls.incrementAndGet();return manifest(id);},NOW,MAPPER);
        for(var bad:List.of("1E+100000000","NaN","-1","9".repeat(100))){var e=new Evaluation("b".repeat(64),"RETROSPECTIVE_DEVELOPMENT",42,2024,bad,"0.03","0.03","0.03","0.5");code("MODEL_REGISTRATION_INVALID",()->s.register(new Registration(candidate(42).artifactId(),"a".repeat(64),e)));}
        code("MODEL_REGISTRATION_INVALID",()->s.register(new Registration(candidate(42).artifactId(),"a".repeat(64),candidate(43).evaluation())));assertEquals(0,calls.get());
    }
    @Test void mismatchedDescriptorOrUnavailableInspectionDoesNotInsert(){
        var f=new ModelDatabaseFixture();var repo=new ModelRepository(f.jdbc,MAPPER);
        var s=new ModelCatalogService(repo,(id,h)->manifest(candidate(43).artifactId()),NOW,MAPPER);
        code("MODEL_ARTIFACT_INCOMPATIBLE",()->s.register(candidate(42)));
        var failed=new ModelCatalogService(repo,(id,h)->{throw new BusinessException("MODEL_ARTIFACT_UNAVAILABLE","模型暂不可用",org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);},NOW,MAPPER);
        code("MODEL_ARTIFACT_UNAVAILABLE",()->failed.register(candidate(42)));assertEquals(0,f.jdbc.queryForObject("select count(*) from model_version",Integer.class));
    }
    @Test void concurrentSameCandidateReusesOneVersion()throws Exception{
        var f=new ModelDatabaseFixture();var s=service(f);var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->s.register(candidate(42)));var b=pool.submit(()->s.register(candidate(42)));assertEquals(a.get(10,TimeUnit.SECONDS).modelId(),b.get(10,TimeUnit.SECONDS).modelId());assertEquals(1,s.list().size());}finally{pool.shutdownNow();}
    }

    @Test void rollingCandidateBindsDatasetAndRemainsPendingWithoutReplacingLegacy()throws Exception {
        var fixture=new ModelDatabaseFixture();
        var legacy=service(fixture).register(candidate(42));
        String id="transformer-s3-rolling-20241231-seed42-"+"a".repeat(16);
        var metadata=(com.fasterxml.jackson.databind.node.ObjectNode)manifest(id);
        metadata.put("schemaVersion",2).put("trainingCutoff","2024-12-31");
        metadata.set("trainingData",MAPPER.readTree("""
            {"datasetHash":"%s","seed":42,"train":{"from":"2023-01-01","through":"2024-12-31"},
            "validation":{"from":"2025-01-01","through":"2025-06-30"},
            "holdout":{"from":"2025-07-01","through":"2026-09-24"}}
            """.formatted("d".repeat(64))));
        var repo=new ModelRepository(fixture.jdbc,MAPPER);
        var catalog=new ModelCatalogService(repo,(artifact,hash)->metadata,NOW,MAPPER);
        var rolling=new RollingEvaluation("d".repeat(64),"0.023","0.022","0.49",593);
        var evaluation=new Evaluation("b".repeat(64),"ROLLING_OUT_OF_SAMPLE",42,null,
            "0.02","0.021",null,null,null,rolling);
        var input=new Registration(id,"e".repeat(64),evaluation);
        var first=catalog.register(input);
        assertEquals("PENDING",first.state());
        assertEquals(first.modelId(),catalog.register(input).modelId());
        assertEquals("2023-12-31",repo.find(legacy.modelId(),false).manifest().path("trainingCutoff").asText());
        assertTrue(catalog.published().isEmpty());
        var changed=new RollingEvaluation("f".repeat(64),"0.023","0.022","0.49",593);
        code("MODEL_ARTIFACT_INCOMPATIBLE",()->catalog.register(new Registration(id,"e".repeat(64),
            new Evaluation(evaluation.sourceHash(),evaluation.scope(),42,null,"0.02","0.021",null,null,null,changed))));
    }
}
