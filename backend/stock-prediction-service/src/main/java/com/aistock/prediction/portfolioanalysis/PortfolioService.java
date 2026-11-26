package com.aistock.prediction.portfolioanalysis;
import com.aistock.common.BusinessException;import com.aistock.prediction.marketuse.*;import com.aistock.prediction.modelcatalog.ModelRecords.Actor;import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;import java.time.*;import java.util.*;import java.math.BigDecimal;import java.util.concurrent.Semaphore;
import static com.aistock.prediction.portfolioanalysis.PortfolioRecords.*;
public class PortfolioService {
 private final PortfolioMarketUseOwner marketUses;private final PortfolioRepository repo;private final PortfolioSources sources;private final Clock clock;private final BigDecimal threshold;private final Semaphore permits;private final PortfolioCalculator calculator=new PortfolioCalculator();
 public PortfolioService(PortfolioRepository repo,PortfolioSources sources,Clock clock,BigDecimal threshold,int concurrency){this(repo,sources,clock,threshold,concurrency,null);}
 public PortfolioService(PortfolioRepository repo,PortfolioSources sources,Clock clock,BigDecimal threshold,int concurrency,PortfolioMarketUseOwner marketUses){this.marketUses=marketUses;if(concurrency<1||concurrency>8||threshold==null||threshold.signum()<=0||threshold.compareTo(new BigDecimal("100"))>0||threshold.scale()>6)throw new IllegalArgumentException("Invalid portfolio policy");this.repo=repo;this.sources=sources;this.clock=clock;this.threshold=threshold;permits=new Semaphore(concurrency);}
 public Creation create(Actor actor,SessionToken token,Request request){
  UUID owner=owner(actor);if(token==null||token.value()==null||!token.value().matches("[A-Za-z0-9_-]{43}"))throw new BusinessException("AUTH_REQUIRED","请重新登录",HttpStatus.UNAUTHORIZED);if(request==null||request.requestId()==null)throw PortfolioErrors.invalid();
  var previous=repo.find(owner,request.requestId(),clock.instant());if(previous!=null)return new Creation(previous,true);
  if(!permits.tryAcquire())throw new BusinessException("PORTFOLIO_BUSY","持仓分析正在处理中，请稍后再试",HttpStatus.TOO_MANY_REQUESTS);
  Claim claim=null;
  try{
   Instant accepted=clock.instant();var local=accepted.atZone(ZoneId.of("Asia/Shanghai"));LocalDate cutoff=local.toLocalTime().isBefore(LocalTime.of(20,0))?local.toLocalDate().minusDays(1):local.toLocalDate();
   claim=repo.claim(owner,request.requestId(),accepted,cutoff,threshold);if(!claim.execute())return new Creation(claim.operation(),true);
   Instant deadline=claim.operation().expiresAt();check(deadline);var snapshot=sources.snapshot(token);check(deadline);
   new PortfolioSourceVerifier().verifySnapshot(snapshot,owner);
   try(var scope=marketUses==null||snapshot.items().isEmpty()?MarketUseContext.empty():marketUses.acquire(owner,request.requestId(),claim.generation(),snapshot.items().stream().map(PositionItem::instrumentId).toList())){check(deadline);
   QuoteBatch quotes=snapshot.items().isEmpty()?new QuoteBatch(cutoff,clock.instant(),List.of()):sources.quotes(snapshot.items().stream().map(PositionItem::instrumentId).toList(),cutoff);check(deadline);
   new PortfolioSourceVerifier().verify(snapshot,quotes,owner,cutoff);if(!MarketUseContext.expectedVersions().isEmpty()){var actual=quotes.items().stream().map(Quote::versionId).filter(Objects::nonNull).map(UUID::toString).sorted().toList();if(!actual.equals(MarketUseContext.expectedVersions()))throw MarketUseClient.invalid();}var calculation=calculator.calculate(snapshot,quotes,threshold);check(deadline);
   var revision=sources.revision(token);check(deadline);
   if(revision==null||!owner.equals(revision.ownerId())||!"stock-user-service".equals(revision.sourceService()))throw PortfolioErrors.invalid();
   if(!snapshot.bookRevision().equals(revision.bookRevision()))throw new BusinessException("PORTFOLIO_CHANGED","持仓已改变，请重新读取后生成新分析",HttpStatus.CONFLICT);
   return new Creation(repo.complete(owner,request.requestId(),claim.generation(),calculation,snapshot,quotes,clock),false);
   }
  }catch(BusinessException e){saveFailure(owner,request.requestId(),claim,e.code());throw e;}
   catch(RuntimeException e){saveFailure(owner,request.requestId(),claim,"PORTFOLIO_SOURCE_UNAVAILABLE");throw PortfolioErrors.unavailable();}
  finally{if(marketUses!=null&&claim!=null&&claim.execute())marketUses.notifyCompleted(claim.generation());permits.release();}
 }
 public Operation operation(Actor a,UUID request){return repo.operation(owner(a),request,clock.instant());}
 public JsonNode get(Actor a,UUID id){return repo.get(owner(a),id);}
 public PortfolioRepository.Page list(Actor a,String before,String upper){return repo.list(owner(a),before,upper);}
 private UUID owner(Actor a){if(a==null||a.userId()==null||!Set.of("USER","ADMIN").contains(a.role()==null?"":a.role()))throw new BusinessException("AUTH_REQUIRED","请重新登录",HttpStatus.UNAUTHORIZED);return a.userId();}
 private void check(Instant deadline){if(deadline==null||!clock.instant().isBefore(deadline))throw new BusinessException("PORTFOLIO_OPERATION_EXPIRED","本次分析处理已超时，请查询原请求",HttpStatus.CONFLICT);}
 private void saveFailure(UUID owner,UUID request,Claim claim,String code){if(claim==null||!claim.execute())return;try{repo.fail(owner,request,claim.generation(),code,clock);}catch(RuntimeException ignored){/* Original failure is preserved; durable RUNNING expires on lookup. */}}
}
