package com.aistock.prediction.security;
import com.aistock.common.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-user-service",url="${aistock.user.url:}")
public interface SessionIdentityClient {
    record ResolveInput(String token){@Override public String toString(){return "ResolveInput[redacted]";}}
    record Identity(UUID userId,String role,@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=ExperimentGrantDeserializer.class) boolean experimentAllowed,@com.fasterxml.jackson.annotation.JsonProperty(required=true) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.aistock.common.json.StrictNonNegativeLongDeserializer.class) long credentialVersion){}
    final class ExperimentGrantDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<Boolean> {
        @Override public Boolean deserialize(com.fasterxml.jackson.core.JsonParser input,
            com.fasterxml.jackson.databind.DeserializationContext context) throws java.io.IOException {
            return switch(input.currentToken()) {
                case VALUE_TRUE -> true;
                case VALUE_FALSE -> false;
                default -> throw com.fasterxml.jackson.databind.exc.MismatchedInputException.from(
                    input,boolean.class,"experimentAllowed must be a JSON boolean");
            };
        }
        @Override public Boolean getNullValue(com.fasterxml.jackson.databind.DeserializationContext context) {return false;}
    }
    @PostMapping("/internal/v1/sessions/resolve") ApiResponse<Identity> resolve(@RequestBody ResolveInput input,@RequestHeader("X-Service-Token")String token);
}
