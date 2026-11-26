package com.aistock.prediction.statistics;
import com.aistock.common.*;import com.aistock.common.statistics.*;import com.aistock.prediction.modelcatalog.ModelRecords.Actor;import jakarta.servlet.http.HttpServletRequest;import java.time.Clock;import org.springframework.http.HttpStatus;import org.springframework.security.core.Authentication;import org.springframework.util.MultiValueMap;import org.springframework.web.bind.annotation.*;
@RestController public class PredictionStatisticsController {
 private final PredictionStatisticsRepository repository;private final Clock clock;
 public PredictionStatisticsController(PredictionStatisticsRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
 @GetMapping("/api/v1/admin/statistics/predictions") public ApiResponse<PredictionStatisticsRecords.PredictionsSummary> summary(@RequestParam MultiValueMap<String,String> q,Authentication auth,HttpServletRequest r){
  if(auth==null||!(auth.getPrincipal() instanceof Actor actor))throw new BusinessException("AUTH_REQUIRED","请先登录",HttpStatus.UNAUTHORIZED);if(!"ADMIN".equals(actor.role()))throw new BusinessException("ACCESS_DENIED","仅管理员可以查看统计",HttpStatus.FORBIDDEN);
  return ApiResponse.success(repository.summary(StatisticsWindow.parse(q,clock),clock.instant()),TraceIds.normalize(r.getHeader("X-Trace-Id")));
 }
}
