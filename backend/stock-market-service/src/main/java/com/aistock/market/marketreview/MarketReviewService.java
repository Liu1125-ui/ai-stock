package com.aistock.market.marketreview;

import com.aistock.common.BusinessException;
import com.aistock.common.marketreview.MarketReviewContract;
import com.aistock.common.marketreview.MarketReviewContract.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service public class MarketReviewService {
 private final JdbcTemplate jdbc;private final Clock clock;private final TransactionTemplate write,read;
 public MarketReviewService(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;var manager=new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource()));write=new TransactionTemplate(manager);write.setTimeout(5);read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);}
 public Page read(String stock,String version){MarketReviewContract.identity(stock,version);return safe(()->read.execute(tx->{owns(stock,version,false);var current=jdbc.queryForList("select revision from market_version_review where version_id=?",Long.class,version);long rev=current.isEmpty()?0:current.get(0);var all=jdbc.query("select * from market_version_review_operation where version_id=? order by revision desc limit 21",(rs,n)->receipt(stock,rs),version);var history=List.copyOf(all.subList(0,Math.min(20,all.size())));return MarketReviewContract.page(new Page(stock,version,""+rev,history.isEmpty()?null:history.get(0),history,all.size()>20,clock.instant()),stock,version);}));}
 public Receipt operation(String stock,String version,String actor,String op){MarketReviewContract.identity(stock,version);MarketReviewContract.id(actor);MarketReviewContract.id(op);return safe(()->read.execute(tx->{owns(stock,version,false);var rows=jdbc.query("select * from market_version_review_operation where operation_id=? and version_id=? and actor_user_id=?",(rs,n)->receipt(stock,rs),op,version,actor);if(rows.size()!=1)throw error("REVIEW_OPERATION_NOT_FOUND","目前未查到本次保存结果，请保留编号稍后核对",HttpStatus.NOT_FOUND);return rows.get(0);}));}
 public Receipt save(String stock,String version,String actor,Command command){MarketReviewContract.identity(stock,version);MarketReviewContract.id(actor);MarketReviewContract.command(command);return safe(()->write.execute(tx->{
  owns(stock,version,true);
  var prior=jdbc.queryForList("select version_id,actor_user_id,expected_revision,review_state,note from market_version_review_operation where operation_id=?",command.operationId());
  if(!prior.isEmpty()){var old=prior.get(0);if(!version.equals(old.get("version_id"))||!actor.equals(old.get("actor_user_id"))||MarketReviewContract.revision(command.expectedRevision())!=((Number)old.get("expected_revision")).longValue()||!command.reviewState().equals(old.get("review_state"))||!command.note().equals(old.get("note")))throw conflict();return operation(stock,version,actor,command.operationId());}
  jdbc.update("insert into market_version_review(version_id,revision) values(?,0) on duplicate key update version_id=version_id",version);
  long rev=Objects.requireNonNull(jdbc.queryForObject("select revision from market_version_review where version_id=? for update",Long.class,version));
  if(rev!=MarketReviewContract.revision(command.expectedRevision()))throw error("REVIEW_REVISION_CONFLICT","记录已被其他操作更新，请重新加载后核对",HttpStatus.CONFLICT);
  if(rev>=Long.MAX_VALUE-1)throw MarketReviewContract.invalid();
  var at=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  jdbc.update("insert into market_version_review_operation(operation_id,version_id,actor_user_id,expected_revision,revision,review_state,note,created_at) values(?,?,?,?,?,?,?,?)",command.operationId(),version,actor,rev,rev+1,command.reviewState(),command.note(),Timestamp.from(at));
  jdbc.update("update market_version_review set revision=? where version_id=?",rev+1,version);
  return new Receipt(command.operationId(),stock,version,""+(rev+1),command.reviewState(),command.note(),at);
 }));}
 private void owns(String stock,String version,boolean lock){var found=jdbc.queryForList("select v.version_id from market_data_version v join instrument i on i.instrument_id=v.instrument_id where v.version_id=? and v.instrument_id=? and i.demo_only=false"+(lock?" for update":""),String.class,version,stock);if(found.size()!=1)throw error("MARKET_ADMIN_RESOURCE_NOT_FOUND","未找到所属股票的行情版本",HttpStatus.NOT_FOUND);}
 private Receipt receipt(String stock,java.sql.ResultSet rs)throws java.sql.SQLException{return new Receipt(rs.getString("operation_id"),stock,rs.getString("version_id"),rs.getString("revision"),rs.getString("review_state"),rs.getString("note"),rs.getTimestamp("created_at").toInstant());}
 private <T>T safe(java.util.function.Supplier<T> work){try{return work.get();}catch(DuplicateKeyException e){throw conflict();}catch(DataAccessException|TransactionException e){throw error("MARKET_ADMIN_UNAVAILABLE","审核记录暂不可用，请核对原操作编号",HttpStatus.SERVICE_UNAVAILABLE);}}
 private BusinessException conflict(){return error("REVIEW_OPERATION_CONFLICT","操作编号已被使用，请先核对原保存结果",HttpStatus.CONFLICT);}
 private BusinessException error(String code,String message,HttpStatus status){return new BusinessException(code,message,status);}
}
