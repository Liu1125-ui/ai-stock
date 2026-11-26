package com.aistock.user.admin;
import com.aistock.common.*;
import com.aistock.user.account.*;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    public record StatusInput(String status,Long revision){}
    public record GrantInput(Boolean enabled,Long revision){}
    private final AdminUserService admins;
    public AdminUserController(AdminUserService admins){this.admins=admins;}
    @GetMapping public ApiResponse<AdminUserService.UserPage> list(Authentication auth,@RequestParam(defaultValue="")String q,
            @RequestParam(required=false)String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,
            @RequestHeader(value="X-Trace-Id",required=false)String trace){return ApiResponse.success(admins.list(ProfileController.owner(auth),q,status,page,size),TraceIds.normalize(trace));}
    @PatchMapping("/{userId}/status") public ApiResponse<ProfileView> status(Authentication auth,@PathVariable UUID userId,@RequestBody StatusInput body,@RequestHeader(value="X-Trace-Id",required=false)String trace){
        if(body.revision()==null)throw ProfileController.invalid();return ApiResponse.success(admins.setStatus(ProfileController.owner(auth),userId,body.status(),body.revision(),trace),TraceIds.normalize(trace));}
    @PutMapping("/{userId}/experiment-grant") public ApiResponse<ProfileView> grant(Authentication auth,@PathVariable UUID userId,@RequestBody GrantInput body,@RequestHeader(value="X-Trace-Id",required=false)String trace){
        if(body.revision()==null||body.enabled()==null)throw ProfileController.invalid();return ApiResponse.success(admins.setExperimentGrant(ProfileController.owner(auth),userId,body.enabled(),body.revision(),trace),TraceIds.normalize(trace));}
}
