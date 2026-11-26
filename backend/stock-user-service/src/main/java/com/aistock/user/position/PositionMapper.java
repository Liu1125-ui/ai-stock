package com.aistock.user.position;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.time.Instant;import java.util.Map;
@Mapper public interface PositionMapper extends BaseMapper<PositionEntity> {
 @Insert("insert into position_book(user_id,revision,updated_at) values(#{owner},0,#{now}) on duplicate key update user_id=user_id") int ensureBook(@Param("owner")String owner,@Param("now")Instant now);
 @Select("select revision from position_book where user_id=#{owner} for update") Long lockBook(String owner);
 @Select("select revision from position_book where user_id=#{owner}") Long bookRevision(String owner);
 @Update("update position_book set revision=revision+1,updated_at=#{now} where user_id=#{owner}") int advanceBook(@Param("owner")String owner,@Param("now")Instant now);
 @Select("select payload_hash,result_json from position_operation_receipt where user_id=#{owner} and request_id=#{request}") Map<String,Object> receipt(@Param("owner")String owner,@Param("request")String request);
 @Insert("insert into position_operation_receipt(user_id,request_id,action,payload_hash,state,http_status,error_code,message,position_id,book_revision,result_json,created_at) values(#{owner},#{request},#{action},#{hash},#{state},#{status},#{code},#{message},#{position},#{revision},#{json},#{now})")
 int saveReceipt(@Param("owner")String owner,@Param("request")String request,@Param("action")String action,@Param("hash")String hash,@Param("state")String state,@Param("status")int status,@Param("code")String code,@Param("message")String message,@Param("position")String position,@Param("revision")long revision,@Param("json")String json,@Param("now")Instant now);
}
