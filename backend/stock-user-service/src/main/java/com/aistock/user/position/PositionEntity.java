package com.aistock.user.position;
import com.baomidou.mybatisplus.annotation.*;
@TableName("position")
public class PositionEntity {
 @TableId(type=IdType.INPUT)
 private String positionId;
 private String userId;
 private String instrumentId;
 private Long quantity;
 private java.math.BigDecimal averageCost;
 private Long revision;
 private java.time.Instant createdAt;
 private java.time.Instant updatedAt;
 public String getPositionId(){return positionId;}
 public void setPositionId(String value){positionId=value;}
 public String getUserId(){return userId;}
 public void setUserId(String value){userId=value;}
 public String getInstrumentId(){return instrumentId;}
 public void setInstrumentId(String value){instrumentId=value;}
 public Long getQuantity(){return quantity;}
 public void setQuantity(Long value){quantity=value;}
 public java.math.BigDecimal getAverageCost(){return averageCost;}
 public void setAverageCost(java.math.BigDecimal value){averageCost=value;}
 public Long getRevision(){return revision;}
 public void setRevision(Long value){revision=value;}
 public java.time.Instant getCreatedAt(){return createdAt;}
 public void setCreatedAt(java.time.Instant value){createdAt=value;}
 public java.time.Instant getUpdatedAt(){return updatedAt;}
 public void setUpdatedAt(java.time.Instant value){updatedAt=value;}
}
