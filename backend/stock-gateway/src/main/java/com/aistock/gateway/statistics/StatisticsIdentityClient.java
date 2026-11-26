package com.aistock.gateway.statistics;
import com.aistock.common.ApiResponse;import com.fasterxml.jackson.annotation.JsonIgnoreProperties;import java.util.UUID;import org.springframework.cloud.openfeign.FeignClient;import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-user-service",contextId="statistics-identity",url="${aistock.user.url:}")
public interface StatisticsIdentityClient {
 record ResolveInput(String token){@Override public String toString(){return "ResolveInput[redacted]";}}
 @JsonIgnoreProperties(ignoreUnknown=true) record Identity(UUID userId,String role,@com.fasterxml.jackson.annotation.JsonProperty(required=true) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.aistock.common.json.StrictNonNegativeLongDeserializer.class) long credentialVersion){}
 @PostMapping("/internal/v1/sessions/resolve") ApiResponse<Identity> resolve(@RequestBody ResolveInput input,@RequestHeader("X-Service-Token")String token);
}
