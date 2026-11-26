package com.aistock.prediction.comparison;
import com.fasterxml.jackson.databind.*;import java.util.*;
public final class HttpComparisonMarketClient implements ComparisonMarketClient {
 private final BoundedComparisonHttp http;private final String base;
 public HttpComparisonMarketClient(String base,String token,ObjectMapper mapper){this.base=base;http=new BoundedComparisonHttp(token,mapper);}
 public JsonNode options(List<String> ids,String period){if(ids==null||ids.size()<2||ids.size()>3||ids.stream().anyMatch(i->i==null||!i.matches("(SH|SZ)[0-9]{6}"))||new HashSet<>(ids).size()!=ids.size()||!Set.of("3M","6M","1Y").contains(period==null?"":period))throw ComparisonErrors.invalid();return data(http.call(base,"/internal/v1/market/comparison-options?ids="+String.join(",",ids)+"&period="+period,null,5));}
 public JsonNode evidence(UUID id){if(id==null)throw ComparisonErrors.invalid();return data(http.call(base,"/internal/v1/market/comparison-evidence/"+id,null,5));}
 private JsonNode data(JsonNode n){if(!"OK".equals(n.path("code").asText())||!n.path("data").isObject())throw ComparisonErrors.unavailable();return n.get("data");}
}
