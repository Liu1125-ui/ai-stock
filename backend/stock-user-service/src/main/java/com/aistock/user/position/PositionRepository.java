package com.aistock.user.position;
import java.time.*;import java.math.*;import java.util.*;
import com.aistock.common.BusinessException;import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Repository;import org.springframework.beans.factory.annotation.Value;import org.springframework.transaction.*;import org.springframework.transaction.support.TransactionTemplate;import org.springframework.http.HttpStatus;
import static com.aistock.user.position.PositionRecords.*;
@Repository public class PositionRepository {
 private final PositionMapper mapper;private final ObjectMapper json;private final TransactionTemplate write,read;private final int maxItems;
 public PositionRepository(PositionMapper mapper,PlatformTransactionManager manager,ObjectMapper json,@Value("${aistock.positions.max-items:100}")int maxItems){if(maxItems<1||maxItems>1000)throw new IllegalArgumentException("Invalid position capacity");this.mapper=mapper;this.json=json;this.maxItems=maxItems;write=new TransactionTemplate(manager);read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);}
 private QueryWrapper<PositionEntity> owned(UUID owner){return new QueryWrapper<PositionEntity>().eq("user_id",owner.toString());}
 public PositionBook list(UUID owner){return read.execute(s->{Long revision=mapper.bookRevision(owner.toString());if(revision==null)return new PositionBook("0",List.of());var items=mapper.selectList(owned(owner).orderByAsc("instrument_id")).stream().map(PositionRepository::view).toList();return new PositionBook(revision.toString(),items);});}
 public StoredReceipt findReceipt(UUID owner,UUID request){var row=mapper.receipt(owner.toString(),request.toString());if(row==null)return null;try{return new StoredReceipt((String)row.get("payload_hash"),json.readValue(row.get("result_json").toString(),PositionReceipt.class));}catch(java.io.IOException e){throw new IllegalStateException("Invalid saved position receipt");}}
 public PositionReceipt operation(UUID owner,UUID request){var saved=findReceipt(owner,request);if(saved==null)throw error("RESOURCE_NOT_FOUND","未找到已保存的持仓操作",404);return saved.receipt();}
 public PositionReceipt replay(StoredReceipt saved,String hash){if(!saved.hash().equals(hash))throw error("POSITION_REQUEST_CONFLICT","请求编号已用于不同操作，请重新核对",409);return saved.receipt();}
 public PositionReceipt apply(UUID owner,PositionMutation input,String hash,Instant now){return write.execute(s->{
  mapper.ensureBook(owner.toString(),now);long revision=mapper.lockBook(owner.toString());
  var saved=findReceipt(owner,input.requestId());if(saved!=null)return replay(saved,hash);
  PositionReceipt result;
  try{result=change(owner,input,revision,now);}catch(BusinessException denied){result=new PositionReceipt(input.requestId(),input.action(),"REJECTED",denied.status().value(),denied.code(),denied.getMessage(),null,Long.toString(revision),null);}
  try{mapper.saveReceipt(owner.toString(),input.requestId().toString(),input.action(),hash,result.state(),result.httpStatus(),result.code(),result.message(),result.positionId(),Long.parseLong(result.bookRevision()),json.writeValueAsString(result),now);}catch(java.io.IOException e){throw new IllegalStateException("Position receipt serialization failed");}
  return result;
 });}
 private PositionReceipt change(UUID owner,PositionMutation m,long revision,Instant now){
  if(revision==Long.MAX_VALUE)throw error("POSITION_VERSION_EXHAUSTED","持仓版本已达上限，请联系管理员",409);
  PositionEntity item;String state;
  if(m.action().equals("CREATE")){
   if(mapper.selectCount(owned(owner).eq("instrument_id",m.instrumentId()))>0)throw error("POSITION_ALREADY_EXISTS","该股票已在持仓中，请修改现有记录",409);
   if(mapper.selectCount(owned(owner))>=maxItems)throw error("POSITION_LIMIT_REACHED","持仓条数已达上限",422);
   item=new PositionEntity();item.setPositionId(UUID.randomUUID().toString());item.setUserId(owner.toString());item.setInstrumentId(m.instrumentId());item.setQuantity(Long.parseLong(m.quantity()));item.setAverageCost(new BigDecimal(m.averageCost()).setScale(6));item.setRevision(1L);item.setCreatedAt(now);item.setUpdatedAt(now);mapper.insert(item);state="CREATED";
  }else{
   item=mapper.selectOne(owned(owner).eq("position_id",m.positionId().toString()));if(item==null)throw error("RESOURCE_NOT_FOUND","持仓记录不存在",404);
   if(!item.getRevision().equals(m.expectedRevision()))throw error("POSITION_REVISION_CONFLICT","持仓已被修改，请重新读取后核对",409);
   if(m.action().equals("DELETE")){if(mapper.delete(owned(owner).eq("position_id",item.getPositionId()).eq("revision",m.expectedRevision()))!=1)throw error("POSITION_REVISION_CONFLICT","持仓已改变，请重新读取",409);state="DELETED";}
   else{
    long quantity=Long.parseLong(m.quantity());var cost=new BigDecimal(m.averageCost()).setScale(6);
    if(item.getQuantity()==quantity&&item.getAverageCost().compareTo(cost)==0)return receipt(m,"UNCHANGED",revision,item);
    if(item.getRevision()==Long.MAX_VALUE)throw error("POSITION_VERSION_EXHAUSTED","持仓版本已达上限",409);
    var update=new UpdateWrapper<PositionEntity>().eq("user_id",owner.toString()).eq("position_id",item.getPositionId()).eq("revision",m.expectedRevision()).set("quantity",quantity).set("average_cost",cost).set("revision",item.getRevision()+1).set("updated_at",now);
    if(mapper.update(null,update)!=1)throw error("POSITION_REVISION_CONFLICT","持仓已改变，请重新读取",409);
    item.setQuantity(quantity);item.setAverageCost(cost);item.setRevision(item.getRevision()+1);item.setUpdatedAt(now);state="UPDATED";
   }
  }
  mapper.advanceBook(owner.toString(),now);return receipt(m,state,revision+1,item);
 }
 private PositionReceipt receipt(PositionMutation m,String state,long revision,PositionEntity item){return new PositionReceipt(m.requestId(),m.action(),state,state.equals("CREATED")?201:200,"OK","操作已保存",item.getPositionId(),Long.toString(revision),state.equals("DELETED")?null:view(item));}
 static PositionView view(PositionEntity e){return new PositionView(e.getPositionId(),e.getInstrumentId(),e.getQuantity().toString(),e.getAverageCost().toPlainString(),e.getRevision().toString(),e.getCreatedAt(),e.getUpdatedAt());}
 static BusinessException error(String code,String message,int status){return new BusinessException(code,message,HttpStatus.valueOf(status));}
}
