package com.aistock.prediction.portfolioanalysis;
import com.aistock.common.*;import com.aistock.prediction.modelcatalog.ModelRecords.Actor;import com.fasterxml.jackson.core.JsonParser;import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;import java.util.*;
import org.springframework.http.*;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
import static com.aistock.prediction.portfolioanalysis.PortfolioRecords.*;
@RestController @RequestMapping("/api/v1/portfolio-analyses")
public class PortfolioController {
 private final PortfolioService service;private final ObjectMapper mapper;
 public PortfolioController(PortfolioService service,ObjectMapper mapper){this.service=service;this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);}
 @PostMapping public ResponseEntity<ApiResponse<?>> create(Authentication auth,HttpServletRequest req){
  var actor=actor(auth);query(req,Set.of());UUID request;
  try{var bytes=req.getInputStream().readNBytes(1025);if(bytes.length>1024)throw new BusinessException("PORTFOLIO_REQUEST_TOO_LARGE","持仓分析请求超过大小限制",HttpStatus.PAYLOAD_TOO_LARGE);var body=mapper.readTree(bytes);if(body==null||!body.isObject()||body.size()!=1||!body.path("requestId").isTextual())throw invalid();request=uuid(body.get("requestId").asText());}catch(BusinessException e){throw e;}catch(Exception e){throw invalid();}
  String cookie=null;if(req.getCookies()!=null)for(var c:req.getCookies())if("AISTOCK_SESSION".equals(c.getName()))cookie=c.getValue();
  var result=service.create(actor,new SessionToken(cookie),new Request(request));var op=result.operation();
  if("FAILED".equals(op.state())){String code=op.errorCode()==null?"PORTFOLIO_SOURCE_UNAVAILABLE":op.errorCode();HttpStatus status=switch(code){case "AUTH_REQUIRED"->HttpStatus.UNAUTHORIZED;case "PORTFOLIO_CHANGED","PORTFOLIO_OPERATION_EXPIRED"->HttpStatus.CONFLICT;case "PORTFOLIO_SOURCE_INVALID"->HttpStatus.UNPROCESSABLE_ENTITY;default->HttpStatus.SERVICE_UNAVAILABLE;};throw new BusinessException(code,"本次持仓分析未完成，请查询原请求，已保存结果保留",status);}
  HttpStatus status="RUNNING".equals(op.state())?HttpStatus.ACCEPTED:result.replayed()?HttpStatus.OK:HttpStatus.CREATED;
  return ResponseEntity.status(status).header("Cache-Control","no-store").body(ok(op,req));
 }
 @GetMapping public ApiResponse<?> list(Authentication auth,HttpServletRequest req){var who=actor(auth);query(req,Set.of("before","upper"));return ok(service.list(who,req.getParameter("before"),req.getParameter("upper")),req);}
 @GetMapping("/operations/{id}") public ApiResponse<?> operation(Authentication auth,@PathVariable String id,HttpServletRequest req){var who=actor(auth);query(req,Set.of());return ok(service.operation(who,uuid(id)),req);}
 @GetMapping("/{id}") public ApiResponse<?> get(Authentication auth,@PathVariable String id,HttpServletRequest req){var who=actor(auth);query(req,Set.of());return ok(service.get(who,uuid(id)),req);}
 private UUID uuid(String id){if(id==null||!id.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))throw invalid();return UUID.fromString(id);}
 private Actor actor(Authentication auth){if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof Actor a))throw new BusinessException("AUTH_REQUIRED","请先登录",HttpStatus.UNAUTHORIZED);return a;}
 private void query(HttpServletRequest r,Set<String> names){if(!names.containsAll(r.getParameterMap().keySet())||r.getParameterMap().values().stream().anyMatch(v->v.length!=1))throw invalid();}
 private BusinessException invalid(){return new BusinessException("PORTFOLIO_REQUEST_INVALID","持仓分析请求格式不正确",HttpStatus.BAD_REQUEST);}
 private ApiResponse<?> ok(Object value,HttpServletRequest req){return ApiResponse.success(value,TraceIds.normalize(req.getHeader("X-Trace-Id")));}
}
