package com.aistock.market.comparison;
import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;import java.time.temporal.ChronoUnit;import java.sql.*;import java.util.*;import java.security.MessageDigest;
import org.springframework.http.HttpStatus;import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
import static com.aistock.market.comparison.ComparisonUpdateRecords.*;
public final class ComparisonRefreshRepository {
 private final JdbcTemplate jdbc;private final ComparisonEvidenceRepository evidence;private final ComparisonEvidenceValidator validator;
 private final ObjectMapper mapper;private final Clock clock;private final TransactionTemplate tx;
 public record Claim(boolean execute,Result receipt){}
 public ComparisonRefreshRepository(JdbcTemplate jdbc,ComparisonEvidenceRepository evidence,ComparisonEvidenceValidator validator,ObjectMapper mapper,Clock clock){
  this.jdbc=jdbc;this.evidence=evidence;this.validator=validator;this.mapper=mapper;this.clock=clock;
  tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));tx.setTimeout(5);
 }
 static void validate(Request r){
  if(r==null||r.operationId()==null||r.rawVersionId()==null||r.instrumentId()==null||!r.instrumentId().matches("(SH|SZ)[0-9]{6}")
    ||r.rawContentHash()==null||!r.rawContentHash().matches("[a-f0-9]{64}")||!ComparisonEvidenceValidator.DEFINITION.equals(r.definition())
    ||r.start()==null||r.end()==null||!r.start().equals(r.end().minusYears(1))||r.start().isBefore(LocalDate.of(1990,1,1))
    ||r.leaseUntil()==null||!r.leaseUntil().equals(r.leaseUntil().truncatedTo(ChronoUnit.MILLIS)))
   throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");
 }
 public Result replay(Request r){
  validate(r);return tx.execute(s->{
   var rows=jdbc.queryForList("select * from comparison_refresh_receipt where operation_id=? for share",r.operationId().toString());
   if(rows.isEmpty())return null;verify(rows.get(0),r);return result(rows.get(0));
  });
 }
 public Claim begin(Request r){
  validate(r);
  // Finish the read transaction before admission; absent-row share locks must not be upgraded.
  var old=replay(r);if(old!=null)return new Claim(false,old);
  return tx.execute(s->{
   if(!clock.instant().isBefore(r.leaseUntil())||r.leaseUntil().isAfter(clock.instant().plusSeconds(120)))throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");
   int inserted=jdbc.update("insert ignore into comparison_refresh_receipt(operation_id,request_hash,instrument_id,raw_version_id,raw_content_hash,range_start,target_date,definition,lease_until,state,created_at) values(?,?,?,?,?,?,?,?,?,'RUNNING',?)",
    r.operationId().toString(),hash(r),r.instrumentId(),r.rawVersionId().toString(),r.rawContentHash(),r.start(),r.end(),r.definition(),Timestamp.from(r.leaseUntil()),Timestamp.from(clock.instant()));
   var row=lock(r);var got=result(row);
   return new Claim(inserted==1&&"RUNNING".equals(got.state()),got);
  });
 }
 public Result complete(Request r,ComparisonEvidenceRecords.Candidate candidate,String errorCode){return com.aistock.market.marketlifecycle.MarketEvidenceGate.local(()->{com.aistock.market.marketlifecycle.MarketEvidenceGate.raw(r.rawVersionId().toString());
  validate(r);
  try{return tx.execute(s->{
   var row=lock(r);var known=result(row);if(!"RUNNING".equals(known.state()))return known;
   guard(r);
   if(errorCode!=null){
    if(!errorCode.matches("[A-Z][A-Z0-9_]{0,63}"))throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");
    jdbc.update("update comparison_refresh_receipt set state='FAILED',fencing_revision=fencing_revision+1,error_code=?,finished_at=? where operation_id=?",errorCode,Timestamp.from(clock.instant()),r.operationId().toString());
    guard(r);return new Result(r.operationId().toString(),"FAILED",null,errorCode);
   }
   checkCandidate(r,candidate);
   var published=evidence.publish(candidate,hash(candidate),validator,()->guard(r));
   jdbc.update("update comparison_refresh_receipt set state='AVAILABLE',fencing_revision=fencing_revision+1,evidence_id=?,finished_at=? where operation_id=?",
    published.evidenceId().toString(),Timestamp.from(clock.instant()),r.operationId().toString());
   guard(r);
   return new Result(r.operationId().toString(),"AVAILABLE",published.evidenceId().toString(),null);
  });}catch(BusinessException e){
   if(!"ATTEMPT_EXPIRED".equals(e.code()))throw e;
   return lookup(r.operationId());
  }
 });}
 public String marketUseInput(Request request){return tx.execute(s->{var row=lock(request);if(!"RUNNING".equals(row.get("state"))||!clock.instant().isBefore(instant(row.get("lease_until"))))throw com.aistock.market.marketlifecycle.MarketComparisonUseOwner.unavailable();return row.get("raw_version_id").toString();});}
 public com.aistock.market.marketlifecycle.MarketLifecycleRecords.TerminalProof executionTerminal(UUID operation,List<String> versions){return tx.execute(s->{var rows=jdbc.queryForList("select * from comparison_refresh_receipt where operation_id=? for update",operation.toString());if(rows.size()!=1)throw com.aistock.market.marketlifecycle.MarketComparisonUseOwner.unavailable();var row=rows.get(0);if(!versions.equals(List.of(row.get("raw_version_id").toString())))throw com.aistock.market.marketlifecycle.MarketComparisonUseOwner.unavailable();Instant now=clock.instant();if("RUNNING".equals(row.get("state"))&&!now.isBefore(instant(row.get("lease_until")))){jdbc.update("update comparison_refresh_receipt set state='FAILED',error_code='ATTEMPT_EXPIRED',finished_at=?,fencing_revision=fencing_revision+1 where operation_id=? and state='RUNNING'",Timestamp.from(now),operation.toString());row=jdbc.queryForMap("select * from comparison_refresh_receipt where operation_id=?",operation.toString());}if(!Set.of("AVAILABLE","FAILED").contains(row.get("state"))||row.get("finished_at")==null)throw com.aistock.market.marketlifecycle.MarketComparisonUseOwner.unavailable();return new com.aistock.market.marketlifecycle.MarketLifecycleRecords.TerminalProof(new com.aistock.common.marketlifecycle.MarketUseRecords.UseIdentity("market","COMPARISON_EVIDENCE_PUBLISH",operation.toString(),"1"),"AVAILABLE".equals(row.get("state"))?"SUCCEEDED":"FAILED",instant(row.get("finished_at")),row.get("fencing_revision").toString(),row.get("evidence_id")==null?operation.toString():row.get("evidence_id").toString(),versions);});}
 static void checkCandidate(Request r,ComparisonEvidenceRecords.Candidate c){
  if(c==null||!r.operationId().toString().equals(c.operationId())||!r.instrumentId().equals(c.instrumentId())||!r.rawVersionId().equals(c.rawVersionId())
    ||!r.start().equals(c.from())||!r.end().equals(c.to())||!"baostock".equals(c.provider())||!r.definition().equals(c.definition()))
   throw ComparisonEvidenceValidator.invalid("SOURCE_INVALID");
  if(c.points()==null||c.points().stream().noneMatch(p->p!=null&&r.end().equals(p.date())))
   throw ComparisonEvidenceValidator.invalid("COMPARISON_TARGET_MISSING");
 }
 public Result lookup(UUID op){
  if(op==null)throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");
  // A current locking read waits for an in-flight publisher before inferring expiry.
  // Lock/transaction timeout propagates as unknown, never as a terminal receipt.
  return tx.execute(s->{
   var rows=jdbc.queryForList("select * from comparison_refresh_receipt where operation_id=? for share",op.toString());
   if(rows.isEmpty())throw error("COMPARISON_REFRESH_NOT_FOUND",HttpStatus.NOT_FOUND);
   return result(rows.get(0));
  });
 }
 private Map<String,Object> lock(Request r){
  var rows=jdbc.queryForList("select * from comparison_refresh_receipt where operation_id=? for update",r.operationId().toString());
  if(rows.isEmpty())throw error("COMPARISON_REFRESH_NOT_FOUND",HttpStatus.NOT_FOUND);verify(rows.get(0),r);return rows.get(0);
 }
 private void verify(Map<String,Object> row,Request r){if(!hash(r).equals(row.get("request_hash")))throw error("COMPARISON_OPERATION_CONFLICT",HttpStatus.CONFLICT);}
 private Result result(Map<String,Object> row){
  var state=(String)row.get("state");var op=(String)row.get("operation_id");
  if("RUNNING".equals(state)&&!clock.instant().isBefore(instant(row.get("lease_until"))))return new Result(op,"FAILED",null,"ATTEMPT_EXPIRED");
  return new Result(op,state,(String)row.get("evidence_id"),(String)row.get("error_code"));
 }
 private static Instant instant(Object v){return v instanceof Timestamp t?t.toInstant():((LocalDateTime)v).toInstant(ZoneOffset.UTC);}
 private void guard(Request r){if(!clock.instant().isBefore(r.leaseUntil()))throw error("ATTEMPT_EXPIRED",HttpStatus.CONFLICT);}
 private String hash(Object o){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(o)));}
  catch(Exception e){throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");}
 }
 private static BusinessException error(String code,HttpStatus status){return new BusinessException(code,"比较证据更新未完成，原版本保留",status);}
}
