package com.aistock.market.adminquery;

import com.aistock.common.*;import com.aistock.common.adminmarket.AdminMarketQuery;
import jakarta.servlet.http.HttpServletRequest;import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;import java.util.UUID;
import org.springframework.http.HttpStatus;import org.springframework.util.MultiValueMap;import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AdminMarketQuery.BASE)
public class AdminMarketReadController {
 private final AdminMarketReadRepository repository;private final Clock clock;
 public AdminMarketReadController(AdminMarketReadRepository repository,Clock clock) {this.repository=repository;this.clock=clock;}
 @GetMapping public ApiResponse<AdminMarketRecords.HeadPage> heads(@RequestParam MultiValueMap<String,String> q,HttpServletRequest r,HttpServletResponse s) {guard(r,s);return ApiResponse.success(repository.heads(AdminMarketQuery.listing(q),clock.instant()),trace(r));}
 @GetMapping("/{instrumentId}/versions") public ApiResponse<AdminMarketRecords.VersionPage> versions(@PathVariable String instrumentId,@RequestParam MultiValueMap<String,String> q,HttpServletRequest r,HttpServletResponse s) {guard(r,s);return ApiResponse.success(repository.versions(instrumentId,AdminMarketQuery.history(q),clock.instant()),trace(r));}
 @GetMapping("/{instrumentId}/versions/{versionId}") public ApiResponse<AdminMarketRecords.VersionDetail> detail(@PathVariable String instrumentId,@PathVariable UUID versionId,@RequestParam MultiValueMap<String,String> q,HttpServletRequest r,HttpServletResponse s) {guard(r,s);return ApiResponse.success(repository.detail(instrumentId,versionId,AdminMarketQuery.detail(q,clock),clock.instant()),trace(r));}
 private static void guard(HttpServletRequest r,HttpServletResponse s) {s.setHeader("Cache-Control","no-store");if(!Boolean.TRUE.equals(r.getAttribute(AdminMarketDataAccessFilter.ADMIN)))throw new BusinessException("ACCESS_DENIED","仅管理员可以查看行情管理",HttpStatus.FORBIDDEN);}
 private static String trace(HttpServletRequest r) {return TraceIds.normalize(r.getHeader("X-Trace-Id"));}
}
