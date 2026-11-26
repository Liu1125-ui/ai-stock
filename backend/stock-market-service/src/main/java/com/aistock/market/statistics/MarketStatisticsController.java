package com.aistock.market.statistics;
import com.aistock.common.*;import com.aistock.common.statistics.*;import jakarta.servlet.http.HttpServletRequest;import java.time.Clock;import java.util.Set;import org.springframework.http.HttpStatus;import org.springframework.util.MultiValueMap;import org.springframework.web.bind.annotation.*;
@RestController public class MarketStatisticsController {
 private final MarketStatisticsRepository repository;private final Clock clock;
 public MarketStatisticsController(MarketStatisticsRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
 @GetMapping("/api/v1/admin/statistics/market") public ApiResponse<MarketStatisticsRecords.MarketSummary> summary(@RequestParam MultiValueMap<String,String> q,HttpServletRequest r){if(!Boolean.TRUE.equals(r.getAttribute(AdminStatisticsAccessFilter.ADMIN)))throw new BusinessException("AUTH_REQUIRED","请先登录",HttpStatus.UNAUTHORIZED);var w=StatisticsQuery.window(q,clock,Set.of("page","size"));return ApiResponse.success(repository.summary(w,StatisticsQuery.page(q),StatisticsQuery.size(q),clock.instant()),TraceIds.normalize(r.getHeader("X-Trace-Id")));}
}
