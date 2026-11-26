package com.aistock.prediction.analysis;
import com.fasterxml.jackson.databind.*;import java.time.*;import java.util.*;
public final class HttpAnalysisMarketClient implements AnalysisMarketClient {
 private final BoundedAnalysisHttp http;private final String base;
 public HttpAnalysisMarketClient(String base,String token,ObjectMapper mapper){this.base=base;http=new BoundedAnalysisHttp(token,mapper);}
 public JsonNode evidence(String id,LocalDate from,LocalDate to,UUID version){if(id==null||!id.matches("(SH|SZ)[0-9]{6}")||version==null||from==null||to==null)throw AnalysisFacts.invalid();var n=http.call(base,"/internal/v1/market/analysis-evidence/"+id+"?from="+from+"&to="+to+"&versionId="+version,null,5);var d=n.path("data");if(!"OK".equals(n.path("code").asText())||!id.equals(d.path("instrumentId").asText())||!version.toString().equalsIgnoreCase(d.path("versionId").asText())||!from.toString().equals(d.path("requestedRange").path("from").asText())||!to.toString().equals(d.path("requestedRange").path("to").asText()))throw AnalysisFacts.invalid();return d;}
}