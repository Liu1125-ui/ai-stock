package com.aistock.notification.alert;
import com.aistock.common.ApiResponse;import java.util.UUID;import org.springframework.cloud.openfeign.FeignClient;import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-user-service",contextId="notificationIdentity",url="${aistock.user.url:}",configuration=NotificationClientConfiguration.class)
public interface NotificationIdentityClient {
 record ResolveInput(String token){@Override public String toString(){return "ResolveInput[redacted]";}}
 record Identity(UUID userId,String role,boolean experimentAllowed,long credentialVersion){}
 record Profile(UUID userId,boolean emailVerified,String status){}
 @PostMapping("/internal/v1/sessions/resolve") ApiResponse<Identity> resolve(@RequestBody ResolveInput input,@RequestHeader("X-Service-Token")String token);
 @GetMapping("/api/v1/users/me") ApiResponse<Profile> profile(@RequestHeader("Cookie")String cookie);
}
