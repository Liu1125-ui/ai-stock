package com.aistock.gateway;
import com.aistock.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class ModelApiProxyController {
    private final PredictionPublicClient client;private final ObjectMapper mapper;
    public ModelApiProxyController(PredictionPublicClient client,ObjectMapper mapper){this.client=client;this.mapper=mapper;}
    @RequestMapping({"/api/v1/models","/api/v1/admin/models","/api/v1/admin/models/**"})
    public ResponseEntity<byte[]> proxy(HttpServletRequest request){
        var path=request.getRequestURI();var method=request.getMethod();if(!ProxyHeaderPolicy.allowed(method,path))throw new BusinessException("RESOURCE_NOT_FOUND","接口不存在",HttpStatus.NOT_FOUND);
        var headers=ProxyHeaderPolicy.request(request);var relative=path.substring(8);
        byte[] body=new byte[0];
        if("POST".equals(method)){
            try{body=request.getInputStream().readNBytes(65537);}catch(java.io.IOException e){throw new BusinessException("VALIDATION_FAILED","请求格式不正确",HttpStatus.BAD_REQUEST);}
            if(body.length>65536)throw new BusinessException("MODEL_PAYLOAD_TOO_LARGE","模型请求超过当前处理上限",HttpStatus.PAYLOAD_TOO_LARGE);
        }
        try{
            var response=switch(method){case "GET"->client.get(relative,headers);case "POST"->client.post(relative,headers,body==null?new byte[0]:body);case "OPTIONS"->client.options(relative,headers);default->throw new IllegalStateException("Unsupported model method");};
            return ResponseEntity.status(response.getStatusCode()).headers(ProxyHeaderPolicy.response(response.getHeaders())).body(response.getBody());
        }catch(feign.FeignException e){
            if(e.status()>=400&&e.status()<500){try{var n=mapper.readTree(e.contentUTF8());if(n.hasNonNull("code")&&n.hasNonNull("message")&&n.path("data").isNull())return ResponseEntity.status(e.status()).contentType(MediaType.APPLICATION_JSON).header("Cache-Control","no-store").body(mapper.writeValueAsBytes(ApiResponse.failure(n.path("code").asText(),n.path("message").asText(),TraceIds.normalize(request.getHeader("X-Trace-Id")))));}catch(java.io.IOException ignored){}
                if(e.status()==403)return forbidden(request);}
            throw unavailable();
        }catch(RuntimeException e){throw unavailable();}
    }
    private ResponseEntity<byte[]> forbidden(HttpServletRequest request){
        try{return ResponseEntity.status(403).contentType(MediaType.APPLICATION_JSON).header("Cache-Control","no-store").body(mapper.writeValueAsBytes(ApiResponse.failure("ACCESS_DENIED","请求来源或访问权限不允许",TraceIds.normalize(request.getHeader("X-Trace-Id")))));}
        catch(java.io.IOException e){throw unavailable();}
    }
    private BusinessException unavailable(){return new BusinessException("PREDICTION_SERVICE_UNAVAILABLE","模型服务暂不可用，请稍后重试",HttpStatus.SERVICE_UNAVAILABLE);}
}
