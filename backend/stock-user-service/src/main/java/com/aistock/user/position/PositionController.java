package com.aistock.user.position;
import java.util.*;import jakarta.servlet.http.HttpServletRequest;
import com.aistock.common.*;import com.aistock.user.session.Identity;
import com.fasterxml.jackson.core.JsonParser;import com.fasterxml.jackson.databind.*;
import org.springframework.http.*;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
import static com.aistock.user.position.PositionRecords.*;
@RestController @RequestMapping("/api/v1/users/me/positions")
public class PositionController {
 private final PositionService service;private final ObjectMapper json;
 public PositionController(PositionService service,ObjectMapper mapper){this.service=service;this.json=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);}
 @GetMapping public ApiResponse<PositionBook> list(Authentication auth,HttpServletRequest req){var who=owner(auth);query(req);return ApiResponse.success(service.list(who),trace(req));}
 @GetMapping("/operations/{requestId}") public ApiResponse<PositionReceipt> operation(Authentication auth,@PathVariable String requestId,HttpServletRequest req){var who=owner(auth);query(req);return ApiResponse.success(service.operation(who,uuid(requestId)),trace(req));}
 @PostMapping public ResponseEntity<?> create(Authentication auth,HttpServletRequest req){var who=owner(auth);query(req);var n=body(req,Set.of("requestId","instrumentId","quantity","averageCost"));return result(service.apply(who,new PositionMutation(uuid(text(n,"requestId")),"CREATE",null,text(n,"instrumentId"),text(n,"quantity"),text(n,"averageCost"),null)),req);}
 @PutMapping("/{positionId}") public ResponseEntity<?> update(Authentication auth,@PathVariable String positionId,HttpServletRequest req){var who=owner(auth);query(req);var n=body(req,Set.of("requestId","expectedRevision","quantity","averageCost"));return result(service.apply(who,new PositionMutation(uuid(text(n,"requestId")),"UPDATE",uuid(positionId),null,text(n,"quantity"),text(n,"averageCost"),revision(text(n,"expectedRevision")))),req);}
 @DeleteMapping("/{positionId}") public ResponseEntity<?> delete(Authentication auth,@PathVariable String positionId,HttpServletRequest req){var who=owner(auth);query(req);var n=body(req,Set.of("requestId","expectedRevision"));return result(service.apply(who,new PositionMutation(uuid(text(n,"requestId")),"DELETE",uuid(positionId),null,null,null,revision(text(n,"expectedRevision")))),req);}
 private ResponseEntity<?> result(PositionReceipt receipt,HttpServletRequest req){return ResponseEntity.status(receipt.httpStatus()).header("Cache-Control","no-store").body(receipt.state().equals("REJECTED")?ApiResponse.failure(receipt.code(),receipt.message(),trace(req)):ApiResponse.success(receipt,trace(req)));}
 private JsonNode body(HttpServletRequest req,Set<String> fields){try{var bytes=req.getInputStream().readNBytes(4097);if(bytes.length>4096)throw PositionRepository.error("POSITION_REQUEST_TOO_LARGE","持仓请求超过大小限制",413);var n=json.readTree(bytes);if(n==null||!n.isObject())throw malformed();var names=new HashSet<String>();n.fieldNames().forEachRemaining(names::add);if(!names.equals(fields))throw malformed();for(var field:fields)text(n,field);return n;}catch(BusinessException e){throw e;}catch(Exception e){throw malformed();}}
 private String text(JsonNode n,String key){if(!n.path(key).isTextual())throw malformed();return n.get(key).asText();}
 private UUID uuid(String text){if(text==null||!text.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))throw malformed();return UUID.fromString(text);}
 private long revision(String text){try{if(!text.matches("[1-9][0-9]{0,18}"))throw malformed();return Long.parseLong(text);}catch(NumberFormatException e){throw malformed();}}
 private void query(HttpServletRequest req){if(!req.getParameterMap().isEmpty())throw malformed();}
 private Identity owner(Authentication auth){if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof Identity i))throw PositionRepository.error("AUTH_REQUIRED","请先登录",401);return i;}
 private String trace(HttpServletRequest req){return TraceIds.normalize(req.getHeader("X-Trace-Id"));}
 private BusinessException malformed(){return PositionRepository.error("VALIDATION_FAILED","持仓请求格式不正确",400);}
}
