package com.aistock.scheduler.security;
import com.aistock.common.ApiResponse;import java.util.UUID;import org.springframework.cloud.openfeign.FeignClient;import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-user-service",url="${aistock.user.url:}")
public interface SessionIdentityClient {
 record ResolveInput(String token){@Override public String toString(){return "ResolveInput[redacted]";}}
 record Identity(UUID userId,String role,boolean experimentAllowed,@com.fasterxml.jackson.annotation.JsonProperty(required=true) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.aistock.common.json.StrictNonNegativeLongDeserializer.class) long credentialVersion){}
 @PostMapping("/internal/v1/sessions/resolve") ApiResponse<Identity> resolve(@RequestBody ResolveInput input,@RequestHeader("X-Service-Token")String token);
}
