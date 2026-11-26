package com.aistock.prediction.portfolioanalysis;
import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicReference;import java.math.BigDecimal;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;import com.fasterxml.jackson.databind.*;
import static com.aistock.prediction.portfolioanalysis.PortfolioTestEvidence.*;
@Tag("mysql") class PortfolioRepositoryMysqlTest {
 JdbcTemplate jdbc;PortfolioRepository repo;ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 @BeforeEach void setup(){String url=System.getenv("PREDICTION_TEST_DB_URL"),password=System.getenv("PREDICTION_TEST_DB_PASSWORD");if(!"jdbc:mysql://127.0.0.1:13316/stock_prediction_test?serverTimezone=UTC".equals(url)||password==null||password.isBlank())throw new IllegalStateException("Only isolated prediction DB allowed");jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"root",password));assertEquals("stock_prediction_test",jdbc.queryForObject("select database()",String.class));Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration").load().migrate();jdbc.update("delete from portfolio_market_use");jdbc.update("delete from portfolio_analysis_operation");jdbc.update("delete from portfolio_analysis_result");repo=new PortfolioRepository(jdbc,mapper);}
 PortfolioRecords.Claim claim(UUID id){return repo.claim(OWNER,id,NOW,DATE,new BigDecimal("50"));}
 @Test void newPositionRevisionLeavesPreviousValuationUnchanged(){
  var sources=org.mockito.Mockito.mock(PortfolioSources.class);
  var clock=Clock.fixed(NOW,ZoneOffset.UTC);
  var service=new PortfolioService(repo,sources,clock,new BigDecimal("50"),1);
  var actor=new com.aistock.prediction.modelcatalog.ModelRecords.Actor(OWNER,"USER");
  var token=new PortfolioRecords.SessionToken("a".repeat(43));
  var first=position("SH600030","100","10");
  var second=position("SH600276","200","10");
  var next=new PortfolioRecords.PositionItem(first.positionId(),first.instrumentId(),"110","10","2");
  var revised=new PortfolioRecords.PositionSnapshot(OWNER,"stock-user-service","3",List.of(next,second));
  var cutoff=LocalDate.parse("2026-09-28");
  var quoteBatch=new PortfolioRecords.QuoteBatch(cutoff,NOW,List.of(quote("SH600030","12",DATE),quote("SH600276","9",DATE)));
  org.mockito.Mockito.when(sources.snapshot(token)).thenReturn(snapshot(first,second),revised);
  org.mockito.Mockito.when(sources.quotes(List.of("SH600030","SH600276"),cutoff)).thenReturn(quoteBatch);
  org.mockito.Mockito.when(sources.revision(token)).thenReturn(
      new PortfolioRecords.PositionRevision(OWNER,"stock-user-service","2"),
      new PortfolioRecords.PositionRevision(OWNER,"stock-user-service","3"));
  var requestA=new PortfolioRecords.Request(UUID.randomUUID());
  var operationA=service.create(actor,token,requestA).operation();
  var savedA=service.get(actor,operationA.analysisId());
  assertEquals("3000.00",savedA.at("/totals/costAmount").asText());
  assertEquals("3000.00",savedA.at("/totals/marketValue").asText());
  assertEquals("0.00",savedA.at("/totals/returnPercent").asText());
  assertEquals("200.00",savedA.at("/lines/0/floatingProfit").asText());
  assertEquals("-200.00",savedA.at("/lines/1/floatingProfit").asText());
  assertEquals("40.00",savedA.at("/lines/0/weightPercent").asText());
  assertEquals("60.00",savedA.at("/lines/1/weightPercent").asText());
  var operationB=service.create(actor,token,new PortfolioRecords.Request(UUID.randomUUID())).operation();
  var savedB=service.get(actor,operationB.analysisId());
  assertNotEquals(operationA.analysisId(),operationB.analysisId());
  assertEquals("3",savedB.path("bookRevision").asText());
  assertEquals("3100.00",savedB.at("/totals/costAmount").asText());
  assertEquals("3120.00",savedB.at("/totals/marketValue").asText());
  assertEquals("20.00",savedB.at("/totals/floatingProfit").asText());
  assertEquals(DATE.toString(),savedB.at("/lines/0/quote/priceDate").asText());
  assertEquals(savedA,service.get(actor,operationA.analysisId()));
  assertEquals(operationA,service.create(actor,token,requestA).operation());
  assertEquals(2,service.list(actor,null,null).items().size());
  var other=new com.aistock.prediction.modelcatalog.ModelRecords.Actor(UUID.randomUUID(),"USER");
  assertEquals(404,assertThrows(com.aistock.common.BusinessException.class,
      ()->service.get(other,operationA.analysisId())).status().value());
 }
 @Test void successIsImmutableAndOwnerScopedWhileReplayNeverExecutesAgain(){var id=UUID.randomUUID();var c=claim(id);assertTrue(c.execute());assertFalse(c.replayed());var done=repo.complete(OWNER,id,c.generation(),calculation(),normal(),prices(),Clock.fixed(NOW,ZoneOffset.UTC));assertEquals("SUCCEEDED",done.state());var replay=claim(id);assertFalse(replay.execute());assertTrue(replay.replayed());assertEquals(done.analysisId(),replay.operation().analysisId());assertEquals("5000.00",repo.get(OWNER,done.analysisId()).path("totals").path("marketValue").asText());assertThrows(com.aistock.common.BusinessException.class,()->repo.get(UUID.randomUUID(),done.analysisId()));assertEquals(1,jdbc.queryForObject("select count(*) from portfolio_analysis_result",Integer.class));}
 @Test void expiredAndFailedRequestsCannotBeTakenOver(){var id=UUID.randomUUID();claim(id);var op=repo.operation(OWNER,id,NOW.plusSeconds(46));assertEquals("FAILED",op.state());assertEquals("PORTFOLIO_OPERATION_EXPIRED",op.errorCode());assertFalse(repo.claim(OWNER,id,NOW.plusSeconds(46),DATE,new BigDecimal("60")).execute());var another=UUID.randomUUID();var c=claim(another);repo.fail(OWNER,another,c.generation(),"PORTFOLIO_CHANGED",Clock.fixed(NOW,ZoneOffset.UTC));assertFalse(claim(another).execute());assertEquals(0,jdbc.queryForObject("select count(*) from portfolio_analysis_result",Integer.class));}
 @Test void concurrentRequestHasExactlyOneExecutor()throws Exception{var id=UUID.randomUUID();var executor=Executors.newFixedThreadPool(2);try{var start=new CountDownLatch(1);var a=executor.submit(()->{start.await();return claim(id);});var b=executor.submit(()->{start.await();return claim(id);});start.countDown();assertEquals(1,(a.get(5,TimeUnit.SECONDS).execute()?1:0)+(b.get(5,TimeUnit.SECONDS).execute()?1:0));}finally{executor.shutdownNow();}}
 @Test void oversizedEvidenceRollsBackWithoutOrphanResult(){var id=UUID.randomUUID();var c=claim(id);var s=normal();var huge=new PortfolioRecords.PositionSnapshot(OWNER,"stock-user-service","2",java.util.Collections.nCopies(20000,s.items().get(0)));assertThrows(com.aistock.common.BusinessException.class,()->repo.complete(OWNER,id,c.generation(),calculation(),huge,prices(),Clock.fixed(NOW,ZoneOffset.UTC)));assertEquals(0,jdbc.queryForObject("select count(*) from portfolio_analysis_result",Integer.class));assertEquals("RUNNING",repo.operation(OWNER,id,NOW).state());}
 @Test void paginationHasFixedUpperBoundAndTwentyRows(){for(int i=0;i<22;i++){var id=UUID.randomUUID();var c=claim(id);repo.complete(OWNER,id,c.generation(),calculation(),normal(),prices(),Clock.fixed(NOW,ZoneOffset.UTC));}var page=repo.list(OWNER,null,null);assertEquals(20,page.items().size());assertNotNull(page.nextBefore());var id=UUID.randomUUID();var c=claim(id);repo.complete(OWNER,id,c.generation(),calculation(),normal(),prices(),Clock.fixed(NOW,ZoneOffset.UTC));var next=repo.list(OWNER,page.nextBefore(),page.upper());assertEquals(2,next.items().size());assertEquals(page.upper(),next.upper());assertTrue(repo.list(UUID.randomUUID(),null,null).items().isEmpty());}
 @Test void clockIsRereadAfterRealRowLockWaitAtDeadlineAndLease()throws Exception{
  for(int seconds:new int[]{31,46}){
   var id=UUID.randomUUID();var claim=claim(id);var current=new AtomicReference<>(NOW);Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return current.get();}};
   var executor=Executors.newSingleThreadExecutor();
   try(var lock=jdbc.getDataSource().getConnection()){
    lock.setAutoCommit(false);try(var statement=lock.prepareStatement("select owner_id from portfolio_analysis_operation where owner_id=? and request_id=? for update")){statement.setString(1,OWNER.toString());statement.setString(2,id.toString());statement.executeQuery().close();}
    var future=executor.submit(()->seconds==31?repo.complete(OWNER,id,claim.generation(),calculation(),normal(),prices(),clock):repo.fail(OWNER,id,claim.generation(),"DEPENDENCY_FAILED",clock));
    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);boolean waiting=false;while(System.nanoTime()<end){if(jdbc.queryForObject("select count(*) from performance_schema.data_lock_waits",Integer.class)>0){waiting=true;break;}Thread.sleep(10);}assertTrue(waiting,"worker must actually wait on the row lock");current.set(NOW.plusSeconds(seconds));lock.commit();var result=future.get(4,TimeUnit.SECONDS);assertEquals("FAILED",result.state());assertEquals("PORTFOLIO_OPERATION_EXPIRED",result.errorCode());assertEquals(0,jdbc.queryForObject("select count(*) from portfolio_analysis_result",Integer.class));
   }finally{executor.shutdownNow();}
  }
 }
 @Test void finalClockCheckRollsBackAnInsertedResult(){
  var id=UUID.randomUUID();var c=claim(id);var calls=new java.util.concurrent.atomic.AtomicInteger();
  Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return calls.getAndIncrement()==0?NOW:NOW.plusSeconds(31);}};
  assertThrows(com.aistock.common.BusinessException.class,()->repo.complete(OWNER,id,c.generation(),calculation(),normal(),prices(),clock));
  assertEquals(0,jdbc.queryForObject("select count(*) from portfolio_analysis_result",Integer.class));
  assertEquals("PORTFOLIO_OPERATION_EXPIRED",repo.operation(OWNER,id,NOW.plusSeconds(31)).errorCode());
 }
 @Test void upgradingV9PreservesExistingPredictionsAndComparisonsAndIsRepeatable(){
  assertEquals("stock_prediction_test",jdbc.queryForObject("select database()",String.class));
  var source=jdbc.getDataSource();var migration=Flyway.configure().dataSource(source).locations("classpath:db/migration").cleanDisabled(false).target("9").load();migration.clean();migration.migrate();
  var comparison=UUID.randomUUID();var prediction=UUID.randomUUID();
  jdbc.update("insert into stock_comparison_result(comparison_id,owner_id,result_json,summary_json,source_snapshots_json,source_hash,created_at) values(?,?,?,?,'[]',?,?)",comparison.toString(),OWNER.toString(),"{\"sentinel\":true}","{}","a".repeat(64),java.sql.Timestamp.from(NOW));
  jdbc.update("insert into forecast_request(prediction_id,user_id,operation_id,request_hash,model_id,instrument_id,horizon,state,model_json,snapshot_json,created_at,expires_at) values(?,?,?,?,?,'SH600030',5,'SUCCEEDED','{}',?,?,?)",prediction.toString(),OWNER.toString(),UUID.randomUUID().toString(),"b".repeat(64),UUID.randomUUID().toString(),"{\"sentinel\":true}",java.sql.Timestamp.from(NOW),java.sql.Timestamp.from(NOW.plusSeconds(60)));
  var latest=Flyway.configure().dataSource(source).locations("classpath:db/migration").target("10").load();assertEquals(1,latest.migrate().migrationsExecuted);assertEquals(0,latest.migrate().migrationsExecuted);
  assertEquals(1,jdbc.queryForObject("select count(*) from stock_comparison_result where comparison_id=?",Integer.class,comparison.toString()));assertEquals(1,jdbc.queryForObject("select count(*) from forecast_request where prediction_id=?",Integer.class,prediction.toString()));
  assertEquals("10",jdbc.queryForObject("select version from flyway_schema_history where success=true order by installed_rank desc limit 1",String.class));
 }
}
