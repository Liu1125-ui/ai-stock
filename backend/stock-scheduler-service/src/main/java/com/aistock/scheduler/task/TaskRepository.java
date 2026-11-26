package com.aistock.scheduler.task;
import com.aistock.common.BusinessException;
import java.time.*;
import java.sql.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
@Repository public class TaskRepository {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;
    private final long comparisonRetryFirst,comparisonRetrySecond;
    private static final Set<String> LEGACY_TYPES=Set.of("MARKET_REFRESH","PREDICTION_VERIFY");
    private static final Set<String> COMPARISON_RETRYABLE=Set.of("SOURCE_TIMEOUT","SOURCE_BUSY","SOURCE_UNAVAILABLE","ATTEMPT_EXPIRED");
    private static final Set<String> RETRYABLE=Set.of("SOURCE_TIMEOUT","SOURCE_BUSY","SOURCE_UNAVAILABLE","MARKET_REVISION_CONFLICT","ATTEMPT_EXPIRED","REFRESH_RUNNING","VERIFICATION_SOURCE_UNAVAILABLE","VERIFICATION_BUDGET_EXCEEDED","REALIZATION_RAW_UNAVAILABLE");
    public TaskRepository(JdbcTemplate jdbc){this(jdbc,900,1800);}
    @org.springframework.beans.factory.annotation.Autowired
    public TaskRepository(JdbcTemplate jdbc,@org.springframework.beans.factory.annotation.Value("${aistock.comparison-update.retry-first-seconds:900}")long first,
      @org.springframework.beans.factory.annotation.Value("${aistock.comparison-update.retry-second-seconds:1800}")long second){
        if(first<1||second<1)throw new IllegalArgumentException("Invalid comparison retry configuration");
        this.comparisonRetryFirst=first;this.comparisonRetrySecond=second;this.jdbc=jdbc;
        this.tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
    }
    public Task create(String instrument,LocalDate target,String key,Instant now) {
        if(instrument==null || !instrument.matches("(SH|SZ)[0-9]{6}") || target==null || target.isBefore(LocalDate.of(1990,1,1)) || target.isAfter(now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()) || key==null || key.startsWith("admin-market-refresh-") || !key.matches("[A-Za-z0-9_-]{1,128}"))throw error("TASK_REQUEST_INVALID",HttpStatus.BAD_REQUEST);
        return tx.execute(status->{
            var id=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO scheduled_task(task_id,task_type,instrument_id,target_date,idempotency_key,state,attempt_count,next_at,created_at,updated_at) VALUES (?,'MARKET_REFRESH',?,?,?,'QUEUED',0,?,?,?) ON DUPLICATE KEY UPDATE task_id=task_id",id,instrument,target,key,ts(now),ts(now),ts(now));
            var result=jdbc.queryForObject("SELECT * FROM scheduled_task WHERE idempotency_key=? FOR UPDATE",this::map,key);
            if(!"MARKET_REFRESH".equals(result.taskType())||!instrument.equals(result.instrumentId())||!target.equals(result.targetDate()))throw error("TASK_KEY_CONFLICT",HttpStatus.CONFLICT);
            return result;
        });
    }
    public Task createAdminRefresh(String stock,LocalDate target,UUID operation,com.aistock.common.adminrefresh.AdminMarketRefreshRecords.Constraint c,Instant now) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("admin refresh requires caller transaction");
        String id=UUID.randomUUID().toString();
        jdbc.update("insert into scheduled_task(task_id,task_type,instrument_id,target_date,idempotency_key,state,attempt_count,next_at,created_at,updated_at) values(?,'MARKET_REFRESH',?,?,?,'QUEUED',0,?,?,?)",id,stock,target,"admin-market-refresh-"+operation,ts(now),ts(now),ts(now));
        jdbc.update("insert into admin_market_refresh_context(task_id,instrument_id,base_version_id,head_revision,coverage_start,target_date,policy_version,created_at)values(?,?,?,?,?,?,?,?)",id,stock,c.baseVersionId(),c.headRevision(),c.coverageStart(),target,c.policyVersion(),ts(now));
        return get(id);
    }
    public com.aistock.common.adminrefresh.AdminMarketRefreshRecords.Constraint adminContext(Claim claim) {
        var task=jdbc.queryForMap("select idempotency_key from scheduled_task where task_id=?",claim.taskId());
        if(!((String)task.get("idempotency_key")).startsWith("admin-market-refresh-"))return null;
        var rows=jdbc.queryForList("select * from admin_market_refresh_context where task_id=?",claim.taskId());
        if(rows.size()!=1)throw error("ADMIN_REFRESH_CONTEXT_INVALID",HttpStatus.CONFLICT);
        var c=rows.get(0);
        if(!claim.instrumentId().equals(c.get("instrument_id"))||!claim.targetDate().equals(((java.sql.Date)c.get("target_date")).toLocalDate()))throw error("ADMIN_REFRESH_CONTEXT_INVALID",HttpStatus.CONFLICT);
        try{return new com.aistock.common.adminrefresh.AdminMarketRefreshRecords.Constraint((String)c.get("base_version_id"),((Number)c.get("head_revision")).longValue(),((java.sql.Date)c.get("coverage_start")).toLocalDate(),(String)c.get("policy_version"));}
        catch(RuntimeException e){throw error("ADMIN_REFRESH_CONTEXT_INVALID",HttpStatus.CONFLICT);}
    }
    public Task createVerification(String prediction,String instrument,LocalDate target,String rule,String key,Instant now) {
        var local=now.atZone(ZoneId.of("Asia/Shanghai"));var cutoff=local.toLocalDate().minusDays(local.getHour()<20?1:0);
        if(!validVersion(prediction)||instrument==null||!instrument.matches("(SH|SZ)[0-9]{6}")||target==null||target.isBefore(LocalDate.of(1990,1,1))||target.isAfter(cutoff)||!"origin-share-close-v1".equals(rule)||key==null||!key.matches("[A-Za-z0-9_-]{1,128}"))throw error("TASK_REQUEST_INVALID",HttpStatus.BAD_REQUEST);
        return tx.execute(status->{jdbc.update("insert into scheduled_task(task_id,task_type,prediction_id,rule_version,instrument_id,target_date,idempotency_key,state,attempt_count,next_at,created_at,updated_at) values(?,'PREDICTION_VERIFY',?,?,?,?,?,'QUEUED',0,?,?,?) on duplicate key update task_id=task_id",UUID.randomUUID().toString(),prediction,rule,instrument,target,key,ts(now),ts(now),ts(now));var t=jdbc.queryForObject("select * from scheduled_task where idempotency_key=? for update",this::map,key);if(!"PREDICTION_VERIFY".equals(t.taskType())||!prediction.equals(t.predictionId())||!rule.equals(t.ruleVersion())||!instrument.equals(t.instrumentId())||!target.equals(t.targetDate()))throw error("TASK_KEY_CONFLICT",HttpStatus.CONFLICT);return t;});
    }
    public Task createComparison(ComparisonTaskRecords.Snapshot s,LocalDate cycle,int limit,Instant now) {
        var local=now.atZone(ZoneId.of("Asia/Shanghai"));
        if(s==null||s.instrumentId()==null||!s.instrumentId().matches("(SH|SZ)[0-9]{6}")||s.rawVersionId()==null
         ||s.rawContentHash()==null||!s.rawContentHash().matches("[a-f0-9]{64}")||s.start()==null||s.end()==null
         ||!s.start().equals(s.end().minusYears(1))||s.start().isBefore(LocalDate.of(1990,1,1))||s.end().isAfter(local.toLocalDate())
         ||!"baostock-comparison-backward-close-v1".equals(s.definition())||s.planAsOf()==null
         ||!s.planAsOf().equals(s.planAsOf().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
         ||s.planAsOf().isBefore(now.minusSeconds(5))||s.planAsOf().isAfter(now.plusSeconds(5))
         ||cycle==null||!cycle.equals(local.toLocalDate())||!cycle.equals(s.planAsOf().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate())
         ||local.getHour()<20||limit<1||limit>3)throw error("TASK_REQUEST_INVALID",HttpStatus.UNPROCESSABLE_ENTITY);
        for (int attempt = 1; ; attempt++) {
            try {
                return createComparisonInTransaction(s, cycle, limit, now);
            } catch (org.springframework.dao.CannotAcquireLockException failure) {
                // MySQL has rolled back a deadlock victim, so retry the entire registration.
                if (attempt == 3 || !(failure.getMostSpecificCause() instanceof SQLException sql)
                    || sql.getErrorCode() != 1213) throw failure;
            }
        }
    }

    private Task createComparisonInTransaction(ComparisonTaskRecords.Snapshot s, LocalDate cycle,
                                               int limit, Instant now) {
        return tx.execute(status->{
         var key="comp-update:"+cycle+":"+s.instrumentId()+":v1";var id=UUID.randomUUID().toString();
         jdbc.update("insert into scheduled_task(task_id,task_type,instrument_id,target_date,idempotency_key,state,attempt_count,next_at,created_at,updated_at) values(?,'COMPARISON_UPDATE',?,?,?,'QUEUED',0,?,?,?) on duplicate key update task_id=task_id",
          id,s.instrumentId(),s.end(),key,ts(now),ts(now),ts(now));
         var saved=jdbc.queryForObject("select * from scheduled_task where idempotency_key=? for update",this::map,key);
         if(saved==null||!"COMPARISON_UPDATE".equals(saved.taskType())||!s.instrumentId().equals(saved.instrumentId()))throw error("TASK_KEY_CONFLICT",HttpStatus.CONFLICT);
         var contexts=jdbc.queryForList("select task_id from comparison_update_context where task_id=?",saved.taskId());
         if(contexts.isEmpty()){
          if(!saved.taskId().equals(id))throw error("COMPARISON_CONTEXT_INVALID",HttpStatus.CONFLICT);
          jdbc.update("insert into comparison_update_context(task_id,cycle_date,instrument_id,raw_version_id,raw_content_hash,range_start,target_date,definition,plan_as_of,attempt_limit) values(?,?,?,?,?,?,?,?,?,?)",
           id,cycle,s.instrumentId(),s.rawVersionId().toString(),s.rawContentHash(),s.start(),s.end(),s.definition(),ts(s.planAsOf()),limit);
         }
         comparisonContext(saved.taskId());return saved;
        });
    }
    public ComparisonTaskRecords.Context comparisonContext(String id) {
        var task=get(id);
        var contexts=jdbc.query("select * from comparison_update_context where task_id=?",(rs,n)->
         new ComparisonTaskRecords.Context(id,rs.getDate("cycle_date").toLocalDate(),
          new ComparisonTaskRecords.Snapshot(rs.getString("instrument_id"),UUID.fromString(rs.getString("raw_version_id")),
           rs.getString("raw_content_hash"),rs.getDate("range_start").toLocalDate(),rs.getDate("target_date").toLocalDate(),
           rs.getString("definition"),rs.getTimestamp("plan_as_of").toInstant()),rs.getInt("attempt_limit")),id);
        if(!"COMPARISON_UPDATE".equals(task.taskType())||contexts.size()!=1)throw error("COMPARISON_CONTEXT_INVALID",HttpStatus.CONFLICT);
        var c=contexts.get(0);
        if(!task.instrumentId().equals(c.snapshot().instrumentId())||!task.targetDate().equals(c.snapshot().end()))throw error("COMPARISON_CONTEXT_INVALID",HttpStatus.CONFLICT);
        return c;
    }
    public Task createRecheck(String prediction,String instrument,LocalDate target,UUID request,Instant now){if(request==null)throw error("TASK_REQUEST_INVALID",HttpStatus.BAD_REQUEST);return createVerification(prediction,instrument,target,"origin-share-close-v1","recheck-"+prediction+"-"+request+"-origin-share-close-v1",now);}
    public static String verificationKey(PredictionTaskClient.Item item){return "verify-"+item.predictionId()+"-"+item.dueThrough()+"-"+item.ruleVersion();}
    public Claim claim(Instant now) {return claim(now,LEGACY_TYPES);}
    public Claim claim(Instant now,Set<String> allowedTypes) {
        var types=types(allowedTypes);if(types.isEmpty())return null;
        return tx.execute(status->{
            if(types.contains("COMPARISON_UPDATE")){
             var outdated=jdbc.query("select t.* from scheduled_task t join comparison_update_context c on t.task_id=c.task_id where t.task_type='COMPARISON_UPDATE' and t.state in ('QUEUED','RETRY_WAIT') and c.cycle_date<? order by t.created_at limit 100 for update skip locked",
              this::map,now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate());
             for(var t:outdated)jdbc.update("update scheduled_task set revision=revision+1,state='FAILED',error_code='COMPARISON_CYCLE_EXPIRED',updated_at=? where task_id=?",ts(now),t.taskId());
            }
            var marks=String.join(",",Collections.nCopies(types.size(),"?"));var expiryArgs=new ArrayList<Object>(types);expiryArgs.add(ts(now));
            var expired=jdbc.query("SELECT * FROM scheduled_task WHERE task_type IN ("+marks+") AND state='RUNNING' AND lease_until<=? ORDER BY lease_until LIMIT 100 FOR UPDATE SKIP LOCKED",this::map,expiryArgs.toArray());
            for(var t:expired){
                jdbc.update("UPDATE task_attempt SET state='UNKNOWN',error_code='RESULT_UNKNOWN' WHERE attempt_id=? AND state='RUNNING'",t.currentAttempt());
                jdbc.update("UPDATE scheduled_task SET revision=revision+1,state='RECONCILING',error_code='RESULT_UNKNOWN',updated_at=? WHERE task_id=?",ts(now),t.taskId());
            }
            var claimArgs=new ArrayList<Object>(expiryArgs);claimArgs.add(now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate());
            var queued=jdbc.query("SELECT t.* FROM scheduled_task t LEFT JOIN comparison_update_context c ON c.task_id=t.task_id WHERE t.task_type IN ("+marks+") AND t.state IN ('QUEUED','RETRY_WAIT') AND t.next_at<=? AND (t.task_type<>'COMPARISON_UPDATE' OR c.cycle_date=?) ORDER BY t.next_at,t.created_at,t.task_id LIMIT 1 FOR UPDATE SKIP LOCKED",this::map,claimArgs.toArray());
            if(queued.isEmpty())return null;
            var t=queued.get(0);
            if("COMPARISON_UPDATE".equals(t.taskType())&&attemptTotal(t.taskId())>=comparisonContext(t.taskId()).attemptLimit()){
             jdbc.update("update scheduled_task set revision=revision+1,state='FAILED',error_code='COMPARISON_BUDGET_EXHAUSTED',updated_at=? where task_id=?",ts(now),t.taskId());return null;
            }
            var token=UUID.randomUUID().toString();var until=now.plusSeconds("PREDICTION_VERIFY".equals(t.taskType())?120:90).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            jdbc.update("UPDATE scheduled_task SET revision=revision+1,state='RUNNING',attempt_count=attempt_count+1,current_attempt=?,lease_until=?,updated_at=? WHERE task_id=?",token,ts(until),ts(now),t.taskId());
            int ordinal=attemptTotal(t.taskId())+1;
            jdbc.update("INSERT INTO task_attempt(attempt_id,task_id,ordinal,state,started_at,lease_until) VALUES (?,?,?,'RUNNING',?,?)",token,t.taskId(),ordinal,ts(now),ts(until));
            return new Claim(t.taskId(),token,t.instrumentId(),t.targetDate(),until,t.taskType(),t.predictionId(),t.ruleVersion());
        });
    }
    private int attemptTotal(String id){return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM task_attempt WHERE task_id=?",Integer.class,id));}
    private List<String> types(Set<String> allowed){
     if(allowed==null||!Set.of("MARKET_REFRESH","PREDICTION_VERIFY","COMPARISON_UPDATE").containsAll(allowed))throw error("TASK_TYPE_INVALID",HttpStatus.UNPROCESSABLE_ENTITY);
     return allowed.stream().sorted().toList();
    }
    public void markComparisonUnknown(Claim claim,Instant now) {
        tx.executeWithoutResult(status->{
            var task=locked(claim.taskId());
            if(!"COMPARISON_UPDATE".equals(task.taskType())||!"RUNNING".equals(task.state())||!claim.attemptId().equals(task.currentAttempt()))return;
            jdbc.update("update task_attempt set state='UNKNOWN',error_code='RESULT_UNKNOWN' where attempt_id=? and state='RUNNING'",claim.attemptId());
            jdbc.update("update scheduled_task set revision=revision+1,state='RECONCILING',error_code='RESULT_UNKNOWN',updated_at=? where task_id=?",ts(now),task.taskId());
        });
    }
    public boolean finish(Claim claim,MarketRefreshClient.Result result,Instant now) {
        return finish(claim,result,now,false);
    }
    public boolean reconcile(Claim claim,MarketRefreshClient.Result result,Instant now) {
        if(result==null || !claim.attemptId().equals(result.operationId()) || "RUNNING".equals(result.state()))return false;
        return finish(claim,result,now,true);
    }
    private boolean finish(Claim claim,MarketRefreshClient.Result result,Instant now,boolean confirmedReceipt) {
        return Boolean.TRUE.equals(tx.execute(status->{
            var t=locked(claim.taskId());
            if(!Set.of("RUNNING","RECONCILING").contains(t.state())||!claim.attemptId().equals(t.currentAttempt())||(!confirmedReceipt && !now.isBefore(t.leaseUntil())))return false;
            String state,error=null,version=null;
            if(result!=null && claim.attemptId().equals(result.operationId()) && "AVAILABLE".equals(result.state()) && validVersion(result.versionId()) && result.errorCode()==null){state="SUCCEEDED";version=result.versionId();}
            else if("MARKET_REFRESH".equals(claim.taskType()) && result!=null && claim.attemptId().equals(result.operationId()) && "LEGITIMATE_EMPTY".equals(result.state()) && validVersion(result.versionId()) && result.errorCode()==null){state="EMPTY";version=result.versionId();}
            else if("PREDICTION_VERIFY".equals(claim.taskType()) && result!=null && claim.attemptId().equals(result.operationId()) && "SKIPPED_NOT_DUE".equals(result.state()) && result.versionId()==null && result.errorCode()==null){state="SKIPPED";}
            else {
                state="FAILED";
                error=result!=null && claim.attemptId().equals(result.operationId()) && "FAILED".equals(result.state()) && result.errorCode()!=null && result.errorCode().matches("[A-Z0-9_]{1,64}")?result.errorCode():"SOURCE_INVALID";
            }
            jdbc.update("UPDATE task_attempt SET state=?,version_id=?,error_code=?,finished_at=? WHERE attempt_id=? AND state IN ('RUNNING','UNKNOWN')",state,version,error,ts(now),claim.attemptId());
            if(error!=null && ("COMPARISON_UPDATE".equals(t.taskType())?COMPARISON_RETRYABLE:RETRYABLE).contains(error))transitionFailure(t,error,now);
            else jdbc.update("UPDATE scheduled_task SET revision=revision+1,state=?,version_id=?,error_code=?,current_attempt=NULL,lease_until=NULL,updated_at=? WHERE task_id=?",state,version,error,ts(now),t.taskId());
            return true;
        }));
    }
    private void transitionFailure(Task t,String code,Instant now) {
        if("COMPARISON_UPDATE".equals(t.taskType())){
         var c=comparisonContext(t.taskId());int total=attemptTotal(t.taskId());
         boolean retry=total<c.attemptLimit()&&c.cycleDate().equals(now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate());
         jdbc.update("UPDATE scheduled_task SET revision=revision+1,state=?,error_code=?,current_attempt=NULL,lease_until=NULL,next_at=?,updated_at=? WHERE task_id=?",
          retry?"RETRY_WAIT":"FAILED",code,ts(now.plusSeconds(total==1?comparisonRetryFirst:comparisonRetrySecond)),ts(now),t.taskId());return;
        }
        boolean delayedRecovery = eligibleForDailyRecovery(t, code);
        boolean retry = t.attemptCount() < 3 || delayedRecovery;
        long delaySeconds = t.attemptCount() == 1 ? 60 : 120;
        if (delayedRecovery) delaySeconds = Duration.ofMinutes(30).toSeconds();
        jdbc.update("UPDATE scheduled_task SET revision=revision+1,state=?,error_code=?,current_attempt=NULL,lease_until=NULL,next_at=?,updated_at=? WHERE task_id=?",
            retry ? "RETRY_WAIT" : "FAILED", code, ts(now.plusSeconds(delaySeconds)), ts(now), t.taskId());
    }

    private boolean eligibleForDailyRecovery(Task task, String code) {
        if (!"MARKET_REFRESH".equals(task.taskType()) || task.attemptCount() != 3 ||
            !Set.of("SOURCE_UNAVAILABLE", "SOURCE_TIMEOUT", "SOURCE_BUSY").contains(code)) return false;
        var expectedKey = "daily-" + task.instrumentId() + "-" + task.targetDate();
        var savedKey = jdbc.queryForObject("SELECT idempotency_key FROM scheduled_task WHERE task_id=?",
            String.class, task.taskId());
        return expectedKey.equals(savedKey) && attemptTotal(task.taskId()) == 3;
    }

    public Task retry(String id,Instant now) {
        // Manual refresh retries require actor, operation id and expected revision through the audited command.
        if(new com.aistock.scheduler.adminrefresh.AdminMarketRefreshAdmission(jdbc,60).scope(id)!=null)throw error("TASK_ACTION_NOT_ALLOWED",HttpStatus.CONFLICT);
        return tx.execute(status->{var t=locked(id);if(!"FAILED".equals(t.state()))throw error("TASK_STATE_CONFLICT",HttpStatus.CONFLICT);
            if("COMPARISON_UPDATE".equals(t.taskType())){
             var c=comparisonContext(id);
             if(!c.cycleDate().equals(now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate())||attemptTotal(id)>=c.attemptLimit()
               ||!COMPARISON_RETRYABLE.contains(t.errorCode()==null?"":t.errorCode()))throw error("COMPARISON_RETRY_NOT_ALLOWED",HttpStatus.CONFLICT);
             jdbc.update("UPDATE scheduled_task SET revision=revision+1,state='QUEUED',error_code=NULL,version_id=NULL,next_at=?,updated_at=? WHERE task_id=?",ts(now),ts(now),id);
            }else jdbc.update("UPDATE scheduled_task SET revision=revision+1,state='QUEUED',attempt_count=0,error_code=NULL,version_id=NULL,next_at=?,updated_at=? WHERE task_id=?",ts(now),ts(now),id);
            return get(id);});
    }
    public Task cancel(String id,Instant now) {
        return tx.execute(status->{var t=locked(id);if(!Set.of("QUEUED","RETRY_WAIT").contains(t.state()))throw error("TASK_STATE_CONFLICT",HttpStatus.CONFLICT);
            jdbc.update("UPDATE scheduled_task SET revision=revision+1,state='CANCELLED',updated_at=? WHERE task_id=?",ts(now),id);return get(id);});
    }
    public void enqueueDaily(List<String> instruments,Instant now) {
        var local=now.atZone(ZoneId.of("Asia/Shanghai"));var due=local.getHour()<20?local.toLocalDate().minusDays(1):local.toLocalDate();
        for(var id:instruments){
            if(!id.matches("(SH|SZ)[0-9]{6}"))throw error("TASK_REQUEST_INVALID",HttpStatus.BAD_REQUEST);
            tx.executeWithoutResult(status->{
                jdbc.update("INSERT INTO market_update_plan(instrument_id,enabled) VALUES (?,true) ON DUPLICATE KEY UPDATE enabled=true",id);
                var last=jdbc.query("SELECT last_target FROM market_update_plan WHERE instrument_id=? FOR UPDATE",(rs,n)->rs.getDate(1)==null?null:rs.getDate(1).toLocalDate(),id).get(0);
                if(last==null||last.isBefore(due)){
                    create(id,due,"daily-"+id+"-"+due,now);
                    jdbc.update("UPDATE market_update_plan SET last_target=? WHERE instrument_id=?",due,id);
                }
            });
        }
    }
    public Task get(String id){validateId(id);var rows=jdbc.query("SELECT * FROM scheduled_task WHERE task_id=?",this::map,id);if(rows.isEmpty())throw error("TASK_NOT_FOUND",HttpStatus.NOT_FOUND);return rows.get(0);}
    private Task locked(String id){validateId(id);var rows=jdbc.query("SELECT * FROM scheduled_task WHERE task_id=? FOR UPDATE",this::map,id);if(rows.isEmpty())throw error("TASK_NOT_FOUND",HttpStatus.NOT_FOUND);return rows.get(0);}
    public List<Claim> pendingReceipts(){return pendingReceipts(LEGACY_TYPES);}
    public List<Claim> pendingReceipts(Set<String> allowedTypes){
        var types=types(allowedTypes);if(types.isEmpty())return List.of();
        var marks=String.join(",",Collections.nCopies(types.size(),"?"));
        return jdbc.query("SELECT * FROM scheduled_task WHERE task_type IN ("+marks+") AND state IN ('RUNNING','RECONCILING') ORDER BY updated_at LIMIT 20", (rs,n)->new Claim(rs.getString("task_id"),rs.getString("current_attempt"),rs.getString("instrument_id"),rs.getDate("target_date").toLocalDate(),rs.getTimestamp("lease_until").toInstant(),rs.getString("task_type"),rs.getString("prediction_id"),rs.getString("rule_version")),types.toArray());
    }
    public List<Task> list(){return jdbc.query("SELECT * FROM scheduled_task ORDER BY created_at DESC,task_id LIMIT 100",this::map);}
    public List<Map<String,Object>> attempts(String id){get(id);return jdbc.queryForList("SELECT attempt_id,ordinal,state,started_at,lease_until,finished_at,version_id,error_code FROM task_attempt WHERE task_id=? ORDER BY ordinal LIMIT 100",id);}
    private Task map(ResultSet r,int n)throws SQLException {
        return new Task(r.getString("task_id"),r.getString("instrument_id"),r.getDate("target_date").toLocalDate(),r.getString("state"),r.getInt("attempt_count"),r.getString("current_attempt"),instant(r,"lease_until"),r.getString("version_id"),r.getString("error_code"),instant(r,"next_at"),instant(r,"created_at"),instant(r,"updated_at"),r.getString("task_type"),r.getString("prediction_id"),r.getString("rule_version"));
    }
    private Instant instant(ResultSet r,String name)throws SQLException{var t=r.getTimestamp(name);return t==null?null:t.toInstant();}
    private Timestamp ts(Instant value){return Timestamp.from(value.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));}
    private boolean validVersion(String id){return id!=null&&id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}");}
    private void validateId(String id){if(!validVersion(id))throw error("TASK_REQUEST_INVALID",HttpStatus.BAD_REQUEST);}
    private BusinessException error(String code,HttpStatus status){return new BusinessException(code,"任务请求或状态不符合当前操作条件",status);}
    public record Task(String taskId,String instrumentId,LocalDate targetDate,String state,int attemptCount,String currentAttempt,Instant leaseUntil,String versionId,String errorCode,Instant nextAt,Instant createdAt,Instant updatedAt,String taskType,String predictionId,String ruleVersion){}
    public record Claim(String taskId,String attemptId,String instrumentId,LocalDate targetDate,Instant leaseUntil,String taskType,String predictionId,String ruleVersion){}
}
