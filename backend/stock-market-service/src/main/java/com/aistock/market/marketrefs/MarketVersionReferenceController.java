package com.aistock.market.marketrefs;
import com.aistock.common.*;import com.aistock.common.adminmarket.AdminMarketQuery;import com.aistock.common.marketrefs.MarketReferenceRecords.Report;import com.aistock.market.adminquery.AdminMarketDataAccessFilter;import jakarta.servlet.http.*;import java.io.IOException;import org.springframework.http.HttpStatus;import org.springframework.util.MultiValueMap;import org.springframework.web.bind.annotation.*;
@RestController public class MarketVersionReferenceController {
 private final MarketVersionReferenceService service;
 public MarketVersionReferenceController(MarketVersionReferenceService service){this.service=service;}
 @GetMapping("/api/v1/admin/market-data/{instrumentId}/versions/{versionId}/references") public ApiResponse<Report> inspect(@PathVariable String instrumentId,@PathVariable String versionId,@RequestParam MultiValueMap<String,String> query,HttpServletRequest r,HttpServletResponse s)throws IOException{s.setHeader("Cache-Control","no-store");if(!Boolean.TRUE.equals(r.getAttribute(AdminMarketDataAccessFilter.ADMIN)))throw new BusinessException("ACCESS_DENIED","仅管理员可以检查引用",HttpStatus.FORBIDDEN);if(!query.isEmpty()||r.getInputStream().read()!=-1)throw AdminMarketQuery.invalid();return ApiResponse.success(service.inspect(instrumentId,versionId),TraceIds.normalize(r.getHeader("X-Trace-Id")));}
}
