package com.aistock.market.valuation;
import com.aistock.common.*;import com.fasterxml.jackson.core.JsonParser;import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;import java.time.LocalDate;import java.util.*;
import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
import static com.aistock.market.valuation.ValuationQuoteRecords.*;
@RestController public class ValuationQuoteController {
 private final ValuationQuoteService service;private final ObjectMapper json;
 public ValuationQuoteController(ValuationQuoteService service,ObjectMapper mapper){this.service=service;json=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);}
 @PostMapping("/internal/v1/market/valuation-quotes") public ApiResponse<QuoteBatch> quotes(HttpServletRequest request){return ApiResponse.success(service.batch(parse(request)),TraceIds.normalize(request.getHeader("X-Trace-Id")));}
 private QuoteRequest parse(HttpServletRequest r){try{
  if(!r.getParameterMap().isEmpty())throw invalid();var bytes=r.getInputStream().readNBytes(4097);if(bytes.length>4096)throw new BusinessException("MARKET_PAYLOAD_TOO_LARGE","报价请求超过大小限制",HttpStatus.PAYLOAD_TOO_LARGE);
  var n=json.readTree(bytes);if(n==null||!n.isObject()||n.size()!=2||!n.path("asOfDate").isTextual()||!n.path("asOfDate").asText().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||!n.path("instrumentIds").isArray())throw invalid();
  var ids=new ArrayList<String>();for(var id:n.get("instrumentIds")){if(!id.isTextual())throw invalid();ids.add(id.asText());}
  return new QuoteRequest(LocalDate.parse(n.get("asOfDate").asText()),List.copyOf(ids));
 }catch(BusinessException e){throw e;}catch(Exception e){throw invalid();}}
 private BusinessException invalid(){return new BusinessException("PORTFOLIO_QUOTE_REQUEST_INVALID","报价请求格式不正确",HttpStatus.BAD_REQUEST);}
}
