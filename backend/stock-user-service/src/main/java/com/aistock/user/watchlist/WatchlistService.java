package com.aistock.user.watchlist;
import com.aistock.common.BusinessException;import com.aistock.user.session.Identity;import java.time.Clock;import org.springframework.beans.factory.annotation.Value;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;
@Service public class WatchlistService {
 private final WatchlistRepository repository;private final MarketIdentityClient market;private final String token;private final Clock clock;private final boolean prepareMarket;
 public WatchlistService(WatchlistRepository repository,MarketIdentityClient market,String token,Clock clock){
  this(repository,market,token,clock,false);
 }
 @org.springframework.beans.factory.annotation.Autowired
 public WatchlistService(WatchlistRepository repository,MarketIdentityClient market,
   @Value("${aistock.internal.token:}")String token,Clock clock,
   @Value("${aistock.watchlist.market-preparation-enabled:false}")boolean prepareMarket){
  this.repository=repository;this.market=market;this.token=token;this.clock=clock;this.prepareMarket=prepareMarket;
 }
 public WatchlistPage list(Identity identity,int page,int size){if(page<1||page>100000||size<1||size>100)throw invalid();return repository.list(identity.userId(),page,size);}
 public WatchlistItem add(Identity identity,String id){validate(id);if(token.isBlank())throw unavailable();MarketIdentityClient.InstrumentView item;try{var response=market.get(id,token);item=response!=null&&"OK".equals(response.code())?response.data():null;}catch(feign.FeignException e){if(e.status()==404)throw missing();throw unavailable();}catch(RuntimeException e){throw unavailable();}if(item==null||item.demoOnly()||!id.equals(item.instrumentId()))throw missing();return prepareMarket && !"HK00700".equals(id) ? repository.addAndQueue(identity.userId(),id,clock.instant()) : repository.add(identity.userId(),id,clock.instant());}
 public void delete(Identity identity,String id){validate(id);repository.delete(identity.userId(),id);}
 private void validate(String id){if(id==null||!id.matches("(?:SH|SZ)[0-9]{6}|HK00700"))throw invalid();}
 private BusinessException missing(){return new BusinessException("RESOURCE_NOT_FOUND","证券不存在",HttpStatus.NOT_FOUND);}
 private BusinessException unavailable(){return new BusinessException("MARKET_SERVICE_UNAVAILABLE","行情服务暂不可用，请稍后重试",HttpStatus.SERVICE_UNAVAILABLE);}
 private BusinessException invalid(){return new BusinessException("VALIDATION_FAILED","证券代码或分页参数不正确",HttpStatus.BAD_REQUEST);}
}
