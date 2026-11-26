package com.aistock.user.account;
import com.aistock.common.*;
import com.aistock.user.security.CookieWriter;
import com.aistock.user.session.Identity;
import jakarta.servlet.http.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/users/me")
public class ProfileController {
    public record RenameInput(String nickname,Long revision,String avatarStyle){}
    public record PasswordInput(String currentPassword,String newPassword){@Override public String toString(){return "PasswordInput[redacted]";}}
    private final ProfileService profiles;private final CookieWriter cookies;private final CookieCsrfTokenRepository csrf;
    public ProfileController(ProfileService profiles,CookieWriter cookies,CookieCsrfTokenRepository csrf){this.profiles=profiles;this.cookies=cookies;this.csrf=csrf;}
    @GetMapping public ApiResponse<ProfileView> me(Authentication authentication,@RequestHeader(value="X-Trace-Id",required=false)String trace){return ApiResponse.success(profiles.me(owner(authentication)),TraceIds.normalize(trace));}
    @PatchMapping public ApiResponse<ProfileView> rename(Authentication authentication,@RequestBody RenameInput body,@RequestHeader(value="X-Trace-Id",required=false)String trace){
        if(body.revision()==null)throw invalid();return ApiResponse.success(profiles.update(owner(authentication),body.nickname(),body.revision(),body.avatarStyle()),TraceIds.normalize(trace));
    }
    @PutMapping("/password") public ApiResponse<Void> password(Authentication authentication,@RequestBody PasswordInput body,HttpServletRequest request,HttpServletResponse response){
        profiles.changePassword(owner(authentication),body.currentPassword(),body.newPassword());cookies.clear(response);csrf.saveToken(null,request,response);
        return ApiResponse.success(null,TraceIds.normalize(request.getHeader("X-Trace-Id")));
    }
    public static UUID owner(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof Identity identity))throw new BusinessException("AUTH_REQUIRED","请先登录",HttpStatus.UNAUTHORIZED);return identity.userId();}
    public static BusinessException invalid(){return new BusinessException("VALIDATION_FAILED","缺少必填参数",HttpStatus.BAD_REQUEST);}
}
