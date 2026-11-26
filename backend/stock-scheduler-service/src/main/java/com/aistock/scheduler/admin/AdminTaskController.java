package com.aistock.scheduler.admin;
import com.aistock.common.*;import jakarta.servlet.http.HttpServletRequest;import org.springframework.web.bind.annotation.*;import org.springframework.util.MultiValueMap;import static com.aistock.scheduler.admin.AdminTaskRecords.*;
@RestController @RequestMapping("/api/v1/admin/tasks")
public class AdminTaskController {
 private final AdminTaskService service;
 public AdminTaskController(AdminTaskService service){this.service=service;}
 @GetMapping public ApiResponse<Page<TaskSummary>> list(@RequestParam MultiValueMap<String,String> query,HttpServletRequest r){return ok(service.list(AdminTaskQuery.parseList(query),query.getFirst("cursor")),r);}
 @GetMapping("/{id}") public ApiResponse<Detail> detail(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,HttpServletRequest r){AdminTaskQuery.noParameters(query);return ok(service.detail(AdminTaskQuery.validateId(id)),r);}
 @GetMapping("/{id}/attempts") public ApiResponse<Page<Attempt>> attempts(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,HttpServletRequest r){return ok(service.attempts(AdminTaskQuery.validateId(id),AdminTaskQuery.parseAttempts(query),query.getFirst("cursor")),r);}
 private <T>ApiResponse<T> ok(T data,HttpServletRequest r){return ApiResponse.success(data,TraceIds.normalize(r.getHeader("X-Trace-Id")));}
}
