package com.aistock.gateway;
import com.aistock.common.*;import com.fasterxml.jackson.databind.ObjectMapper;import jakarta.servlet.http.HttpServletRequest;import java.util.*;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
@RestController public class ForecastApiProxyController {
 private final ForecastPublicClient client;private final ObjectMapper mapper;public ForecastApiProxyController(ForecastPublicClient client,ObjectMapper mapper){this.client=client;this.mapper=mapper;}
 @RequestMapping({"/api/v1/predictions","/api/v1/predictions/**","/api/v1/training-submissions","/api/v1/training-submissions/**"})public ResponseEntity<byte[]> proxy(HttpServletRequest request,@RequestParam Map<String,String> query){var path=request.getRequestURI();var method=request.getMethod();if(!ProxyHeaderPolicy.allowed(method,path))throw new BusinessException("RESOURCE_NOT_FOUND","接口不存在",HttpStatus.NOT_FOUND);
  boolean training=path.startsWith("/api/v1/training-submissions");
  var safe=new LinkedHashMap<String,String>();
  if(training){
   if(!query.isEmpty()&&(!"GET".equals(method)||!path.equals("/api/v1/training-submissions")
       ||!query.keySet().equals(Set.of("before"))||request.getParameterValues("before").length!=1))
    throw new BusinessException("TRAINING_REQUEST_INVALID","训练请求格式不正确",HttpStatus.BAD_REQUEST);
   if(query.containsKey("before"))safe.put("before",query.get("before"));
  }else if(path.equals("/api/v1/predictions/latest-by-instrument")){
   if(!query.keySet().equals(Set.of("ids"))||request.getParameterValues("ids").length!=1
       ||query.get("ids").length()>179||!query.get("ids").matches("(SH|SZ)[0-9]{6}(,(SH|SZ)[0-9]{6}){0,19}"))
    throw new BusinessException("PREDICTION_REQUEST_INVALID","股票列表格式不正确",HttpStatus.BAD_REQUEST);
   safe.put("ids",query.get("ids"));
  }else if(query.containsKey("page"))safe.put("page",query.get("page"));
  byte[] body=new byte[0];
  if("POST".equals(method)){
   int limit=training?2048:4096;
   try{body=request.getInputStream().readNBytes(limit+1);}
   catch(java.io.IOException e){throw new BusinessException("VALIDATION_FAILED","请求格式不正确",HttpStatus.BAD_REQUEST);}
   if(body.length>limit)throw new BusinessException(training?"TRAINING_REQUEST_INVALID":"PREDICTION_REQUEST_INVALID",
       training?"训练请求超过处理上限":"预测请求超过处理上限",HttpStatus.PAYLOAD_TOO_LARGE);
  }
  try{var headers=ProxyHeaderPolicy.request(request);var relative=path.substring(8);var response=switch(method){case "GET"->client.get(relative,safe,headers);case "POST"->client.post(relative,headers,body);case "OPTIONS"->client.options(relative,headers);default->throw new IllegalStateException();};return ResponseEntity.status(response.getStatusCode()).headers(ProxyHeaderPolicy.response(response.getHeaders())).body(response.getBody());}catch(feign.FeignException e){if(e.status()>=400&&e.status()<500){try{var n=mapper.readTree(e.contentUTF8());if(n.hasNonNull("code")&&n.hasNonNull("message")&&n.path("data").isNull())return ResponseEntity.status(e.status()).contentType(MediaType.APPLICATION_JSON).header("Cache-Control","no-store").body(mapper.writeValueAsBytes(ApiResponse.failure(n.path("code").asText(),n.path("message").asText(),TraceIds.normalize(request.getHeader("X-Trace-Id")))));}catch(java.io.IOException ignored){}if(e.status()==403)throw new BusinessException("ACCESS_DENIED","请求来源或权限不允许",HttpStatus.FORBIDDEN);}throw unavailable();}catch(RuntimeException e){throw unavailable();}}
 private BusinessException unavailable(){return new BusinessException("PREDICTION_SERVICE_UNAVAILABLE","预测服务暂不可用，请通过原请求查询状态",HttpStatus.SERVICE_UNAVAILABLE);}
}
