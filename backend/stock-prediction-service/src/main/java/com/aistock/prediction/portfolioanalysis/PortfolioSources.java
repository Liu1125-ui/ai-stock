package com.aistock.prediction.portfolioanalysis;
import com.aistock.prediction.MarketDependencyGuard;import com.fasterxml.jackson.databind.*;import java.time.LocalDate;import java.util.List;
import static com.aistock.prediction.portfolioanalysis.PortfolioRecords.*;
public class PortfolioSources {
 private final String userBase,marketBase;private final PortfolioDependencyHttp http;private final ObjectMapper mapper;private final MarketDependencyGuard guard;
 public PortfolioSources(String userBase,String marketBase,PortfolioDependencyHttp http,ObjectMapper mapper,MarketDependencyGuard guard){this.userBase=userBase;this.marketBase=marketBase;this.http=http;this.mapper=mapper;this.guard=guard;}
 public PositionSnapshot snapshot(SessionToken token){return decode(http.call(userBase,"/internal/v1/positions/snapshot",new SnapshotRequest(token.value()),5),PositionSnapshot.class);}
 public PositionRevision revision(SessionToken token){return decode(http.call(userBase,"/internal/v1/positions/revision",new SnapshotRequest(token.value()),3),PositionRevision.class);}
 public QuoteBatch quotes(List<String> ids,LocalDate asOf){return guard.invoke(()->decode(http.call(marketBase,"/internal/v1/market/valuation-quotes",new QuoteRequest(asOf,ids),8),QuoteBatch.class));}
 private <T>T decode(JsonNode n,Class<T> type){try{return mapper.treeToValue(n,type);}catch(Exception e){throw PortfolioErrors.invalid();}}
}
