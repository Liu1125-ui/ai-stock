package com.aistock.scheduler.task;
import com.aistock.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/v1/tasks")
public class TaskController {
    private final TaskRepository repository;
    public TaskController(TaskRepository repository){this.repository=repository;}
    @PostMapping public ApiResponse<TaskRepository.Task> create(@RequestBody Create body,HttpServletRequest request){return ok(repository.create(body.instrumentId(),body.targetDate(),body.key(),Instant.now()),request);}
    @PostMapping("/verifications") public ApiResponse<TaskRepository.Task> verification(@RequestBody VerificationCreate body,HttpServletRequest request){return ok(repository.createVerification(body.predictionId(),body.instrumentId(),body.dueThrough(),body.ruleVersion(),body.key(),Instant.now()),request);}
    @PostMapping("/verification-rechecks") public ApiResponse<TaskRepository.Task> recheck(@RequestBody Recheck body,HttpServletRequest request){return ok(repository.createRecheck(body.predictionId(),body.instrumentId(),body.dueThrough(),body.requestId(),Instant.now()),request);}
    @GetMapping public ApiResponse<List<TaskRepository.Task>> list(HttpServletRequest request){return ok(repository.list(),request);}
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> get(@PathVariable String id,HttpServletRequest request){return ok(Map.of("task",repository.get(id),"attempts",repository.attempts(id)),request);}
    @PostMapping("/{id}/retry") public ApiResponse<TaskRepository.Task> retry(@PathVariable String id,HttpServletRequest request){return ok(repository.retry(id,Instant.now()),request);}
    @PostMapping("/{id}/cancel") public ApiResponse<TaskRepository.Task> cancel(@PathVariable String id,HttpServletRequest request){return ok(repository.cancel(id,Instant.now()),request);}
    private <T> ApiResponse<T> ok(T value,HttpServletRequest request){return ApiResponse.success(value,TraceIds.normalize(request.getHeader("X-Trace-Id")));}
    public record Recheck(String predictionId,String instrumentId,LocalDate dueThrough,UUID requestId){}
    public record VerificationCreate(String predictionId,String instrumentId,LocalDate dueThrough,String ruleVersion,String key){}
    public record Create(String instrumentId,LocalDate targetDate,String key){}
}
