package com.aistock.user.position;
import java.time.Clock;import java.math.BigDecimal;import java.util.UUID;
import com.aistock.user.session.Identity;import com.aistock.user.watchlist.MarketIdentityClient;import com.aistock.common.ApiResponse;
import org.springframework.stereotype.Service;import org.springframework.beans.factory.annotation.Value;
import static com.aistock.user.position.PositionRecords.*;
@Service public class PositionService {
 private final PositionRepository repo;private final MarketIdentityClient market;private final String token;private final Clock clock;private final long maxQuantity;private final BigDecimal maxCost;
 public PositionService(PositionRepository repo,MarketIdentityClient market,@Value("${aistock.internal.token:}")String token,Clock clock,@Value("${aistock.positions.max-quantity:1000000000}")long maxQuantity,@Value("${aistock.positions.max-average-cost:1000000}")BigDecimal maxCost){if(maxQuantity<1||maxCost.signum()<0)throw new IllegalArgumentException("Invalid position limits");this.repo=repo;this.market=market;this.token=token;this.clock=clock;this.maxQuantity=maxQuantity;this.maxCost=maxCost;}
 public PositionBook list(Identity owner){return repo.list(owner.userId());}
 public PositionReceipt operation(Identity owner,UUID request){return repo.operation(owner.userId(),request);}
 public PositionReceipt apply(Identity owner,PositionMutation input){
  String hash=PositionRules.hash(input);var saved=repo.findReceipt(owner.userId(),input.requestId());if(saved!=null)return repo.replay(saved,hash);
  if(!input.action().equals("DELETE")){PositionRules.quantity(input.quantity(),maxQuantity);PositionRules.cost(input.averageCost(),maxCost);}
  if(input.action().equals("CREATE")){
   ApiResponse<MarketIdentityClient.InstrumentView> response;
   try{if(token.isBlank())throw new IllegalStateException("missing service configuration");response=market.get(input.instrumentId(),token);}catch(feign.FeignException e){if(e.status()==404)throw PositionRepository.error("RESOURCE_NOT_FOUND","证券不存在",404);throw unavailable();}catch(RuntimeException e){throw unavailable();}
   var item=response!=null&&"OK".equals(response.code())?response.data():null;
   if(item==null||item.demoOnly()||!input.instrumentId().equals(item.instrumentId())||!"stock-market-service".equals(item.sourceService()))throw PositionRepository.error("RESOURCE_NOT_FOUND","证券不存在或未核验",404);
  }
  return repo.apply(owner.userId(),input,hash,clock.instant());
 }
 private com.aistock.common.BusinessException unavailable(){return PositionRepository.error("MARKET_SERVICE_UNAVAILABLE","行情身份服务暂不可用，原持仓仍可维护",503);}
}
