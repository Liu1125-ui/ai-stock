package com.aistock.prediction.verification;
import com.aistock.common.*;import com.aistock.prediction.modelcatalog.ModelRecords.Actor;
import jakarta.servlet.http.HttpServletRequest;import java.util.UUID;import org.springframework.security.core.Authentication;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
@RestController public final class VerificationHistoryController {
 private final VerificationQueryRepository records;
 public VerificationHistoryController(VerificationQueryRepository records){this.records=records;}
 @GetMapping("/api/v1/predictions/{id}/verifications") public ApiResponse<?> list(Authentication auth,@PathVariable UUID id,@RequestParam(defaultValue="1")int page,HttpServletRequest request){return ApiResponse.success(records.list(user(auth),id,page),TraceIds.normalize(request.getHeader("X-Trace-Id")));}
 @GetMapping("/api/v1/predictions/{id}/verifications/{version}") public ApiResponse<?> detail(Authentication auth,@PathVariable UUID id,@PathVariable UUID version,HttpServletRequest request){return ApiResponse.success(records.detail(user(auth),id,version),TraceIds.normalize(request.getHeader("X-Trace-Id")));}
 private UUID user(Authentication auth){if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof Actor a))throw VerificationRepository.error("AUTH_REQUIRED",HttpStatus.UNAUTHORIZED);return a.userId();}
}
