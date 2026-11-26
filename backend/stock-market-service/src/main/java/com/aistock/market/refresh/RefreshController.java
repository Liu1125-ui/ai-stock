package com.aistock.market.refresh;
import com.aistock.common.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/v1/market")
public class RefreshController {
    private final RefreshService service;private final RefreshRepository repository;
    public RefreshController(RefreshService service,RefreshRepository repository){this.service=service;this.repository=repository;}
    @GetMapping("/refresh/{operationId}") public ApiResponse<RefreshResult> receipt(@PathVariable String operationId,HttpServletRequest request){return ApiResponse.success(repository.lookup(operationId,java.time.Clock.systemUTC()),TraceIds.normalize(request.getHeader("X-Trace-Id")));}
    @PostMapping("/refresh") public ApiResponse<RefreshResult> refresh(@RequestBody RefreshRequest body,HttpServletRequest request){return ApiResponse.success(service.refresh(body),TraceIds.normalize(request.getHeader("X-Trace-Id")));}
}
