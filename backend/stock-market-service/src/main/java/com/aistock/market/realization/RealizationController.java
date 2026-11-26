package com.aistock.market.realization;
import com.aistock.common.*;import com.fasterxml.jackson.databind.*;import jakarta.servlet.http.HttpServletRequest;import java.util.UUID;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/v1/market/realizations") public class RealizationController {
 private final RealizationService service;private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
 public RealizationController(RealizationService service){this.service=service;}
 @PostMapping("/prepare")public ApiResponse<?> prepare(HttpServletRequest r){try{var bytes=r.getInputStream().readNBytes(4097);if(bytes.length>4096)throw new BusinessException("REALIZATION_PAYLOAD_TOO_LARGE","真实验证请求超过上限",HttpStatus.PAYLOAD_TOO_LARGE);var request=mapper.readValue(bytes,RealizationRecords.Request.class);return ApiResponse.success(service.prepare(request),TraceIds.normalize(r.getHeader("X-Trace-Id")));}catch(java.io.IOException e){throw new BusinessException("VALIDATION_FAILED","请求格式或字段不正确",HttpStatus.BAD_REQUEST);}}
 @GetMapping("/operations/{id}")public ApiResponse<?> operation(@PathVariable UUID id){return ApiResponse.success(service.operation(id),TraceIds.normalize(null));}
 @GetMapping("/{id}")public ApiResponse<?> version(@PathVariable UUID id){return ApiResponse.success(service.version(id.toString()),TraceIds.normalize(null));}
}
