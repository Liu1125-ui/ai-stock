package com.aistock.prediction.verification;
import com.aistock.common.*;import java.time.LocalDate;import java.util.UUID;import jakarta.servlet.http.HttpServletRequest;import org.springframework.web.bind.annotation.*;
@RestController public final class VerificationDueController {
 private final VerificationDueRepository repo;public VerificationDueController(VerificationDueRepository repo){this.repo=repo;}
 @GetMapping("/internal/v1/prediction-verifications/affected") public ApiResponse<?> affected(@RequestParam String instrumentId,@RequestParam LocalDate dueThrough,@RequestParam(required=false)UUID after,@RequestParam(defaultValue="50")int limit,HttpServletRequest request){return ApiResponse.success(repo.affected(instrumentId,dueThrough,after,limit),TraceIds.normalize(request.getHeader("X-Trace-Id")));}
 @GetMapping("/internal/v1/prediction-verifications/due") public ApiResponse<?> due(@RequestParam LocalDate dueThrough,@RequestParam(required=false)UUID after,@RequestParam(defaultValue="50")int limit,HttpServletRequest request){return ApiResponse.success(repo.due(dueThrough,after,limit),TraceIds.normalize(request.getHeader("X-Trace-Id")));}
}
