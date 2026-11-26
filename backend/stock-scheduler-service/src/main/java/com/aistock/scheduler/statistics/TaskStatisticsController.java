package com.aistock.scheduler.statistics;
import com.aistock.common.*;import com.aistock.common.statistics.*;import com.aistock.scheduler.security.AdminTaskPrincipal;import jakarta.servlet.http.HttpServletRequest;import java.time.Clock;import org.springframework.http.HttpStatus;import org.springframework.util.MultiValueMap;import org.springframework.web.bind.annotation.*;
@RestController public class TaskStatisticsController {
 private final TaskStatisticsRepository repository;private final Clock clock;
 public TaskStatisticsController(TaskStatisticsRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
 @GetMapping("/api/v1/admin/statistics/tasks")public ApiResponse<TaskStatisticsRecords.TasksSummary> summary(@RequestParam MultiValueMap<String,String> query,HttpServletRequest r){if(!(r.getAttribute(AdminTaskPrincipal.class.getName()) instanceof AdminTaskPrincipal))throw new BusinessException("AUTH_REQUIRED","请先登录",HttpStatus.UNAUTHORIZED);return ApiResponse.success(repository.summary(StatisticsWindow.parse(query,clock),clock.instant()),TraceIds.normalize(r.getHeader("X-Trace-Id")));}
}
