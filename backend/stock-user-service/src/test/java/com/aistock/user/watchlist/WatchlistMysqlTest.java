package com.aistock.user.watchlist;
import com.aistock.user.support.AuthDatabaseFixture;import java.time.Instant;import java.util.UUID;import java.util.concurrent.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
@Tag("mysql")class WatchlistMysqlTest{
 void user(AuthDatabaseFixture f,UUID id){f.jdbc().update("INSERT INTO user_account(user_id,email,email_normalized,email_verified_at,password_hash,role_code,status,nickname,credential_version,created_at) VALUES (?,?,?,?,?,'USER','ENABLED','tester',1,?)",id.toString(),id+"@example.com",id+"@example.com",java.sql.Timestamp.from(f.clock().instant()),"$2a$12$"+"a".repeat(53),java.sql.Timestamp.from(f.clock().instant()));}
 @Test void deletingOwnInstrumentDoesNotDeleteAnotherUsersRow(){var f=new AuthDatabaseFixture();var a=UUID.randomUUID();var b=UUID.randomUUID();user(f,a);user(f,b);var repo=new WatchlistRepository(f.jdbc());repo.add(a,"SH600030",f.clock().instant());repo.add(b,"SH600030",f.clock().instant());repo.delete(a,"SH600030");assertEquals(0,repo.count(a));assertEquals(1,repo.count(b));repo.delete(a,"SH600030");assertEquals(1,repo.count(b));}
 @Test void concurrentDuplicateAddReturnsOriginalCreatedAt()throws Exception{var f=new AuthDatabaseFixture();var a=UUID.randomUUID();user(f,a);var repo=new WatchlistRepository(f.jdbc());var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);try{var x=pool.submit(()->{barrier.await();return repo.add(a,"SH600030",Instant.parse("2026-09-01T00:00:00Z"));});var y=pool.submit(()->{barrier.await();return repo.add(a,"SH600030",Instant.parse("2026-09-02T00:00:00Z"));});assertEquals(x.get(10,TimeUnit.SECONDS),y.get(10,TimeUnit.SECONDS));assertEquals(1,repo.count(a));}finally{pool.shutdownNow();}}
 @Test void listPaginatesOnlyOwner(){var f=new AuthDatabaseFixture();var a=UUID.randomUUID();var b=UUID.randomUUID();user(f,a);user(f,b);var repo=new WatchlistRepository(f.jdbc());repo.add(a,"SH600030",f.clock().instant());repo.add(a,"SZ000002",f.clock().instant());repo.add(b,"SH600276",f.clock().instant());assertEquals(2,repo.list(a,1,1).total());assertEquals(1,repo.list(a,2,1).items().size());assertEquals(1,repo.count(b));}

 @Test void addingSameSymbolForTwoAccountsSharesDailyPreparation(){
  var f=new AuthDatabaseFixture();var first=UUID.randomUUID();var second=UUID.randomUUID();
  user(f,first);user(f,second);
  var repo=new WatchlistRepository(f.jdbc());
  var now=Instant.parse("2026-11-20T06:00:00Z");
  repo.addAndQueue(first,"SH600519",now);
  repo.addAndQueue(second,"SH600519",now);
  var requests=new WatchlistMarketQueue(f.jdbc()).due(now,10);
  assertEquals(1,requests.stream().filter(item->item.instrumentId().equals("SH600519")).count());
  assertEquals(java.time.LocalDate.parse("2026-11-19"),
    requests.stream().filter(item->item.instrumentId().equals("SH600519")).findFirst().orElseThrow().targetDate());
  assertEquals(1,repo.count(first));assertEquals(1,repo.count(second));
 }
 @Test void removingWatchlistEntryKeepsPositionForTheSameStock(){
  var f=new AuthDatabaseFixture();
  var owner=UUID.randomUUID();
  user(f,owner);
  var now=f.clock().instant();
  var stamp=java.sql.Timestamp.from(now);
  var repo=new WatchlistRepository(f.jdbc());
  repo.add(owner,"SH600030",now);
  f.jdbc().update("INSERT INTO `position` (position_id,user_id,instrument_id,quantity,average_cost,revision,created_at,updated_at) VALUES (?,?,?,100,25,1,?,?)",UUID.randomUUID().toString(),owner.toString(),"SH600030",stamp,stamp);
  repo.delete(owner,"SH600030");
  assertEquals(0,repo.count(owner));
  assertEquals(100L,f.jdbc().queryForObject("SELECT quantity FROM `position` WHERE user_id=? AND instrument_id='SH600030'",Long.class,owner.toString()));
 }
 @Test void activeWatchlistStocksKeepRefreshingOnLaterDays(){
  var f=new AuthDatabaseFixture();var owner=UUID.randomUUID();user(f,owner);
  var queue=new WatchlistMarketQueue(f.jdbc());var repo=new WatchlistRepository(f.jdbc());
  var now=Instant.parse("2026-11-20T06:00:00Z");
  repo.addAndQueue(owner,"SH600036",now);
  queue.delivered(queue.due(now,5).get(0));
  var tomorrow=now.plusSeconds(86400);
  var scheduler=org.mockito.Mockito.mock(WatchlistMarketScheduler.class);
  var worker=new WatchlistMarketDispatch(queue,scheduler,java.time.Clock.fixed(tomorrow,java.time.ZoneOffset.UTC),"test",true);
  worker.dispatch();
  var job=new WatchlistMarketScheduler.Job("SH600036",java.time.LocalDate.parse("2026-11-20"),"daily-SH600036-2026-11-20");
  org.mockito.Mockito.verify(scheduler).submit(job,"test");
  repo.delete(owner,"SH600036");
  var dayAfter=tomorrow.plusSeconds(86400);
  new WatchlistMarketDispatch(queue,scheduler,java.time.Clock.fixed(dayAfter,java.time.ZoneOffset.UTC),"test",true).dispatch();
  assertTrue(queue.due(dayAfter,5).isEmpty());
 }
}
