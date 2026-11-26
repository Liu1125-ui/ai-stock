package com.aistock.user.watchlist;
import com.aistock.common.*;import com.aistock.user.session.Identity;import java.time.*;import java.util.UUID;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class WatchlistServiceTest {
 final Identity owner=new Identity(UUID.randomUUID(),"USER",false,1);
 @Test void rejectedDemoAndUnavailableMarketDoNotWrite(){var repo=mock(WatchlistRepository.class);var c=mock(MarketIdentityClient.class);var s=new WatchlistService(repo,c,"service",Clock.systemUTC());when(c.get(eq("SH600030"),eq("service"))).thenReturn(ApiResponse.success(new MarketIdentityClient.InstrumentView("SH600030","SH600030","demo",true,"stock-market-service"),"t"));assertEquals("RESOURCE_NOT_FOUND",assertThrows(BusinessException.class,()->s.add(owner,"SH600030")).code());reset(c);when(c.get(anyString(),anyString())).thenThrow(new IllegalStateException("down"));assertEquals("MARKET_SERVICE_UNAVAILABLE",assertThrows(BusinessException.class,()->s.add(owner,"SH600030")).code());verify(repo,never()).add(any(),anyString(),any());s.delete(owner,"SH600030");verify(repo).delete(owner.userId(),"SH600030");}
 @Test void validatedInstrumentAddsToCurrentIdentityOnly(){var repo=mock(WatchlistRepository.class);var c=mock(MarketIdentityClient.class);var s=new WatchlistService(repo,c,"service",Clock.systemUTC());when(c.get(anyString(),anyString())).thenReturn(ApiResponse.success(new MarketIdentityClient.InstrumentView("SH600030","SH600030","code",false,"stock-market-service"),"t"));s.add(owner,"SH600030");verify(repo).add(eq(owner.userId()),eq("SH600030"),any());}
 @Test void pilotCanBeAddedAndRemovedWithoutQueuingDailyPreparation() {
  var repo=mock(WatchlistRepository.class);
  var market=mock(MarketIdentityClient.class);
  var service=new WatchlistService(repo,market,"service",Clock.systemUTC(),true);
  when(market.get("HK00700","service")).thenReturn(ApiResponse.success(
      new MarketIdentityClient.InstrumentView("HK00700","00700","Tencent",false,"stock-market-service"),"t"));
  service.add(owner,"HK00700");
  service.delete(owner,"HK00700");
  verify(repo).add(eq(owner.userId()),eq("HK00700"),any());
  verify(repo).delete(owner.userId(),"HK00700");
  verify(repo,never()).addAndQueue(any(),eq("HK00700"),any());
  assertEquals("VALIDATION_FAILED",assertThrows(BusinessException.class,()->service.add(owner,"HK00941")).code());
 }
}
