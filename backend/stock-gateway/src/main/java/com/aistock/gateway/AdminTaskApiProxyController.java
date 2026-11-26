package com.aistock.gateway;
import com.aistock.common.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.*;
import java.io.IOException;import java.time.Instant;import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;import org.springframework.util.MultiValueMap;import org.springframework.web.bind.annotation.*;

@RestController
public class AdminTaskApiProxyController {
 private final SchedulerPublicClient client;private final ObjectMapper mapper;private final Set<String> origins;
 private static final Map<String,String> SAFE=new LinkedHashMap<>();
 static {
  SAFE.put("TASK_QUERY_INVALID","任务查询参数不正确，请检查筛选条件");SAFE.put("AUTH_REQUIRED","请先登录");SAFE.put("ACCESS_DENIED","仅管理员可以管理任务");SAFE.put("TASK_NOT_FOUND","任务不存在");SAFE.put("USER_SERVICE_UNAVAILABLE","账号服务暂不可用，请稍后重试");
  SAFE.put("TASK_ACTION_INVALID","任务操作参数不正确");SAFE.put("TASK_ACTION_ORIGIN_DENIED","当前来源不允许提交任务操作");SAFE.put("TASK_ACTIONS_DISABLED","管理员任务操作入口尚未开启");SAFE.put("TASK_WORKER_DISABLED","对应任务执行器未开启");SAFE.put("TASK_RESULT_UNCONFIRMED","任务原结果尚未核对");SAFE.put("TASK_ACTION_NOT_ALLOWED","当前任务不符合操作条件");SAFE.put("TASK_RETRY_COOLDOWN","请稍后再尝试人工重试");SAFE.put("TASK_REVISION_CONFLICT","任务状态已变化，请刷新后重新确认");SAFE.put("COMPARISON_CONTEXT_INVALID","比较任务上下文无法核验");SAFE.put("COMPARISON_CYCLE_EXPIRED","比较任务已超出原周期");SAFE.put("COMPARISON_BUDGET_EXHAUSTED","比较任务原有尝试次数已用完");SAFE.put("OPERATION_KEY_CONFLICT","操作编号与原请求不一致");SAFE.put("OPERATION_NOT_FOUND","暂未查询到此操作记录");SAFE.put("TASK_ACTION_UNAVAILABLE","操作结果暂无法确认，请查询原操作编号");SAFE.put("TASK_RETRY_APPLIED","已重新排队，实际结果请查看任务状态");SAFE.put("TASK_CANCEL_APPLIED","已取消当前任务，后续自动计划不受影响");
 }
 public AdminTaskApiProxyController(SchedulerPublicClient client,ObjectMapper mapper){this(client,mapper,"http://127.0.0.1:15173,http://localhost:15173");}
 @Autowired public AdminTaskApiProxyController(SchedulerPublicClient client,ObjectMapper mapper,@Value("${aistock.auth.allowed-origins:http://127.0.0.1:15173,http://localhost:15173}")String origins){this.client=client;this.mapper=mapper;this.origins=Set.copyOf(Arrays.stream(origins.split(",")).map(String::strip).filter(v->!v.isEmpty()).toList());}
 @RequestMapping({"/api/v1/admin/tasks","/api/v1/admin/tasks/**"})
 public ResponseEntity<byte[]> proxy(HttpServletRequest r,HttpServletResponse s,@RequestParam MultiValueMap<String,String> query)throws IOException {
  s.setHeader("Cache-Control","no-store");String path=r.getRequestURI(),method=r.getMethod();
  if(!ProxyHeaderPolicy.allowed(method,path))throw new BusinessException("RESOURCE_NOT_FOUND","接口不存在",HttpStatus.NOT_FOUND);
  byte[] body=null;boolean post="POST".equals(method);
  if(post) {
   if(!origins.contains(Objects.toString(single(r,"Origin"),""))||!"1".equals(single(r,"X-Aistock-Admin-Action")))throw invalidOrigin();
   if(Collections.list(r.getHeaders("Cookie")).size()>1)throw invalidOrigin();
   try{var type=MediaType.parseMediaType(Objects.requireNonNull(single(r,"Content-Type")));if(!"application".equals(type.getType())||!"json".equals(type.getSubtype()))throw new IllegalArgumentException();}catch(RuntimeException e){throw invalid();}
   if(!query.isEmpty()||r.getContentLengthLong()>4096)throw invalid();body=r.getInputStream().readNBytes(4097);if(body.length>4096)throw invalid();
  }
  var headers=ProxyHeaderPolicy.request(r);
  try {
   var reply=post?client.post(path.substring(8),body,headers):"GET".equals(method)?client.get(path.substring(8),query,headers):client.options(path.substring(8),headers);
   if(reply.getBody()!=null&&reply.getBody().length>262144)throw unavailable();
   if(post){var n=mapper.readTree(reply.getBody());if(reply.getStatusCode().value()!=200||!"OK".equals(n.path("code").asText()))throw unavailable();return receiptReply(r,200,n.path("data"),body,"OK");}
   return ResponseEntity.status(reply.getStatusCode()).headers(ProxyHeaderPolicy.response(reply.getHeaders())).body(reply.getBody());
  }catch(feign.FeignException e) {
   try {
    if(e.content().length>262144)throw unavailable();var n=mapper.readTree(e.contentUTF8());var code=n.path("code").asText();
    if(SAFE.containsKey(code)&&e.status()==expectedStatus(code)) {
     if(post&&e.status()==409&&!n.path("data").isNull())return receiptReply(r,409,n.path("data"),body,code);
     if(n.path("data").isNull())return failure(r,e.status(),code,SAFE.get(code));
    }
   }catch(IOException ignored){}
   if(e.status()==403)return failure(r,403,"ACCESS_DENIED",SAFE.get("ACCESS_DENIED"));throw unavailable();
  }catch(RuntimeException|IOException e){throw unavailable();}
 }
 private ResponseEntity<byte[]> receiptReply(HttpServletRequest r,int status,JsonNode data,byte[] input,String code)throws IOException {
  var request=mapper.readTree(input);var segments=r.getRequestURI().split("/");String task=segments[segments.length-2];String action=r.getRequestURI().endsWith("/retry")?"RETRY":"CANCEL";
  if(!data.isObject()||!task.equals(data.path("taskId").asText())||!request.path("operationId").asText().equals(data.path("operationId").asText())||!action.equals(data.path("action").asText())||!(status==200?"APPLIED":"REJECTED").equals(data.path("outcome").asText()))throw unavailable();
  String result=data.path("resultCode").asText();if(!SAFE.containsKey(result)||status==409&&!code.equals(result)||status==200&&!(action.equals("RETRY")?"TASK_RETRY_APPLIED":"TASK_CANCEL_APPLIED").equals(result))throw unavailable();
  var safe=mapper.createObjectNode();
  for(String field:List.of("operationId","taskId","action","outcome","resultCode","beforeState","afterState","beforeRevision","afterRevision","requestedAt","finishedAt")){
   var value=data.get(field);if(value==null||!value.isTextual())throw unavailable();safe.set(field,value);
  }
  for(String field:List.of("beforeRevision","afterRevision")){var v=safe.path(field).asText();if(!v.matches("0|[1-9][0-9]{0,18}"))throw unavailable();try{Long.parseLong(v);}catch(NumberFormatException e){throw unavailable();}}
  for(String field:List.of("requestedAt","finishedAt"))try{Instant.parse(safe.path(field).asText());}catch(RuntimeException e){throw unavailable();}
  safe.put("message",SAFE.get(result));return json(r,status,code,SAFE.get(result),safe);
 }
 private int expectedStatus(String code){return switch(code){case "AUTH_REQUIRED"->401;case "ACCESS_DENIED","TASK_ACTION_ORIGIN_DENIED"->403;case "TASK_QUERY_INVALID","TASK_ACTION_INVALID"->400;case "TASK_NOT_FOUND","OPERATION_NOT_FOUND"->404;case "USER_SERVICE_UNAVAILABLE","TASK_ACTION_UNAVAILABLE"->503;default->409;};}
 private String single(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));return values.size()==1?values.get(0):null;}
 private ResponseEntity<byte[]> json(HttpServletRequest r,int status,String code,String message,JsonNode data)throws IOException{return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).header("Cache-Control","no-store").body(mapper.writeValueAsBytes(new ApiResponse<>(code,message,data,TraceIds.normalize(r.getHeader("X-Trace-Id")))));}
 private ResponseEntity<byte[]> failure(HttpServletRequest r,int status,String code,String message){try{return json(r,status,code,message,mapper.nullNode());}catch(IOException e){throw unavailable();}}
 private BusinessException invalid(){return new BusinessException("TASK_ACTION_INVALID","任务操作参数不正确",HttpStatus.BAD_REQUEST);}
 private BusinessException invalidOrigin(){return new BusinessException("TASK_ACTION_ORIGIN_DENIED","当前来源不允许提交任务操作",HttpStatus.FORBIDDEN);}
 private BusinessException unavailable(){return new BusinessException("SCHEDULER_SERVICE_UNAVAILABLE","任务服务暂不可用，请稍后重试",HttpStatus.SERVICE_UNAVAILABLE);}
}
