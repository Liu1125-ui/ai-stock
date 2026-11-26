package com.aistock.scheduler.adminrefresh;
import com.aistock.common.*;import com.aistock.common.adminrefresh.*;import com.aistock.scheduler.security.AdminTaskPrincipal;import jakarta.servlet.http.*;import java.io.IOException;import java.time.Clock;import org.springframework.http.*;import org.springframework.util.MultiValueMap;import org.springframework.web.bind.annotation.*;
@RestController public class AdminMarketRefreshController {
 private final AdminMarketRefreshService service;private final Clock clock;
 public AdminMarketRefreshController(AdminMarketRefreshService service,Clock clock){this.service=service;this.clock=clock;}
 @RequestMapping({"/api/v1/admin/market-data/{instrumentId}/refresh-options","/api/v1/admin/market-data/{instrumentId}/refresh-operations","/api/v1/admin/market-data/refresh-operations/{operationId}"})
 public ResponseEntity<?> handle(HttpServletRequest r,HttpServletResponse response,@RequestParam MultiValueMap<String,String> query)throws IOException{
  response.setHeader("Cache-Control","no-store");String path=r.getRequestURI();boolean post="POST".equals(r.getMethod());
  if(!(post?AdminMarketRefreshInput.writePath(path):"GET".equals(r.getMethod())&&AdminMarketRefreshInput.readPath(path)))throw new BusinessException("RESOURCE_NOT_FOUND","接口不存在",HttpStatus.NOT_FOUND);
  String trace=TraceIds.normalize(r.getHeader("X-Trace-Id"));var now=clock.instant();String[] parts=path.split("/");
  if(post){if(!query.isEmpty()||r.getContentLengthLong()>4096)throw AdminMarketRefreshInput.invalid();var command=AdminMarketRefreshInput.parse(r.getInputStream().readNBytes(4097));var principal=(AdminTaskPrincipal)r.getAttribute(AdminTaskPrincipal.class.getName());if(principal==null)throw new BusinessException("AUTH_REQUIRED","请先登录",HttpStatus.UNAUTHORIZED);var result=service.submit(principal.userId(),AdminMarketRefreshInput.instrument(parts[5]),command,now);return ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore()).body(new ApiResponse<>(result.code(),result.status()==200?"已登记，请查看任务状态":"当前条件不允许重取",result.receipt(),trace));}
  if(r.getInputStream().read()!=-1)throw AdminMarketRefreshInput.invalid();Object data;
  if(parts[5].equals("refresh-operations")){if(!query.isEmpty())throw AdminMarketRefreshInput.invalid();data=service.receipt(AdminMarketRefreshInput.id(parts[6]),now);}
  else if(path.endsWith("/refresh-options")){if(!query.isEmpty())throw AdminMarketRefreshInput.invalid();data=service.options(AdminMarketRefreshInput.instrument(parts[5]),now);}
  else data=service.history(AdminMarketRefreshInput.instrument(parts[5]),AdminMarketRefreshInput.history(query),now);
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data,trace));
 }
}
