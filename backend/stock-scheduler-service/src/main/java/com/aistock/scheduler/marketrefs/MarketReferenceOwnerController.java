package com.aistock.scheduler.marketrefs;
import com.aistock.common.*;import com.aistock.common.marketrefs.*;import com.aistock.common.marketrefs.MarketReferenceRecords.OwnerResult;import jakarta.servlet.http.*;import java.io.IOException;import org.springframework.web.bind.annotation.*;import org.springframework.util.MultiValueMap;
@RestController public class MarketReferenceOwnerController {
 private final ReferenceScanner scanner;
 public MarketReferenceOwnerController(ReferenceScanner scanner){this.scanner=scanner;}
 @GetMapping(MarketReferenceInput.INTERNAL_BASE+"/{versionId}") public ApiResponse<OwnerResult> inspect(@PathVariable String versionId,@RequestParam MultiValueMap<String,String> query,HttpServletRequest r,HttpServletResponse s)throws IOException{s.setHeader("Cache-Control","no-store");if(!query.isEmpty()||r.getInputStream().read()!=-1)throw com.aistock.common.adminmarket.AdminMarketQuery.invalid();return ApiResponse.success(scanner.scan(MarketReferenceInput.requireVersion(versionId)),TraceIds.normalize(r.getHeader("X-Trace-Id")));}
}
