package com.aistock.notification.alertmail;
import com.aistock.common.BusinessException;import java.time.Instant;import java.util.*;import org.springframework.http.HttpStatus;
public final class AlertMailViewService {
 public record DeliveryView(UUID deliveryId,String state,String reasonCode,int attempts,Instant updatedAt,Instant sentAt){}
 private final AlertMailRepository repo;public AlertMailViewService(AlertMailRepository repo){this.repo=repo;}
 public DeliveryView get(UUID owner,UUID message){
  var rows=repo.jdbc.queryForList("select d.delivery_id,d.state,d.reason_code,d.attempts,d.updated_at,d.sent_at from inbox_message m left join alert_mail_delivery d on d.trigger_id=m.trigger_id and d.owner_id=m.owner_id where m.message_id=? and m.owner_id=?",message.toString(),owner.toString());
  if(rows.isEmpty())throw new BusinessException("RESOURCE_NOT_FOUND","消息不存在",HttpStatus.NOT_FOUND);var row=rows.get(0);if(row.get("delivery_id")==null)return new DeliveryView(null,"NOT_RECORDED",null,0,null,null);
  return new DeliveryView(UUID.fromString((String)row.get("delivery_id")),(String)row.get("state"),(String)row.get("reason_code"),((Number)row.get("attempts")).intValue(),((java.sql.Timestamp)row.get("updated_at")).toInstant(),row.get("sent_at")==null?null:((java.sql.Timestamp)row.get("sent_at")).toInstant());
 }
}
