package com.aistock.user.session;
import com.aistock.common.ApiResponse;
import com.aistock.common.TraceIds;
import com.aistock.user.security.CookieWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    public record LoginInput(String email,String password){@Override public String toString(){return "LoginInput[redacted]";}}
    private final SessionService sessions;private final CookieWriter cookies;private final CookieCsrfTokenRepository csrf;
    @org.springframework.beans.factory.annotation.Value("${aistock.internal.token:}") private String serviceToken="";
    public AuthController(SessionService sessions,CookieWriter cookies,CookieCsrfTokenRepository csrf){this.sessions=sessions;this.cookies=cookies;this.csrf=csrf;}
    @GetMapping("/csrf")
    public ApiResponse<Map<String,String>> csrf(CsrfToken token,HttpServletRequest request){
        return ApiResponse.success(Map.of("token",token.getToken(),"headerName",token.getHeaderName()),TraceIds.normalize(request.getHeader("X-Trace-Id")));
    }
    @PostMapping("/login")
    public ApiResponse<Identity> login(@RequestBody LoginInput body,HttpServletRequest request,HttpServletResponse response){
        var issued=sessions.login(body.email(),body.password(),com.aistock.common.ClientSources.user(request,serviceToken));
        cookies.issue(response,issued.rawToken());csrf.saveToken(null,request,response);
        return ApiResponse.success(issued.identity(),TraceIds.normalize(request.getHeader("X-Trace-Id")));
    }
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request,HttpServletResponse response){
        sessions.logout(CookieWriter.read(request));cookies.clear(response);csrf.saveToken(null,request,response);
        return ApiResponse.success(null,TraceIds.normalize(request.getHeader("X-Trace-Id")));
    }
}
