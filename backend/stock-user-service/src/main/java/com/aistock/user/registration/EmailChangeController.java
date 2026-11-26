package com.aistock.user.registration;

import com.aistock.common.ApiResponse;
import com.aistock.common.ClientSources;
import com.aistock.common.TraceIds;
import com.aistock.user.account.ProfileController;
import com.aistock.user.security.CookieWriter;
import com.aistock.user.session.Identity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
public class EmailChangeController {
    public record RequestInput(String email,String currentPassword) {
        @Override public String toString(){return "RequestInput[redacted]";}
    }
    public record ConfirmInput(UUID challengeId,String code) {
        @Override public String toString(){return "ConfirmInput[redacted]";}
    }
    private final EmailChangeService service;
    private final CookieWriter cookies;
    private final CookieCsrfTokenRepository csrf;
    private final String token;
    public EmailChangeController(EmailChangeService service,CookieWriter cookies,CookieCsrfTokenRepository csrf,
            @Value("${aistock.internal.token}") String token) {
        this.service=service; this.cookies=cookies; this.csrf=csrf; this.token=token;
    }
    @PostMapping("/email-change-challenges")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ChallengeView> request(Authentication auth,@RequestBody RequestInput input,HttpServletRequest request) {
        var owner=ProfileController.owner(auth);
        return ApiResponse.success(service.request(owner,version(auth),input.email(),input.currentPassword(),source(request)),trace(request));
    }
    @GetMapping("/email-change-challenges/{id}")
    public ApiResponse<ChallengeView> status(Authentication auth,@PathVariable UUID id,HttpServletRequest request) {
        return ApiResponse.success(service.status(ProfileController.owner(auth),version(auth),id,source(request)),trace(request));
    }
    @PostMapping("/email-change")
    public ApiResponse<Void> confirm(Authentication auth,@RequestBody ConfirmInput input,HttpServletRequest request,HttpServletResponse response) {
        service.confirm(ProfileController.owner(auth),version(auth),input.challengeId(),input.code(),source(request));
        cookies.clear(response);
        csrf.saveToken(null,request,response);
        return ApiResponse.success(null,trace(request));
    }
    private long version(Authentication auth) { return ((Identity)auth.getPrincipal()).credentialVersion(); }
    private String source(HttpServletRequest request) { return ClientSources.user(request,token); }
    private String trace(HttpServletRequest request) { return TraceIds.normalize(request.getHeader("X-Trace-Id")); }
}
