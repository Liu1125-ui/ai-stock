package com.aistock.user.security;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

public final class CookieWriter {
    public static final String SESSION="AISTOCK_SESSION";
    private final boolean secure;
    public CookieWriter(boolean secure){this.secure=secure;}
    public void issue(HttpServletResponse response,String token){write(response,token,8*3600);}
    public void clear(HttpServletResponse response){write(response,"",0);}
    private void write(HttpServletResponse response,String value,long seconds){
        response.addHeader("Set-Cookie",ResponseCookie.from(SESSION,value).path("/").httpOnly(true).secure(secure).sameSite("Lax").maxAge(seconds).build().toString());
    }
    public static String read(HttpServletRequest request){
        if(request.getCookies()!=null)for(var cookie:request.getCookies())if(SESSION.equals(cookie.getName()))return cookie.getValue();
        return null;
    }
}
