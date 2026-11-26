package com.aistock.prediction.modelcatalog;
import com.aistock.common.BusinessException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.prediction.modelcatalog.ModelRecords.*;
@Tag("mysql")
class ModelReleaseMysqlTest {
    private static final Actor ADMIN=new Actor(UUID.randomUUID(),"ADMIN");
    private ModelReleaseService release(ModelRepository repo,ArtifactInspector inspector){return new ModelReleaseService(repo,inspector,ModelCatalogMysqlTest.NOW,ModelCatalogMysqlTest.MAPPER);}
    private ActionInput input(Version v,Head h){return new ActionInput(UUID.randomUUID(),v.revision(),h.revision(),"已核对回顾性实验和基线，接受当前模型的局限",true);}
    @Test void publishRecommendOfflineRollbackPreserveMetadataAndAudit(){
        var f=new ModelDatabaseFixture();var catalog=ModelCatalogMysqlTest.service(f);var original=catalog.register(ModelCatalogMysqlTest.candidate(42));var repo=new ModelRepository(f.jdbc,ModelCatalogMysqlTest.MAPPER);var s=release(repo,ModelCatalogMysqlTest::inspect);
        var published=s.mutate(ADMIN,original.modelId(),"publish",input(original,repo.head()));assertEquals("PUBLISHED",published.model().state());assertNull(published.head().currentModelId());assertEquals(1,catalog.published().size());
        var recommended=s.mutate(ADMIN,original.modelId(),"recommend",input(published.model(),repo.head()));assertEquals(original.modelId(),recommended.head().currentModelId());
        var offline=s.mutate(ADMIN,original.modelId(),"offline",input(recommended.model(),repo.head()));assertNull(offline.head().currentModelId());assertTrue(catalog.published().isEmpty());
        var restored=s.mutate(ADMIN,original.modelId(),"rollback",input(offline.model(),repo.head()));assertEquals("PUBLISHED",restored.model().state());assertEquals(original.modelId(),restored.head().currentModelId());
        assertEquals(original.manifest(),restored.model().manifest());assertEquals(original.evaluation(),restored.model().evaluation());assertEquals(5,repo.events(original.modelId()).size());assertEquals("REGISTER",repo.events(original.modelId()).get(0).action());
    }
    @Test void roleAcknowledgementAndStateAreChecked(){
        var f=new ModelDatabaseFixture();var catalog=ModelCatalogMysqlTest.service(f);var v=catalog.register(ModelCatalogMysqlTest.candidate(42));var repo=new ModelRepository(f.jdbc,ModelCatalogMysqlTest.MAPPER);var s=release(repo,ModelCatalogMysqlTest::inspect);
        ModelCatalogMysqlTest.code("ACCESS_DENIED",()->s.mutate(new Actor(UUID.randomUUID(),"USER"),v.modelId(),"publish",input(v,repo.head())));
        var a=new ActionInput(UUID.randomUUID(),0L,0L,"已核对回顾性实验和基线，接受当前模型的局限",false);
        ModelCatalogMysqlTest.code("MODEL_REVIEW_REQUIRED",()->s.mutate(ADMIN,v.modelId(),"publish",a));
        ModelCatalogMysqlTest.code("MODEL_STATE_CONFLICT",()->s.mutate(ADMIN,v.modelId(),"rollback",input(v,repo.head())));
        var rejected=s.mutate(ADMIN,v.modelId(),"reject",input(v,repo.head()));assertEquals("REJECTED",rejected.model().state());assertTrue(catalog.published().isEmpty());
    }
    @Test void operationReplayReturnsFrozenReceiptAfterHeadChangesButDifferentPayloadConflicts(){
        var f=new ModelDatabaseFixture();var catalog=ModelCatalogMysqlTest.service(f);var v=catalog.register(ModelCatalogMysqlTest.candidate(42));var repo=new ModelRepository(f.jdbc,ModelCatalogMysqlTest.MAPPER);var s=release(repo,ModelCatalogMysqlTest::inspect);var a=input(v,repo.head());var first=s.mutate(ADMIN,v.modelId(),"publish",a);
        s.mutate(ADMIN,v.modelId(),"recommend",input(first.model(),repo.head()));assertEquals(first,s.mutate(ADMIN,v.modelId(),"publish",a));
        ModelCatalogMysqlTest.code("MODEL_OPERATION_CONFLICT",()->s.mutate(ADMIN,v.modelId(),"offline",a));
        ModelCatalogMysqlTest.code("MODEL_OPERATION_CONFLICT",()->s.mutate(new Actor(UUID.randomUUID(),"ADMIN"),v.modelId(),"publish",a));
        assertEquals(3,repo.events(v.modelId()).size());
    }
    @Test void changedOrMissingArtifactKeepsPreviousStateAndHead(){
        var f=new ModelDatabaseFixture();var catalog=ModelCatalogMysqlTest.service(f);var v=catalog.register(ModelCatalogMysqlTest.candidate(42));var repo=new ModelRepository(f.jdbc,ModelCatalogMysqlTest.MAPPER);
        var changed=release(repo,(id,h)->{var m=ModelCatalogMysqlTest.manifest(id);((com.fasterxml.jackson.databind.node.ObjectNode)m).put("epoch",4);return m;});
        ModelCatalogMysqlTest.code("MODEL_ARTIFACT_CHANGED",()->changed.mutate(ADMIN,v.modelId(),"publish",input(v,repo.head())));
        var absent=release(repo,(id,h)->{throw new BusinessException("MODEL_ARTIFACT_UNAVAILABLE","制品暂不可用",org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);});
        ModelCatalogMysqlTest.code("MODEL_ARTIFACT_UNAVAILABLE",()->absent.mutate(ADMIN,v.modelId(),"publish",input(v,repo.head())));assertEquals(v,repo.find(v.modelId(),false));assertNull(repo.head().currentModelId());
    }
    @Test void auditFailureRollsBackVersionAndHead(){
        var f=new ModelDatabaseFixture();var v=ModelCatalogMysqlTest.service(f).register(ModelCatalogMysqlTest.candidate(42));
        var broken=new JdbcTemplate(f.jdbc.getDataSource()){@Override public int update(String sql,Object...args){if(sql.startsWith("insert into model_release_event"))throw new IllegalStateException("isolated audit failure");return super.update(sql,args);}};
        var repo=new ModelRepository(broken,ModelCatalogMysqlTest.MAPPER);var s=release(repo,ModelCatalogMysqlTest::inspect);
        assertThrows(IllegalStateException.class,()->s.mutate(ADMIN,v.modelId(),"publish",input(v,repo.head())));assertEquals(v,repo.find(v.modelId(),false));assertEquals(0,repo.head().revision());assertEquals(1,repo.events(v.modelId()).size());
    }
    @Test void concurrentDecisionsHaveOneWinnerAndOneStaleConflict()throws Exception{
        var f=new ModelDatabaseFixture();var v=ModelCatalogMysqlTest.service(f).register(ModelCatalogMysqlTest.candidate(42));var repo=new ModelRepository(f.jdbc,ModelCatalogMysqlTest.MAPPER);var s=release(repo,ModelCatalogMysqlTest::inspect);var pool=Executors.newFixedThreadPool(2);var head=repo.head();
        try{var outcomes=pool.invokeAll(List.of(()->attempt(s,v,input(v,head)),()->attempt(s,v,input(v,head))));var codes=List.of(outcomes.get(0).get(),outcomes.get(1).get());assertTrue(codes.contains("OK"));assertTrue(codes.contains("MODEL_REVISION_CONFLICT"));assertEquals(2,repo.events(v.modelId()).size());}finally{pool.shutdownNow();}
    }
    @Test void mysqlJsonFloatRoundTripDoesNotPretendTheHashedArtifactChanged(){
        var f=new ModelDatabaseFixture();var source=ModelCatalogMysqlTest.manifest(ModelCatalogMysqlTest.candidate(43).artifactId());
        ((com.fasterxml.jackson.databind.node.ObjectNode)source).putArray("mean").add(235.00608825683594).add(237.90721130371094);
        var repo=new ModelRepository(f.jdbc,ModelCatalogMysqlTest.MAPPER);
        var catalog=new ModelCatalogService(repo,(id,h)->source.deepCopy(),ModelCatalogMysqlTest.NOW,ModelCatalogMysqlTest.MAPPER);
        var registered=catalog.register(ModelCatalogMysqlTest.candidate(43));
        var stored=repo.find(registered.modelId(),false);
        assertFalse(source.equals(stored.manifest()),"Real MySQL JSON must reproduce the observed numeric round-trip difference");
        var result=release(repo,(id,h)->source.deepCopy()).mutate(ADMIN,registered.modelId(),"publish",input(stored,repo.head()));
        assertEquals("PUBLISHED",result.model().state());assertEquals(2,repo.events(registered.modelId()).size());
    }
    @Test void genuineFloatingMetadataChangesStillBlockPublication(){
        var f=new ModelDatabaseFixture();var source=ModelCatalogMysqlTest.manifest(ModelCatalogMysqlTest.candidate(43).artifactId());
        ((com.fasterxml.jackson.databind.node.ObjectNode)source).putArray("mean").add(235.00608825683594);
        var repo=new ModelRepository(f.jdbc,ModelCatalogMysqlTest.MAPPER);
        var registered=new ModelCatalogService(repo,(id,h)->source.deepCopy(),ModelCatalogMysqlTest.NOW,ModelCatalogMysqlTest.MAPPER).register(ModelCatalogMysqlTest.candidate(43));
        var changed=source.deepCopy();((com.fasterxml.jackson.databind.node.ArrayNode)changed.get("mean")).set(0,com.fasterxml.jackson.databind.node.DoubleNode.valueOf(235.00608825683594+0.001));
        ModelCatalogMysqlTest.code("MODEL_ARTIFACT_CHANGED",()->release(repo,(id,h)->changed).mutate(ADMIN,registered.modelId(),"publish",input(registered,repo.head())));
        assertEquals("PENDING",repo.find(registered.modelId(),false).state());assertEquals(1,repo.events(registered.modelId()).size());
    }
    private String attempt(ModelReleaseService s,Version v,ActionInput i){try{s.mutate(ADMIN,v.modelId(),"publish",i);return "OK";}catch(BusinessException e){return e.code();}}
}
