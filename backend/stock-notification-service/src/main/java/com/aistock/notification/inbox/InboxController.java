package com.aistock.notification.inbox;
import com.aistock.common.*;import com.aistock.notification.alert.NotificationSecurityFilter;
import com.fasterxml.jackson.databind.JsonNode;import jakarta.servlet.http.HttpServletRequest;
import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/messages") public class InboxController {
 private final InboxService service;public InboxController(InboxService service){this.service=service;}
 @GetMapping public ApiResponse<InboxRecords.Page> list(@RequestAttribute(NotificationSecurityFilter.OWNER)UUID owner,HttpServletRequest r){query(r,Set.of("page","size","unreadOnly"));String unread=r.getParameter("unreadOnly");if(unread!=null&&!Set.of("true","false").contains(unread))throw InboxService.invalid();return ok(service.list(owner,page(r,"page",1),page(r,"size",20),"true".equals(unread)),r);}
 @GetMapping("/unread-count") public ApiResponse<InboxRecords.Unread> count(@RequestAttribute(NotificationSecurityFilter.OWNER)UUID owner,HttpServletRequest r){query(r,Set.of());return ok(service.unreadCount(owner),r);}
 @PatchMapping("/{id}/read") public ApiResponse<InboxRecords.Message> read(@RequestAttribute(NotificationSecurityFilter.OWNER)UUID owner,@PathVariable String id,@RequestBody JsonNode body,HttpServletRequest r){query(r,Set.of());if(!body.isObject()||!body.isEmpty())throw InboxService.invalid();return ok(service.read(owner,uuid(id)),r);}
 @PostMapping("/read") public ApiResponse<InboxRecords.ReadResult> batch(@RequestAttribute(NotificationSecurityFilter.OWNER)UUID owner,@RequestBody JsonNode body,HttpServletRequest r){query(r,Set.of());if(!body.isObject()||body.size()!=1||!body.path("ids").isArray()||body.path("ids").isEmpty()||body.path("ids").size()>50)throw InboxService.invalid();var ids=new ArrayList<UUID>();for(var node:body.get("ids")){if(!node.isTextual())throw InboxService.invalid();ids.add(uuid(node.textValue()));}if(new HashSet<>(ids).size()!=ids.size())throw InboxService.invalid();return ok(service.readBatch(owner,ids),r);}
 static UUID uuid(String s){if(s==null||!s.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw InboxService.invalid();return UUID.fromString(s);}
 static void query(HttpServletRequest r,Set<String> allowed){for(var e:r.getParameterMap().entrySet())if(!allowed.contains(e.getKey())||e.getValue().length!=1)throw InboxService.invalid();}
 static int page(HttpServletRequest r,String key,int fallback){String value=r.getParameter(key);if(value==null)return fallback;if(!value.matches("[1-9][0-9]{0,5}"))throw InboxService.invalid();int n=Integer.parseInt(value);InboxService.validatePage(key.equals("page")?n:1,key.equals("size")?n:1);return n;}
 static <T>ApiResponse<T> ok(T result,HttpServletRequest r){return ApiResponse.success(result,TraceIds.normalize(r.getHeader("X-Trace-Id")));}
}
