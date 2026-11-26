package com.aistock.market.calendar;
import com.aistock.common.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/v1/market/calendar") public class CalendarController {
 private final CalendarService service;public CalendarController(CalendarService service){this.service=service;}
 @PostMapping("/refresh") public ApiResponse<CalendarRecords.Snapshot> refresh(@RequestBody com.fasterxml.jackson.databind.JsonNode body,@RequestHeader(value="X-Trace-Id",required=false)String trace){var fields=new java.util.HashSet<String>();if(body==null||!body.isObject())throw invalid();body.fieldNames().forEachRemaining(fields::add);if(!fields.equals(java.util.Set.of("operationId","start","end"))||!body.get("operationId").isTextual()||!body.get("start").isTextual()||!body.get("end").isTextual())throw invalid();CalendarRecords.Request request;try{request=new CalendarRecords.Request(body.get("operationId").textValue(),java.time.LocalDate.parse(body.get("start").textValue()),java.time.LocalDate.parse(body.get("end").textValue()));}catch(java.time.format.DateTimeParseException bad){throw invalid();}return ApiResponse.success(service.refresh(request),TraceIds.normalize(trace));}
 private static BusinessException invalid(){return new BusinessException("VALIDATION_FAILED","日历请求格式不正确",org.springframework.http.HttpStatus.BAD_REQUEST);}
 @GetMapping public ApiResponse<CalendarRecords.Snapshot> current(@RequestParam org.springframework.util.MultiValueMap<String,String> query,@RequestHeader(value="X-Trace-Id",required=false)String trace){
  if(query.isEmpty())return ApiResponse.success(service.current(),TraceIds.normalize(trace));
  if(!query.keySet().equals(java.util.Set.of("start","end"))||query.values().stream().anyMatch(v->v.size()!=1))throw invalid();
  java.time.LocalDate start,end;
  try{String a=query.getFirst("start"),b=query.getFirst("end");if(a==null||b==null||!a.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||!b.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid();start=java.time.LocalDate.parse(a);end=java.time.LocalDate.parse(b);}catch(java.time.format.DateTimeParseException bad){throw invalid();}
  if(start.isBefore(java.time.LocalDate.of(1990,1,1))||end.isAfter(java.time.LocalDate.of(2200,1,1))||end.isBefore(start)||java.time.temporal.ChronoUnit.DAYS.between(start,end)>=400)throw invalid();
  var snapshot=service.covering(start,end).orElseThrow(()->new BusinessException("CALENDAR_SOURCE_UNAVAILABLE","所需范围的有效交易日历暂不可用，旧版本保留",org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
  return ApiResponse.success(snapshot,TraceIds.normalize(trace));
 }
}
