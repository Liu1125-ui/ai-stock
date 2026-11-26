package com.aistock.user.session;
import com.aistock.common.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/v1/sessions")
public class SessionResolveController {
    public record ResolveInput(String token){@Override public String toString(){return "ResolveInput[redacted]";}}
    private final SessionService sessions;private final String serviceToken;
    public SessionResolveController(SessionService sessions,@Value("${aistock.internal.token:}")String token){this.sessions=sessions;this.serviceToken=token;}
    @PostMapping("/resolve")
    public ApiResponse<Identity> resolve(@RequestBody ResolveInput body,@RequestHeader(value="X-Service-Token",required=false)String token,
            @RequestHeader(value="X-Trace-Id",required=false)String trace){
        if(serviceToken.isBlank())throw new BusinessException("INTERNAL_AUTH_NOT_CONFIGURED","内部服务认证未配置",HttpStatus.SERVICE_UNAVAILABLE);
        if(token==null||!MessageDigest.isEqual(serviceToken.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))throw new BusinessException("INTERNAL_AUTH_REQUIRED","缺少有效服务身份",HttpStatus.UNAUTHORIZED);
        return ApiResponse.success(sessions.resolve(body.token()),TraceIds.normalize(trace));
    }
}
