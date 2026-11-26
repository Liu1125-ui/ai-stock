package com.aistock.scheduler.adminrefresh;
import com.aistock.common.*;import com.aistock.common.adminrefresh.AdminMarketRefreshRecords.*;import java.time.Instant;import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;
@Service public class AdminMarketRefreshService {
 private final AdminMarketRefreshRepository repository;private final MarketRefreshContextClient market;private final String token;
 public AdminMarketRefreshService(AdminMarketRefreshRepository repository,MarketRefreshContextClient market,@Value("${aistock.internal.token:}")String token){this.repository=repository;this.market=market;this.token=token;}
 public Options options(String stock,Instant now){return repository.options(context(stock),now);}
 public Submission submit(UUID actor,String stock,Command command,Instant now){var replay=repository.existing(actor,stock,command);if(replay!=null)return replay;return repository.submit(actor,stock,command,context(stock),now);}
 public OperationView receipt(UUID id,Instant now){return repository.receipt(id,now);}
 public HistoryPage history(String stock,HistoryQuery q,Instant now){return repository.history(stock,q,now);}
 private Context context(String stock){try{if(token==null||token.isBlank())throw unavailable();var result=market.context(stock,token);if(result==null||!"OK".equals(result.code())||result.data()==null||!stock.equals(result.data().instrumentId()))throw unavailable();return result.data();}catch(feign.FeignException.NotFound e){throw new BusinessException("MARKET_ADMIN_RESOURCE_NOT_FOUND","未找到可管理的股票",HttpStatus.NOT_FOUND);}catch(RuntimeException e){throw unavailable();}}
 private BusinessException unavailable(){return new BusinessException("MARKET_ADMIN_UNAVAILABLE","行情预检暂不可用，请稍后重试",HttpStatus.SERVICE_UNAVAILABLE);}
}