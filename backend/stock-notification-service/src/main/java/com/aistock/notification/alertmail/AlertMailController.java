package com.aistock.notification.alertmail;
import com.aistock.common.*;import com.aistock.notification.alert.NotificationSecurityFilter;import jakarta.servlet.http.HttpServletRequest;import java.util.UUID;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
@RestController public class AlertMailController {
 private final AlertMailViewService service;public AlertMailController(AlertMailViewService service){this.service=service;}
 @GetMapping("/api/v1/messages/{id}/email-delivery") public ApiResponse<AlertMailViewService.DeliveryView> get(@RequestAttribute(NotificationSecurityFilter.OWNER)UUID owner,@PathVariable String id,HttpServletRequest r){if(!r.getParameterMap().isEmpty()||id==null||!id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))throw new BusinessException("VALIDATION_FAILED","请求参数无效",HttpStatus.BAD_REQUEST);return ApiResponse.success(service.get(owner,UUID.fromString(id)),TraceIds.normalize(r.getHeader("X-Trace-Id")));}
}
