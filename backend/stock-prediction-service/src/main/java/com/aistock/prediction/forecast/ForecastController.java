package com.aistock.prediction.forecast;
import com.aistock.common.*;import com.aistock.prediction.modelcatalog.ModelRecords.Actor;import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;import java.util.*;import org.springframework.security.core.Authentication;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
@RestController public class ForecastController {
 private final ForecastService service;private final ForecastRepository records;private final ObjectMapper mapper;
 public ForecastController(ForecastService service,ForecastRepository records,ObjectMapper mapper){this.service=service;this.records=records;this.mapper=mapper;}
 @PostMapping("/api/v1/predictions") public ApiResponse<?> create(Authentication auth,HttpServletRequest r){var user=user(auth);return ok(service.create(user,read(r)),r);}
 @GetMapping("/api/v1/predictions") public ApiResponse<?> list(Authentication auth,@RequestParam(defaultValue="1")int page,HttpServletRequest r){return ok(records.list(user(auth),page),r);}
 @GetMapping("/api/v1/predictions/latest-by-instrument")
 public ApiResponse<?> latest(Authentication auth,@RequestParam String ids,HttpServletRequest r){
  var owner=user(auth);
  if(ids.length()>179||r.getParameterValues("ids").length!=1||r.getParameterMap().size()!=1)
      throw ForecastRepository.error("PREDICTION_REQUEST_INVALID",HttpStatus.BAD_REQUEST);
  return ok(records.latestByInstrument(owner,Arrays.asList(ids.split(",",-1))),r);
 }
 @GetMapping("/api/v1/predictions/{id}") public ApiResponse<?> get(Authentication auth,@PathVariable UUID id,HttpServletRequest r){return ok(records.get(user(auth),id),r);}
 @PostMapping("/api/v1/predictions/{id}/hide") public ApiResponse<?> hide(Authentication auth,@PathVariable UUID id,HttpServletRequest r){var who=user(auth);try{var bytes=r.getInputStream().readNBytes(1025);if(bytes.length>1024)throw ForecastRepository.error("PREDICTION_REQUEST_INVALID",HttpStatus.BAD_REQUEST);var json=mapper.readTree(bytes);if(json!=null&&(!json.isObject()||!json.isEmpty()))throw ForecastRepository.error("PREDICTION_REQUEST_INVALID",HttpStatus.BAD_REQUEST);}catch(java.io.IOException e){throw ForecastRepository.error("PREDICTION_REQUEST_INVALID",HttpStatus.BAD_REQUEST);}return ok(records.hide(who,id),r);}
 private UUID user(Authentication auth){if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof Actor a))throw ForecastRepository.error("AUTH_REQUIRED",HttpStatus.UNAUTHORIZED);return a.userId();}
 private ForecastRecords.Input read(HttpServletRequest r){try{var bytes=r.getInputStream().readNBytes(4097);if(bytes.length>4096)throw ForecastRepository.error("PREDICTION_REQUEST_INVALID",HttpStatus.PAYLOAD_TOO_LARGE);return mapper.readValue(bytes,ForecastRecords.Input.class);}catch(java.io.IOException e){throw ForecastRepository.error("PREDICTION_REQUEST_INVALID",HttpStatus.BAD_REQUEST);}}
 private ApiResponse<?> ok(Object value,HttpServletRequest r){return ApiResponse.success(value,TraceIds.normalize(r.getHeader("X-Trace-Id")));}
}
