package com.aistock.prediction.verification;
import com.aistock.common.*;import com.fasterxml.jackson.databind.*;import jakarta.servlet.http.HttpServletRequest;import java.util.UUID;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
@RestController public class VerificationController {
 private final VerificationService service;private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
 public VerificationController(VerificationService service){this.service=service;}
 @PostMapping("/internal/v1/predictions/{id}/verify")public ApiResponse<?> verify(@PathVariable UUID id,HttpServletRequest r){try{var bytes=r.getInputStream().readNBytes(4097);if(bytes.length>4096)throw new BusinessException("VERIFICATION_PAYLOAD_TOO_LARGE","验证请求超过上限",HttpStatus.PAYLOAD_TOO_LARGE);return ApiResponse.success(service.verify(id,mapper.readValue(bytes,VerificationRequest.class)),TraceIds.normalize(r.getHeader("X-Trace-Id")));}catch(java.io.IOException e){throw new BusinessException("VALIDATION_FAILED","请求格式或字段不正确",HttpStatus.BAD_REQUEST);}}
 @GetMapping("/internal/v1/prediction-verifications/operations/{id}")public ApiResponse<?> operation(@PathVariable UUID id){return ApiResponse.success(service.operation(id),TraceIds.normalize(null));}
 @GetMapping("/internal/v1/predictions/{id}/verification")public ApiResponse<?> current(@PathVariable UUID id){return ApiResponse.success(service.current(id),TraceIds.normalize(null));}
}
