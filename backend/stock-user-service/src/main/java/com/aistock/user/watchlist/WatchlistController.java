package com.aistock.user.watchlist;
import com.aistock.common.*;import com.aistock.user.session.Identity;import java.util.Map;import org.springframework.security.core.Authentication;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/users/me/watchlist")public class WatchlistController {
 private final WatchlistService service;public WatchlistController(WatchlistService service){this.service=service;}
 @GetMapping public ApiResponse<WatchlistPage> list(Authentication auth,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestHeader(value="X-Trace-Id",required=false)String trace){return ApiResponse.success(service.list(identity(auth),page,size),TraceIds.normalize(trace));}
 @PostMapping public ApiResponse<WatchlistItem> add(Authentication auth,@RequestBody Map<String,Object> body,@RequestHeader(value="X-Trace-Id",required=false)String trace){if(body==null||body.size()!=1||!(body.get("instrumentId") instanceof String id))throw new BusinessException("VALIDATION_FAILED","只接受证券代码",HttpStatus.BAD_REQUEST);return ApiResponse.success(service.add(identity(auth),id),TraceIds.normalize(trace));}
 @DeleteMapping("/{id}")public ApiResponse<Void> delete(Authentication auth,@PathVariable String id,@RequestHeader(value="X-Trace-Id",required=false)String trace){service.delete(identity(auth),id);return ApiResponse.success(null,TraceIds.normalize(trace));}
 private Identity identity(Authentication a){if(a==null||!(a.getPrincipal()instanceof Identity i))throw new BusinessException("AUTH_REQUIRED","请先登录",HttpStatus.UNAUTHORIZED);return i;}
}
