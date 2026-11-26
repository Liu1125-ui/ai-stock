package com.aistock.gateway;
import com.aistock.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class NotificationApiProxyController {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(NotificationApiProxyController.class);
    private final NotificationPublicClient client;private final ObjectMapper mapper;
    public NotificationApiProxyController(NotificationPublicClient client,ObjectMapper mapper){this.client=client;this.mapper=mapper;}
    @RequestMapping({"/api/v1/alerts","/api/v1/alerts/{id}","/api/v1/notification-preferences","/api/v1/alerts/{id}/triggers","/api/v1/messages","/api/v1/messages/unread-count","/api/v1/messages/{id}/read","/api/v1/messages/read","/api/v1/messages/{id}/email-delivery"})
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,@RequestParam Map<String,String> query) throws java.io.IOException{
        String path=request.getRequestURI();String method=request.getMethod();
        if(!ProxyHeaderPolicy.allowed(method,path))throw new BusinessException("RESOURCE_NOT_FOUND","接口不存在",HttpStatus.NOT_FOUND);
        if(path.startsWith("/api/v1/messages")||path.endsWith("/triggers")){var allowed=path.equals("/api/v1/messages")?java.util.Set.of("page","size","unreadOnly"):path.endsWith("/triggers")?java.util.Set.of("page","size"):java.util.Set.<String>of();for(var entry:request.getParameterMap().entrySet())if(!allowed.contains(entry.getKey())||entry.getValue().length!=1)throw new BusinessException("VALIDATION_FAILED","请求参数无效",HttpStatus.BAD_REQUEST);}
        var safeQuery=new LinkedHashMap<String,String>();
        for(String name:new String[]{"page","size","unreadOnly"})if(query.containsKey(name))safeQuery.put(name,query.get(name));
        var headers=ProxyHeaderPolicy.request(request);String relative=path.substring("/api/v1/".length());
        byte[] payload=request.getInputStream().readNBytes(4097);
        if(payload.length>4096)throw new BusinessException("PAYLOAD_TOO_LARGE","请求内容过长",HttpStatus.PAYLOAD_TOO_LARGE);
        try{
            ResponseEntity<byte[]> response=switch(method){
                case "GET" -> client.get(relative,safeQuery,headers);
                case "POST" -> client.post(relative,safeQuery,headers,payload);
                case "PATCH" -> client.patch(relative,safeQuery,headers,payload);
                case "OPTIONS" -> client.options(relative,headers);
                default -> throw new IllegalStateException("Unsupported public method");
            };
            return ResponseEntity.status(response.getStatusCode()).headers(ProxyHeaderPolicy.response(response.getHeaders())).body(response.getBody());
        }catch(feign.FeignException error){
            int status=error.status();LOG.warn("Notification proxy rejected: status={}",status);
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
        }catch(RuntimeException unreachable){LOG.warn("Notification proxy failed: type={}",unreachable.getClass().getSimpleName());throw unavailable();}
    }
    private BusinessException unavailable(){return new BusinessException("NOTIFICATION_SERVICE_UNAVAILABLE","预警服务暂不可用，请稍后重试",HttpStatus.SERVICE_UNAVAILABLE);}
}
