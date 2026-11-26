package com.aistock.user.statistics;
import com.aistock.common.*;import com.aistock.common.statistics.*;import com.aistock.user.session.Identity;import jakarta.servlet.http.HttpServletRequest;import java.time.Clock;import org.springframework.http.HttpStatus;import org.springframework.security.core.Authentication;import org.springframework.util.MultiValueMap;import org.springframework.web.bind.annotation.*;
@RestController public class UserStatisticsController {
 private final UserStatisticsRepository repository;private final Clock clock;
 public UserStatisticsController(UserStatisticsRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
 @GetMapping("/api/v1/admin/statistics/users") public ApiResponse<UserStatisticsRecords.UsersSummary> summary(@RequestParam MultiValueMap<String,String> query,Authentication auth,HttpServletRequest request){
  if(auth==null||!(auth.getPrincipal() instanceof Identity id))throw new BusinessException("AUTH_REQUIRED","请先登录",HttpStatus.UNAUTHORIZED);
  if(!"ADMIN".equals(id.role()))throw new BusinessException("ACCESS_DENIED","仅管理员可以查看统计",HttpStatus.FORBIDDEN);
  return ApiResponse.success(repository.summary(StatisticsWindow.parse(query,clock),clock.instant()),TraceIds.normalize(request.getHeader("X-Trace-Id")));
 }
}
