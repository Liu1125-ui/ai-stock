package com.aistock.user.position;

import com.aistock.common.*;
import com.aistock.user.session.Identity;
import com.aistock.user.session.SessionService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static com.aistock.user.position.PositionSnapshotRecords.*;

/** Private service authentication is checked before consuming any request bytes. */
@RestController
@RequestMapping("/internal/v1/positions")
public class PositionSnapshotController {
 private static final String SOURCE="stock-user-service";
 private final PositionService positions;
 private final SessionService sessions;
 private final ObjectMapper json;
 private final String serviceToken;
 public PositionSnapshotController(PositionService positions,SessionService sessions,ObjectMapper mapper,
   @Value("${aistock.internal.token:}") String serviceToken){
  this.positions=positions;this.sessions=sessions;this.serviceToken=serviceToken;
  this.json=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
   .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 }
 @PostMapping("/snapshot")
 public ApiResponse<PositionSnapshot> snapshot(HttpServletRequest request){
  Identity owner=authorize(request);
  var book=positions.list(owner);
  var items=book.items().stream().map(p->new PositionItem(UUID.fromString(p.positionId()),p.instrumentId(),p.quantity(),p.averageCost(),p.revision())).toList();
  return ApiResponse.success(new PositionSnapshot(owner.userId(),SOURCE,book.bookRevision(),items),trace(request));
 }
 @PostMapping("/revision")
 public ApiResponse<PositionRevision> revision(HttpServletRequest request){
  Identity owner=authorize(request);
  return ApiResponse.success(new PositionRevision(owner.userId(),SOURCE,positions.list(owner).bookRevision()),trace(request));
 }
 private Identity authorize(HttpServletRequest request){
  if(serviceToken==null||serviceToken.isBlank())throw error("INTERNAL_AUTH_NOT_CONFIGURED","内部服务认证未配置",503);
  String supplied=request.getHeader("X-Service-Token");
  if(supplied==null||!MessageDigest.isEqual(serviceToken.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))throw error("INTERNAL_AUTH_REQUIRED","缺少有效服务身份",401);
  if(!request.getParameterMap().isEmpty())throw malformed();
  String sessionToken;
  try {
   byte[] bytes=request.getInputStream().readNBytes(1025);
   if(bytes.length>1024)throw error("POSITION_REQUEST_TOO_LARGE","持仓快照请求超过大小限制",413);
   JsonNode body=json.readTree(bytes);
   if(body==null||!body.isObject())throw malformed();
   if(body.isEmpty())throw error("AUTH_REQUIRED","请重新登录",401);
   if(body.size()!=1||!body.has("token")||!body.get("token").isTextual())throw malformed();
   sessionToken=body.get("token").asText();
  }catch(BusinessException e){throw e;}catch(Exception e){throw malformed();}
  try {return sessions.resolve(sessionToken);}
  catch(BusinessException e){throw e;}
  catch(org.springframework.dao.DataAccessException e){throw error("POSITION_SOURCE_UNAVAILABLE","持仓身份核验暂不可用，请稍后重试",503);}
 }
 private BusinessException malformed(){return error("VALIDATION_FAILED","持仓快照请求格式不正确",400);}
 private BusinessException error(String code,String message,int status){return new BusinessException(code,message,HttpStatus.valueOf(status));}
 private String trace(HttpServletRequest r){return TraceIds.normalize(r.getHeader("X-Trace-Id"));}
}
