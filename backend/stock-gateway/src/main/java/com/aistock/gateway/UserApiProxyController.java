package com.aistock.gateway;
import com.aistock.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserApiProxyController {
    private final UserPublicClient client;private final ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Value("${SERVICE_TOKEN:}") private String serviceToken="";
    @org.springframework.beans.factory.annotation.Value("${aistock.proxy.trusted-host:frontend}") private String trustedProxyHost="frontend";
    public UserApiProxyController(UserPublicClient client,ObjectMapper mapper){this.client=client;this.mapper=mapper;}
    @RequestMapping({"/api/v1/auth/**","/api/v1/users/**","/api/v1/admin/**"})
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,@RequestParam Map<String,String> query,@RequestBody(required=false)byte[] body){
        String path=request.getRequestURI();String method=request.getMethod();
        if(path.equals("/api/v1/admin/market-data")||path.startsWith("/api/v1/admin/market-data/"))throw new BusinessException("RESOURCE_NOT_FOUND","接口不存在",HttpStatus.NOT_FOUND);
        if(path.equals("/api/v1/admin/statistics")||path.startsWith("/api/v1/admin/statistics/"))throw new BusinessException("RESOURCE_NOT_FOUND","接口不存在",HttpStatus.NOT_FOUND);
        if(path.equals("/api/v1/admin/tasks")||path.startsWith("/api/v1/admin/tasks/"))throw new BusinessException("RESOURCE_NOT_FOUND","接口不存在",HttpStatus.NOT_FOUND);
        if(!ProxyHeaderPolicy.allowed(method,path))throw new BusinessException("RESOURCE_NOT_FOUND","接口不存在",HttpStatus.NOT_FOUND);
        if(path.startsWith("/api/v1/users/me/positions")&&!request.getParameterMap().isEmpty())throw new BusinessException("VALIDATION_FAILED","持仓接口不接受额外查询参数",HttpStatus.BAD_REQUEST);
        var safeQuery=new LinkedHashMap<String,String>();
        for(String name:new String[]{"q","status","page","size"})if(query.containsKey(name))safeQuery.put(name,query.get(name));
        var headers=ProxyHeaderPolicy.request(request);String relative=path.substring("/api/v1/".length());
        if(!serviceToken.isBlank()){headers.put("X-Auth-Source",ClientSources.gateway(request,trustedProxyHost));headers.put("X-Service-Token",serviceToken);}
        byte[] payload=body==null?new byte[0]:body;
        try{
            ResponseEntity<byte[]> response=switch(method){
                case "GET" -> client.get(relative,safeQuery,headers);
                case "POST" -> client.post(relative,safeQuery,headers,payload);
                case "PATCH" -> client.patch(relative,safeQuery,headers,payload);
                case "PUT" -> client.put(relative,safeQuery,headers,payload);
                case "DELETE" -> path.startsWith("/api/v1/users/me/positions/")?client.deleteWithBody(relative,safeQuery,headers,payload):client.delete(relative,safeQuery,headers);
                case "OPTIONS" -> client.options(relative,headers);
                default -> throw new IllegalStateException("Unsupported public method");
            };
            return ResponseEntity.status(response.getStatusCode()).headers(ProxyHeaderPolicy.response(response.getHeaders())).body(response.getBody());
        }catch(feign.FeignException error){
            int status=error.status();
            if(status>=400&&status<500){
                try{
                    var json=mapper.readTree(error.contentUTF8());
                    if(json.hasNonNull("code")&&json.hasNonNull("message")&&json.path("data").isNull()){
                        byte[] safe=mapper.writeValueAsBytes(ApiResponse.failure(json.path("code").asText(),json.path("message").asText(),TraceIds.normalize(request.getHeader("X-Trace-Id"))));
                        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).header("Cache-Control","no-store").body(safe);
                    }
                }catch(java.io.IOException invalidResponse){/* Framework CORS may use a plain-text denial. */}
                if(status==403)throw new BusinessException("ACCESS_DENIED","请求来源或访问权限不允许",HttpStatus.FORBIDDEN);
            }
            throw unavailable();
        }catch(RuntimeException unreachable){throw unavailable();}
    }
    private BusinessException unavailable(){return new BusinessException("USER_SERVICE_UNAVAILABLE","账号服务暂不可用，请稍后重试",HttpStatus.SERVICE_UNAVAILABLE);}
}
