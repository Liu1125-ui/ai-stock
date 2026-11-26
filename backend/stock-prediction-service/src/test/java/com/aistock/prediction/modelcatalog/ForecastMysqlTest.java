package com.aistock.prediction.modelcatalog;
import com.aistock.prediction.forecast.*;
import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.*;
import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.prediction.forecast.ForecastRecords.*;
@Tag("mysql") class ForecastMysqlTest {
 static final ObjectMapper M=ModelCatalogMysqlTest.MAPPER;
 static final UUID USER=UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
 static final Instant NOW=Instant.parse("2026-10-05T01:00:00Z");
 static JsonNode window(){try{return M.readTree("""
 {"instrumentId":"SH600030","inputVersionId":"11111111-1111-1111-1111-111111111111","rawVersionId":"22222222-2222-2222-2222-222222222222","rawContentHash":"%s","inputHash":"%s","calendarHash":"%s","originDate":"2026-09-30","originRawClose":"25.86","freshness":"FRESH","targetDates":["2026-10-08","2026-10-09","2026-10-12","2026-10-13","2026-10-14"]}
 """.formatted("a".repeat(64),"b".repeat(64),"d".repeat(64)));}catch(Exception e){throw new AssertionError(e);}}
 static JsonNode result(ModelRecords.Version v,JsonNode w){var n=M.createObjectNode();for(var k:List.of("instrumentId","inputVersionId","rawVersionId","rawContentHash","inputHash","calendarHash","originDate","originRawClose","freshness"))n.set(k,w.get(k));n.put("modelId",v.modelId().toString());n.put("artifactId",v.artifactId());n.put("manifestHash",v.manifestHash());n.put("checkpointHash",v.checkpointHash());n.put("publicationRevision",v.revision());n.put("referenceBasis","ORIGIN_SHARE_BASIS");n.put("formalInference",true);n.put("validationOnly",false);var points=n.putArray("points");for(int i=0;i<5;i++)points.addObject().put("step",i+1).put("targetDate",w.path("targetDates").get(i).asText()).put("ratio","1.02").put("referencePrice","26.377200");return n;}
 static class Setup {
  final ModelDatabaseFixture f=new ModelDatabaseFixture();final AtomicInteger calls=new AtomicInteger();final AtomicReference<Instant> time=new AtomicReference<>(NOW);
  final ModelRepository models=new ModelRepository(f.jdbc,M);final ForecastRepository records;final ModelRecords.Version model;final ForecastService service;
  Setup(){f.jdbc.update("delete from forecast_request");f.jdbc.update("delete from forecast_user_guard");var v=ModelCatalogMysqlTest.service(f).register(ModelCatalogMysqlTest.candidate(42));f.jdbc.update("update model_version set status='PUBLISHED',revision=1,ever_published=true where model_id=?",v.modelId().toString());model=models.find(v.modelId(),false);
   Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return time.get();}};
   records=new ForecastRepository(f.jdbc,M,clock);service=new ForecastService(records,models,new ForecastDependencies(){public JsonNode prepare(String stock,UUID op){return window();}public JsonNode infer(ModelRecords.Version v,JsonNode w){calls.incrementAndGet();return result(v,w);}},M,clock);
  }
  Input input(){return new Input(UUID.randomUUID().toString(),model.modelId().toString(),"SH600030",5);}
 }
 static void code(String c,Runnable r){assertEquals(c,assertThrows(BusinessException.class,r::run).code());}
 @Test void repeatedRequestUsesOneImmutableSnapshotAcrossRestartAndOffline(){var s=new Setup();var i=s.input();var first=s.service.create(USER,i);assertEquals("SUCCEEDED",first.state());s.f.jdbc.update("update model_version set status='OFFLINE'");var replay=s.service.create(USER,i);assertEquals(first.predictionId(),replay.predictionId());assertEquals(1,s.calls.get());assertEquals(first.snapshot(),new ForecastRepository(s.f.jdbc,M,Clock.fixed(NOW,ZoneOffset.UTC)).get(USER,first.predictionId()).snapshot());}
 @Test void parameterConflictOwnershipAndHideCannotOverwrite(){var s=new Setup();var i=s.input();var first=s.service.create(USER,i);code("PREDICTION_REQUEST_CONFLICT",()->s.service.create(USER,new Input(i.operationId(),i.modelId(),"SH600276",5)));code("RESOURCE_NOT_FOUND",()->s.records.get(UUID.randomUUID(),first.predictionId()));code("RESOURCE_NOT_FOUND",()->s.records.hide(UUID.randomUUID(),first.predictionId()));s.records.hide(USER,first.predictionId());assertEquals(0,s.records.list(USER,1).items().size());assertEquals(first.snapshot(),s.records.get(USER,first.predictionId()).snapshot());}
 @Test void concurrentSameRequestClaimsOnce()throws Exception{var s=new Setup();var i=s.input();var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->s.service.create(USER,i));var b=pool.submit(()->s.service.create(USER,i));assertEquals(a.get().predictionId(),b.get().predictionId());assertEquals(1,s.calls.get());}finally{pool.shutdownNow();}}
 @Test void expiryPreventsLateCompletionAndSameKeyReexecution(){var s=new Setup();var i=s.input();var claim=s.records.claim(USER,i,"hash",()->s.model);s.time.set(NOW.plusSeconds(181));assertFalse(s.records.succeed(USER,claim.record().predictionId(),M.createObjectNode()));assertEquals("FAILED",s.records.get(USER,claim.record().predictionId()).state());assertFalse(s.records.claim(USER,i,"hash",()->s.model).execute());}
 @Test void activeRequestAndUnsupportedPeriodsRejected(){var s=new Setup();var i=s.input();s.records.claim(USER,i,"hash",()->s.model);code("PREDICTION_BUSY",()->s.service.create(USER,s.input()));code("PREDICTION_REQUEST_INVALID",()->s.service.create(USER,new Input(i.operationId(),i.modelId(),"SH600030",20)));}
 @Test void wrongInferenceIdentityNeverSavesSuccess(){var s=new Setup();var dep=new ForecastDependencies(){public JsonNode prepare(String stock,UUID op){return window();}public JsonNode infer(ModelRecords.Version v,JsonNode w){var n=(com.fasterxml.jackson.databind.node.ObjectNode)result(v,w);n.put("inputHash","0".repeat(64));return n;}};var service=new ForecastService(s.records,s.models,dep,M,Clock.fixed(NOW,ZoneOffset.UTC));var record=service.create(USER,s.input());assertEquals("FAILED",record.state());assertEquals("PREDICTION_RESULT_INVALID",record.errorCode());assertNull(record.snapshot());}
 @Test void anotherSupportedStockCannotReplaceRequestedInput(){var s=new Setup();var dep=new ForecastDependencies(){public JsonNode prepare(String stock,UUID op){var n=(com.fasterxml.jackson.databind.node.ObjectNode)window();n.put("instrumentId","SH600276");return n;}public JsonNode infer(ModelRecords.Version v,JsonNode w){return result(v,w);}};var service=new ForecastService(s.records,s.models,dep,M,Clock.fixed(NOW,ZoneOffset.UTC));var record=service.create(USER,s.input());assertEquals("FAILED",record.state());assertNull(record.snapshot());}

 @Test void fusionUsesOnlyOwnedMatchingSnapshotsAndRechecksPublication(){
  var setup=new Setup();
  var second=ModelCatalogMysqlTest.service(setup.f).register(ModelCatalogMysqlTest.candidate(43));
  setup.f.jdbc.update("update model_version set status='PUBLISHED',revision=1,ever_published=true where model_id=?",second.modelId().toString());
  var firstRecord=setup.service.create(USER,setup.input());
  var alternate=new ForecastService(setup.records,setup.models,new ForecastDependencies(){
   public JsonNode prepare(String stock,UUID op){return window();}
   public JsonNode infer(ModelRecords.Version model,JsonNode w){
    var out=result(model,w);
    for(var point:out.path("points"))((com.fasterxml.jackson.databind.node.ObjectNode)point).put("ratio","0.98").put("referencePrice","25.342800");
    return out;
   }
  },M,Clock.fixed(NOW,ZoneOffset.UTC));
  var secondRecord=alternate.create(USER,new Input(UUID.randomUUID().toString(),second.modelId().toString(),"SH600030",5));
  var a=new ForecastFusion.Selection(firstRecord.predictionId(),7500);
  var b=new ForecastFusion.Selection(secondRecord.predictionId(),2500);
  var queries=org.mockito.Mockito.mock(com.aistock.prediction.verification.VerificationQueryRepository.class);
  var fusion=new ForecastFusion(setup.records,setup.models,queries);
  var preview=fusion.preview(USER,List.of(a,b));
  assertEquals("1.010000000000",preview.points().get(0).ratio());
  assertEquals("26.118600",preview.points().get(0).referencePrice());
  assertEquals("25.86",preview.baselinePrice());
  assertEquals(5,preview.points().size());
  assertEquals("NO_SHARED_EVIDENCE",preview.assessment().state());
  var dates=preview.points().stream().map(point->LocalDate.parse(point.targetDate())).toList();
  var actual=new com.aistock.prediction.verification.VerificationRecords.ObservedDay(dates.get(0),
      com.aistock.prediction.verification.VerificationRecords.PointState.VERIFIED,
      new java.math.BigDecimal("26.118600"),new java.math.BigDecimal("26.118600"));
  var evidence=new com.aistock.prediction.verification.VerificationRecords.Evidence(UUID.randomUUID().toString(),
      "SH600030","baostock","baostock-backward-close-over-raw-v1",LocalDate.parse(preview.originDate()),
      dates,dates.get(0),new java.math.BigDecimal("25.86"),new java.math.BigDecimal("25.86"),List.of(actual));
  org.mockito.Mockito.when(queries.sharedFusionEvidence(USER,List.of(a.predictionId(),b.predictionId())))
      .thenReturn(Optional.of(evidence));
  var assessed=fusion.preview(USER,List.of(a,b)).assessment();
  assertEquals("PARTIAL",assessed.state());
  assertEquals(1,assessed.fusion().verifiedCount());
  assertEquals(0,assessed.fusion().metrics().ratioMae().signum());
  assertEquals(new java.math.BigDecimal("0.010000000000000000"),assessed.fusion().metrics().baselineRatioMae());
  assertEquals(new java.math.BigDecimal("0.030000000000000000"),assessed.members().get(1).metrics().ratioMae());
  code("FUSION_REQUEST_INVALID",()->fusion.preview(USER,List.of(a,new ForecastFusion.Selection(secondRecord.predictionId(),3000))));
  code("FUSION_REQUEST_INVALID",()->fusion.preview(USER,List.of(a,a)));
  code("RESOURCE_NOT_FOUND",()->fusion.preview(UUID.randomUUID(),List.of(a,b)));
  var changed=secondRecord.snapshot().deepCopy();
  var alteredHash="e".repeat(64);
  ((com.fasterxml.jackson.databind.node.ObjectNode)changed.path("input")).put("inputHash",alteredHash);
  ((com.fasterxml.jackson.databind.node.ObjectNode)changed.path("prediction")).put("inputHash",alteredHash);
  setup.f.jdbc.update("update forecast_request set snapshot_json=? where prediction_id=?",changed.toString(),secondRecord.predictionId().toString());
  code("FUSION_EVIDENCE_MISMATCH",()->fusion.preview(USER,List.of(a,b)));
  setup.f.jdbc.update("update forecast_request set snapshot_json=? where prediction_id=?",secondRecord.snapshot().toString(),secondRecord.predictionId().toString());
  setup.f.jdbc.update("update model_version set status='OFFLINE' where model_id=?",second.modelId().toString());
  code("MODEL_NOT_PUBLISHED",()->fusion.preview(USER,List.of(a,b)));
 }

 @Test void latestByInstrumentReturnsOnlyOwnersVisibleRecords(){
  var s=new Setup();
  var first=s.service.create(USER,s.input());
  s.time.set(NOW.plusSeconds(1));
  var newer=s.service.create(USER,s.input());
  s.time.set(NOW.plusSeconds(2));
  s.service.create(UUID.randomUUID(),s.input());
  assertEquals(newer.predictionId(),s.records.latestByInstrument(USER,List.of("SH600030","SZ000002")).get(0).predictionId());
  s.records.hide(USER,newer.predictionId());
  assertEquals(first.predictionId(),s.records.latestByInstrument(USER,List.of("SH600030")).get(0).predictionId());
  assertTrue(s.records.latestByInstrument(UUID.randomUUID(),List.of("SH600030")).isEmpty());
 }
}
