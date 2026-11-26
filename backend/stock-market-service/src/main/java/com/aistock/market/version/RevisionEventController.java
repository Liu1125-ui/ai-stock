package com.aistock.market.version;
import com.aistock.common.*;import jakarta.servlet.http.HttpServletRequest;import org.springframework.web.bind.annotation.*;
@RestController public class RevisionEventController {
 private final RevisionEventRepository repo;public RevisionEventController(RevisionEventRepository repo){this.repo=repo;}
 @GetMapping("/internal/v1/market/revision-events")public ApiResponse<?> events(@RequestParam(defaultValue="0")long after,@RequestParam(defaultValue="50")int limit,HttpServletRequest r){return ApiResponse.success(repo.events(after,limit),TraceIds.normalize(r.getHeader("X-Trace-Id")));}
}
