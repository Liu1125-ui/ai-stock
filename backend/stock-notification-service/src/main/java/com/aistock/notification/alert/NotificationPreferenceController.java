package com.aistock.notification.alert;
import com.aistock.common.*;import com.fasterxml.jackson.databind.JsonNode;import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/notification-preferences") public class NotificationPreferenceController {
 private final NotificationPreferenceService prefs;private final NotificationAccess access;
 public NotificationPreferenceController(NotificationPreferenceService prefs,NotificationAccess access){this.prefs=prefs;this.access=access;}
 public record View(boolean emailEnabled,long revision,boolean emailVerified,String deliveryAvailability){}
 @GetMapping public ApiResponse<View> get(@RequestAttribute(NotificationSecurityFilter.OWNER)UUID owner,@RequestAttribute(NotificationSecurityFilter.SESSION)String session,@RequestHeader(value="X-Trace-Id",required=false)String trace){return response(prefs.get(owner),access.emailVerified(owner,session),trace);}
 @PatchMapping public ApiResponse<View> patch(@RequestAttribute(NotificationSecurityFilter.OWNER)UUID owner,@RequestAttribute(NotificationSecurityFilter.SESSION)String session,@RequestBody JsonNode body,@RequestHeader(value="X-Trace-Id",required=false)String trace){AlertController.fields(body,Set.of("revision","emailEnabled"));boolean enabled=AlertController.bool(body,"emailEnabled",true);boolean verified=access.emailVerified(owner,session);return response(prefs.patch(owner,AlertController.revision(body),enabled,verified),verified,trace);}
 private ApiResponse<View> response(AlertRecords.Preference p,boolean verified,String trace){return ApiResponse.success(new View(p.emailEnabled(),p.revision(),verified,p.deliveryAvailability()),TraceIds.normalize(trace));}
}
