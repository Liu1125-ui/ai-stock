package com.aistock.user.registration;
import com.aistock.common.ApiResponse;
import com.aistock.common.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {
    public record EmailInput(String email) { }
    private final RegistrationService service;
    @org.springframework.beans.factory.annotation.Value("${aistock.internal.token:}") private String serviceToken="";
    public RegistrationController(RegistrationService service) { this.service=service; }
    @PostMapping("/registration-challenges") @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ChallengeView> request(@RequestBody EmailInput body,HttpServletRequest request) {
        return ApiResponse.success(service.request(body.email(),com.aistock.common.ClientSources.user(request,serviceToken)),TraceIds.normalize(request.getHeader("X-Trace-Id")));
    }
    @GetMapping("/registration-challenges/{challengeId}")
    public ApiResponse<ChallengeView> status(@PathVariable UUID challengeId,HttpServletRequest request) {
        return ApiResponse.success(service.status(challengeId,com.aistock.common.ClientSources.user(request,serviceToken)),TraceIds.normalize(request.getHeader("X-Trace-Id")));
    }
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegistrationResult>> register(@RequestBody RegisterCommand body,HttpServletRequest request) {
        var result=service.register(body,com.aistock.common.ClientSources.user(request,serviceToken));
        return ResponseEntity.status(result.replayed()?HttpStatus.OK:HttpStatus.CREATED)
                .body(ApiResponse.success(result,TraceIds.normalize(request.getHeader("X-Trace-Id"))));
    }
}
