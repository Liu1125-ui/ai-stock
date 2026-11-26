package com.aistock.user.position;
import java.time.*;import java.math.*;import java.util.*;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import com.aistock.user.watchlist.MarketIdentityClient;import com.aistock.common.*;import static com.aistock.user.position.PositionRecords.*;
@Tag("mysql") class PositionServiceTest {
 @Test void unavailableMarketDoesNotWriteAndExistingMutationsDoNotCallMarket(){try(var f=new PositionTestFixture()){
  var client=mock(MarketIdentityClient.class);var s=new PositionService(f.repo,client,"fixture",Clock.systemUTC(),1000000000L,new BigDecimal("1000000"));when(client.get(anyString(),anyString())).thenThrow(new IllegalStateException("unavailable"));
  assertEquals("MARKET_SERVICE_UNAVAILABLE",assertThrows(BusinessException.class,()->s.apply(f.owner,f.create("SH600030"))).code());assertTrue(s.list(f.owner).items().isEmpty());
  var input=f.create("SH600030");var saved=f.service.apply(f.owner,input);clearInvocations(client);
  assertEquals(saved,s.apply(f.owner,input));assertEquals("UPDATED",s.apply(f.owner,new PositionMutation(UUID.randomUUID(),"UPDATE",UUID.fromString(saved.positionId()),null,"2","0",1L)).state());
  assertEquals("DELETED",s.apply(f.owner,new PositionMutation(UUID.randomUUID(),"DELETE",UUID.fromString(saved.positionId()),null,null,null,2L)).state());verifyNoInteractions(client);
 }}
 @Test void demoAndMismatchedSecuritiesAreRejected(){try(var f=new PositionTestFixture()){
  var client=mock(MarketIdentityClient.class);var s=new PositionService(f.repo,client,"fixture",Clock.systemUTC(),1000000000L,new BigDecimal("1000000"));
  when(client.get(anyString(),anyString())).thenReturn(ApiResponse.success(new MarketIdentityClient.InstrumentView("SH600030","SH600030","demo",true,"stock-market-service"),"t"));assertEquals("RESOURCE_NOT_FOUND",assertThrows(BusinessException.class,()->s.apply(f.owner,f.create("SH600030"))).code());
  when(client.get(anyString(),anyString())).thenReturn(ApiResponse.success(new MarketIdentityClient.InstrumentView("SH600276","SH600276","other",false,"stock-market-service"),"t"));assertThrows(BusinessException.class,()->s.apply(f.owner,f.create("SH600030")));assertTrue(s.list(f.owner).items().isEmpty());
 }}
 @Test void snapshotVersionAndRowsNeverMixAcrossConcurrentUpdates()throws Exception{try(var f=new PositionTestFixture()){
  var item=f.service.apply(f.owner,f.create("SH600030"));var start=new java.util.concurrent.CyclicBarrier(2);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try{var write=pool.submit(()->{start.await();for(long n=2;n<=15;n++)f.service.apply(f.owner,new PositionMutation(UUID.randomUUID(),"UPDATE",UUID.fromString(item.positionId()),null,Long.toString(n),"20",n-1));return true;});
   var read=pool.submit(()->{start.await();for(int i=0;i<40;i++){var book=f.service.list(f.owner);assertEquals(book.bookRevision(),book.items().get(0).revision());assertEquals(book.bookRevision(),book.items().get(0).quantity());}return true;});assertTrue(write.get(20,java.util.concurrent.TimeUnit.SECONDS));assertTrue(read.get(20,java.util.concurrent.TimeUnit.SECONDS));
  }finally{pool.shutdownNow();}
 }}
}
