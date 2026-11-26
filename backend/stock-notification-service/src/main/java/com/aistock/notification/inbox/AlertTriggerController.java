package com.aistock.notification.inbox;
import com.aistock.common.ApiResponse;import com.aistock.notification.alert.NotificationSecurityFilter;import jakarta.servlet.http.HttpServletRequest;import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController public class AlertTriggerController {
 private final InboxService service;public AlertTriggerController(InboxService service){this.service=service;}
 @GetMapping("/api/v1/alerts/{id}/triggers") public ApiResponse<InboxRecords.TriggerPage> history(@RequestAttribute(NotificationSecurityFilter.OWNER)UUID owner,@PathVariable String id,HttpServletRequest r){InboxController.query(r,Set.of("page","size"));return InboxController.ok(service.triggers(owner,InboxController.uuid(id),InboxController.page(r,"page",1),InboxController.page(r,"size",20)),r);}
}
