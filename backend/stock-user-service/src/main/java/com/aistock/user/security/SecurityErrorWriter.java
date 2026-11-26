package com.aistock.user.security;
import com.aistock.common.ApiResponse;
import com.aistock.common.TraceIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class SecurityErrorWriter {
    private final ObjectMapper mapper;
    public SecurityErrorWriter(ObjectMapper mapper){this.mapper=mapper;}
    public void write(HttpServletRequest request,HttpServletResponse response,int status,String code,String message)throws IOException{
        response.setStatus(status);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(),ApiResponse.failure(code,message,TraceIds.normalize(request.getHeader("X-Trace-Id"))));
    }
}
