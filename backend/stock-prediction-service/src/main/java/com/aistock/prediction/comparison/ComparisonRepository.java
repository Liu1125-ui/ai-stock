package com.aistock.prediction.comparison;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static com.aistock.prediction.comparison.ComparisonRecords.*;

/** All transactions and records belong to MS03. Never holds a transaction over HTTP. */
public final class ComparisonRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    public record Claim(boolean execute,UUID generation,Operation operation,boolean created) {}
    public record Page(List<JsonNode> items,String upper,String nextBefore) {}
    public ComparisonRepository(JdbcTemplate jdbc,ObjectMapper mapper) {
        this.jdbc=jdbc;this.mapper=mapper;
        tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        tx.setTimeout(5);
    }
    public Operation replay(UUID owner,Input input,Instant now) {
        var rows=rows(owner,input.requestId(),false);
        if(rows.isEmpty())return null;
        fingerprint(rows.get(0),input);
        return receipt(rows.get(0),now);
    }
    public Claim claim(UUID owner,Input input,Instant now) {
        return tx.execute(status->{
            int inserted=jdbc.update("INSERT IGNORE INTO stock_comparison_operation(owner_id,request_id,fingerprint,input_json,state,created_at) VALUES(?,?,?,?,'RUNNING',?)",owner.toString(),input.requestId().toString(),input.fingerprint(),json(input),ts(now));
            var row=rows(owner,input.requestId(),true).get(0);
            fingerprint(row,input);
            var prior=receipt(row,now);
            if(inserted==0&&(!prior.state().equals("RUNNING")||!prior.expired()))return new Claim(false,null,prior,false);
            if(row.get("lease_generation")!=null)useTerminal(UUID.fromString(row.get("lease_generation").toString()),"FAILED",UUID.fromString(row.get("lease_generation").toString()),now,((Number)row.get("fencing_revision")).longValue()+1);
            UUID generation;do{generation=UUID.randomUUID();}while(generation.toString().equals(row.get("lease_generation"))||jdbc.queryForObject("select count(*) from comparison_market_use where execution_id=?",Integer.class,generation.toString())!=0);
            jdbc.update("UPDATE stock_comparison_operation SET lease_generation=?,lease_until=?,fencing_revision=fencing_revision+1 WHERE owner_id=? AND request_id=?",generation.toString(),ts(now.plusSeconds(45)),owner.toString(),input.requestId().toString());
            return new Claim(true,generation,receipt(rows(owner,input.requestId(),false).get(0),now),inserted==1);
        });
    }
    public Operation complete(UUID owner,Input input,UUID generation,Calculation calculation,List<JsonNode> sources,Instant now) {
        return tx.execute(status->{
            var row=ownedLease(owner,input,generation,now);
            UUID id=UUID.randomUUID();
            ObjectNode result=mapper.valueToTree(calculation);
            result.put("comparisonId",id.toString()).put("createdAt",now.toString()).put("snapshotLabel","历史快照");
            var summary=result.deepCopy();
            summary.remove(List.of("sourceRefs","sourceHash"));
            summary.path("stocks").forEach(stock->((ObjectNode)stock).remove(List.of("chart","dataIssues")));
            jdbc.update("INSERT INTO stock_comparison_result(comparison_id,owner_id,result_json,summary_json,source_snapshots_json,source_hash,created_at) VALUES(?,?,?,?,?,?,?)",id.toString(),owner.toString(),json(result),json(summary),json(sources),calculation.sourceHash(),ts(now));
            int updated=jdbc.update("UPDATE stock_comparison_operation SET state='SUCCEEDED',result_id=?,lease_until=NULL,error_code=NULL,fencing_revision=fencing_revision+1 WHERE owner_id=? AND request_id=? AND state='RUNNING' AND lease_generation=? AND lease_until>?",id.toString(),owner.toString(),input.requestId().toString(),generation.toString(),ts(now));
            if(updated!=1)throw ComparisonErrors.conflict();
            useTerminal(generation,"SUCCEEDED",id,now,((Number)row.get("fencing_revision")).longValue()+1);
            return receipt(rows(owner,input.requestId(),false).get(0),now);
        });
    }
    public Operation fail(UUID owner,Input input,UUID generation,String error,Instant now) {
        return tx.execute(status->{
            var row=ownedLease(owner,input,generation,now);
            String safe=error!=null&&error.matches("[A-Z0-9_]{1,64}")?error:"COMPARISON_MARKET_UNAVAILABLE";
            int updated=jdbc.update("UPDATE stock_comparison_operation SET state='FAILED',error_code=?,lease_until=NULL,fencing_revision=fencing_revision+1 WHERE owner_id=? AND request_id=? AND state='RUNNING' AND lease_generation=? AND lease_until>?",safe,owner.toString(),input.requestId().toString(),generation.toString(),ts(now));
            if(updated!=1)throw ComparisonErrors.conflict();
            useTerminal(generation,"FAILED",generation,now,((Number)row.get("fencing_revision")).longValue()+1);
            return receipt(rows(owner,input.requestId(),false).get(0),now);
        });
    }
    /** Called while the parent request lock is held; retirement becomes visible atomically with its fence. */
    private void useTerminal(UUID execution,String state,UUID result,Instant now,long revision){jdbc.update("update comparison_market_use set terminal_state=?,terminal_at=?,terminal_revision=?,result_identity=? where execution_id=? and terminal_state is null",state,ts(now),revision,result.toString(),execution.toString());}
    public com.aistock.common.marketlifecycle.MarketUseRecords.Selector useInput(UUID owner,Input input,UUID execution,Instant now){return tx.execute(s->{ownedLease(owner,input,execution,now);var pairs=new ArrayList<com.aistock.common.marketlifecycle.MarketUseRecords.EvidenceBinding>();for(int i=0;i<input.instrumentIds().size();i++)pairs.add(new com.aistock.common.marketlifecycle.MarketUseRecords.EvidenceBinding(input.instrumentIds().get(i),input.evidenceIds().get(i).toString()));return com.aistock.common.marketlifecycle.MarketLifecycleContract.selector(new com.aistock.common.marketlifecycle.MarketUseRecords.Selector("COMPARISON_EVIDENCE",null,pairs,null));});}
    public JsonNode executionTerminal(UUID execution,Instant now){return tx.execute(s->{
        var pointers=jdbc.queryForList("select owner_id,request_id from comparison_market_use where execution_id=?",execution.toString());if(pointers.size()!=1)throw com.aistock.prediction.marketuse.MarketUseClient.unavailable();var pointer=pointers.get(0);var owner=UUID.fromString(pointer.get("owner_id").toString());var request=UUID.fromString(pointer.get("request_id").toString());var parent=rows(owner,request,true);if(parent.size()!=1)throw com.aistock.prediction.marketuse.MarketUseClient.unavailable();var row=parent.get(0);var use=jdbc.queryForMap("select * from comparison_market_use where execution_id=? for update",execution.toString());
        if(use.get("terminal_state")==null){long fence=((Number)row.get("fencing_revision")).longValue();if(execution.toString().equals(row.get("lease_generation"))){
            if("RUNNING".equals(row.get("state"))){if(row.get("lease_until")!=null&&now.isBefore(instant(row.get("lease_until"))))throw com.aistock.prediction.marketuse.MarketUseClient.unavailable();UUID retired;do{retired=UUID.randomUUID();}while(retired.equals(execution));jdbc.update("update stock_comparison_operation set lease_generation=?,fencing_revision=fencing_revision+1 where owner_id=? and request_id=? and lease_generation=? and state='RUNNING'",retired.toString(),owner.toString(),request.toString(),execution.toString());fence++;useTerminal(execution,"FAILED",execution,now,fence);}
            else{boolean success="SUCCEEDED".equals(row.get("state"));if(success&&row.get("result_id")==null)throw com.aistock.prediction.marketuse.MarketUseClient.unavailable();useTerminal(execution,success?"SUCCEEDED":"FAILED",success?UUID.fromString(row.get("result_id").toString()):execution,now,Math.max(1,fence));}
        }else useTerminal(execution,"FAILED",execution,now,Math.max(1,fence));use=jdbc.queryForMap("select * from comparison_market_use where execution_id=?",execution.toString());}
        return mapper.createObjectNode().put("state",use.get("terminal_state").toString()).put("terminalAt",instant(use.get("terminal_at")).toString()).put("fencingRevision",use.get("terminal_revision").toString()).put("resultIdentity",use.get("result_identity").toString());
    });}
    private Map<String,Object> ownedLease(UUID owner,Input input,UUID generation,Instant now) {
        var found=rows(owner,input.requestId(),true);
        if(found.isEmpty())throw ComparisonErrors.conflict();
        var row=found.get(0);fingerprint(row,input);
        if(!"RUNNING".equals(row.get("state"))||generation==null||!generation.toString().equals(row.get("lease_generation"))||row.get("lease_until")==null||!now.isBefore(instant(row.get("lease_until"))))throw ComparisonErrors.conflict();
        return row;
    }
    public Operation operation(UUID owner,UUID requestId,Instant now) {
        var found=rows(owner,requestId,false);
        if(found.isEmpty())throw ComparisonErrors.missing();
        return receipt(found.get(0),now);
    }
    public JsonNode get(UUID owner,UUID id) {
        var found=jdbc.queryForList("SELECT result_json FROM stock_comparison_result WHERE owner_id=? AND comparison_id=?",String.class,owner.toString(),id.toString());
        if(found.isEmpty())throw ComparisonErrors.missing();
        return parse(found.get(0));
    }
    public Page list(UUID owner,String before,String upper) {
        if(before!=null&&upper==null)throw ComparisonErrors.invalid();
        Long b=number(before),u=number(upper);
        if(b!=null&&b>u)throw ComparisonErrors.invalid();
        if(u==null)u=jdbc.queryForObject("SELECT MAX(sequence_id) FROM stock_comparison_result WHERE owner_id=?",Long.class,owner.toString());
        if(u==null)return new Page(List.of(),null,null);
        var found=b==null?jdbc.queryForList("SELECT sequence_id,summary_json FROM stock_comparison_result WHERE owner_id=? AND sequence_id<=? ORDER BY sequence_id DESC LIMIT 11",owner.toString(),u):jdbc.queryForList("SELECT sequence_id,summary_json FROM stock_comparison_result WHERE owner_id=? AND sequence_id<=? AND sequence_id<? ORDER BY sequence_id DESC LIMIT 11",owner.toString(),u,b);
        var items=new ArrayList<JsonNode>();
        for(var row:found.subList(0,Math.min(10,found.size())))items.add(parse((String)row.get("summary_json")));
        String next=found.size()>10?found.get(9).get("sequence_id").toString():null;
        return new Page(List.copyOf(items),u.toString(),next);
    }
    private Long number(String value) {if(value==null)return null;try{if(!value.matches("[1-9][0-9]{0,18}"))throw ComparisonErrors.invalid();return Long.parseLong(value);}catch(NumberFormatException e){throw ComparisonErrors.invalid();}}
    private List<Map<String,Object>> rows(UUID owner,UUID id,boolean lock) {return jdbc.queryForList("SELECT * FROM stock_comparison_operation WHERE owner_id=? AND request_id=?"+(lock?" FOR UPDATE":""),owner.toString(),id.toString());}
    private void fingerprint(Map<String,Object> row,Input input){if(!input.fingerprint().equals(row.get("fingerprint")))throw ComparisonErrors.conflict();}
    private Operation receipt(Map<String,Object> row,Instant now) {
        var lease=row.get("lease_until")==null?null:instant(row.get("lease_until"));
        return new Operation(UUID.fromString(row.get("request_id").toString()),row.get("state").toString(),row.get("result_id")==null?null:UUID.fromString(row.get("result_id").toString()),(String)row.get("error_code"),lease,"RUNNING".equals(row.get("state"))&&(lease==null||!now.isBefore(lease)));
    }
    private JsonNode parse(String value){try{return mapper.readTree(value);}catch(Exception e){throw ComparisonErrors.unavailable();}}
    private String json(Object value){try{var bytes=mapper.writeValueAsBytes(value);if(bytes.length>2097152)throw ComparisonErrors.invalid();return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);}catch(com.aistock.common.BusinessException e){throw e;}catch(Exception e){throw ComparisonErrors.invalid();}}
    private Timestamp ts(Instant instant){return Timestamp.from(instant);}
    private Instant instant(Object value){return value instanceof Timestamp t?t.toInstant():((LocalDateTime)value).toInstant(ZoneOffset.UTC);}
}

