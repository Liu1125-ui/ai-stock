package com.aistock.prediction.comparisonai;

import com.aistock.common.BusinessException;
import com.aistock.prediction.maas.MaasOperationGate;
import com.aistock.prediction.comparison.ComparisonRecords;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.aistock.prediction.maas.MaasOperationGate.*;

/** MS03-only immutable results; no transaction spans a provider call. */
public final class ComparisonAiRepository {
    private static final String KIND="COMPARISON_EXPLANATION";
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final Clock clock;
    private final TransactionTemplate tx;private final MaasOperationGate gate;
    public record Claim(boolean execute,JsonNode receipt,boolean replayed,boolean cached) {}
    public ComparisonAiRepository(JdbcTemplate jdbc,ObjectMapper mapper,Clock clock) {
        this.jdbc=jdbc;this.mapper=mapper;this.clock=clock;this.gate=new MaasOperationGate(jdbc);
        tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        tx.setTimeout(5);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public JsonNode replay(UUID owner,ComparisonAiInput input,Instant now) {
        input.validate();var rows=ops(input.operationId(),false);
        if(rows.isEmpty())return null;checkRequest(rows.get(0),owner,input);return receipt(rows.get(0),now);
    }
    public Claim claim(UUID owner,ComparisonAiInput input,ComparisonAiFacts.Facts facts,Instant now) {
        input.validate();var prior=replay(owner,input,now);if(prior!=null)return new Claim(false,prior,true,false);
        return tx.execute(status->{
            gate.lock(clock.instant());var existing=ops(input.operationId(),true);
            if(!existing.isEmpty()){checkRequest(existing.get(0),owner,input);return new Claim(false,receipt(existing.get(0),clock.instant()),true,false);}
            owned(owner,input.comparisonId());
            if(!input.comparisonId().toString().equals(facts.context().path("comparisonId").asText())||!facts.hash().equals(ComparisonRecords.hash(facts.context()))||!facts.context().path("facts").equals(mapper.valueToTree(facts.values())))throw ComparisonAiFacts.invalid();
            String context=json(facts.context(),32768);
            var cached=jdbc.queryForList("SELECT explanation_id FROM comparison_ai_result WHERE user_id=? AND comparison_id=? AND context_hash=?",String.class,owner.toString(),input.comparisonId().toString(),facts.hash());
            String id=cached.isEmpty()?null:cached.get(0);Instant fresh=clock.instant();
            if(id==null)gate.lockAndAcquire(KIND,input.operationId(),fresh);
            jdbc.update("INSERT INTO comparison_ai_operation(operation_id,user_id,comparison_id,request_hash,context_hash,context_json,state,explanation_id,created_at,lease_until,finished_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",input.operationId().toString(),owner.toString(),input.comparisonId().toString(),requestHash(input),facts.hash(),context,id==null?"RUNNING":"SUCCEEDED",id,ts(fresh),ts(fresh.plusSeconds(50)),id==null?null:ts(fresh));
            return new Claim(id==null,receipt(ops(input.operationId(),false).get(0),fresh),false,id!=null);
        });
    }
    public JsonNode complete(UUID owner,UUID operation,JsonNode paragraphs,String error,Instant now) {
        return tx.execute(status->{
            var slot=gate.lock(clock.instant());var rows=ops(operation,true);
            if(rows.isEmpty()||!owner.toString().equals(rows.get(0).get("user_id")))throw missing();
            var row=rows.get(0);Instant fresh=clock.instant();
            if(!"RUNNING".equals(row.get("state")))return receipt(row,fresh);
            if(!gate.owns(slot,KIND,operation)||!fresh.isBefore(instant(row.get("lease_until")))||!fresh.isBefore(instant(slot.get("lease_until"))))throw conflict();
            String id=null;String safe=error;
            if(error==null) {
                if(paragraphs==null||!paragraphs.isArray()||paragraphs.size()!=3)throw ComparisonAiFacts.invalid();
                id=UUID.randomUUID().toString();
                jdbc.update("INSERT INTO comparison_ai_result(explanation_id,user_id,comparison_id,context_hash,context_json,paragraphs_json,created_at) VALUES(?,?,?,?,?,?,?)",id,owner.toString(),row.get("comparison_id"),row.get("context_hash"),row.get("context_json"),json(paragraphs,24576),ts(fresh));
            }else if(!safe.matches("[A-Z0-9_]{1,64}"))safe="AI_UNAVAILABLE";
            fresh=clock.instant();
            int updated=jdbc.update("UPDATE comparison_ai_operation SET state=?,explanation_id=?,error_code=?,finished_at=? WHERE operation_id=? AND user_id=? AND state='RUNNING' AND lease_until>?",id==null?"FAILED":"SUCCEEDED",id,safe,ts(fresh),operation.toString(),owner.toString(),ts(fresh));
            if(updated!=1)throw conflict();gate.release(KIND,operation);
            return receipt(ops(operation,false).get(0),fresh);
        });
    }
    public JsonNode operation(UUID owner,UUID operation,Instant now) {
        var rows=jdbc.queryForList("SELECT * FROM comparison_ai_operation WHERE operation_id=? AND user_id=?",operation.toString(),owner.toString());
        if(rows.isEmpty())throw missing();return receipt(rows.get(0),now);
    }
    public JsonNode view(UUID owner,UUID comparison,boolean configured,Instant now) {
        owned(owner,comparison);var result=mapper.createObjectNode().put("configured",configured);
        var saved=jdbc.queryForList("SELECT * FROM comparison_ai_result WHERE user_id=? AND comparison_id=? ORDER BY sequence_id DESC LIMIT 1",owner.toString(),comparison.toString());
        var attempts=jdbc.queryForList("SELECT * FROM comparison_ai_operation WHERE user_id=? AND comparison_id=? ORDER BY sequence_id DESC LIMIT 1",owner.toString(),comparison.toString());
        result.set("latest",saved.isEmpty()?mapper.nullNode():result(saved.get(0)));
        result.set("latestAttempt",attempts.isEmpty()?mapper.nullNode():receipt(attempts.get(0),now));return result;
    }
    public JsonNode get(UUID owner,UUID explanation) {
        var rows=jdbc.queryForList("SELECT * FROM comparison_ai_result WHERE explanation_id=? AND user_id=?",explanation.toString(),owner.toString());
        if(rows.isEmpty())throw missing();return result(rows.get(0));
    }
    private void owned(UUID owner,UUID comparison) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM stock_comparison_result WHERE owner_id=? AND comparison_id=?",Integer.class,owner.toString(),comparison.toString())!=1)throw missing();
    }
    private List<Map<String,Object>> ops(UUID operation,boolean lock) {return jdbc.queryForList("SELECT * FROM comparison_ai_operation WHERE operation_id=?"+(lock?" FOR UPDATE":""),operation.toString());}
    private void checkRequest(Map<String,Object> row,UUID owner,ComparisonAiInput input) {if(!owner.toString().equals(row.get("user_id")))throw missing();if(!requestHash(input).equals(row.get("request_hash")))throw conflict();}
    private String requestHash(ComparisonAiInput input){return ComparisonRecords.hash(List.of(input.comparisonId().toString()));}
    private JsonNode receipt(Map<String,Object> row,Instant now) {
        var n=mapper.createObjectNode();for(var p:List.of(new String[]{"operation_id","operationId"},new String[]{"comparison_id","comparisonId"},new String[]{"state","state"},new String[]{"explanation_id","explanationId"},new String[]{"error_code","errorCode"})){var value=row.get(p[0]);if(value==null)n.putNull(p[1]);else n.put(p[1],value.toString());}
        Instant until=instant(row.get("lease_until"));return n.put("leaseUntil",until.toString()).put("expired","RUNNING".equals(row.get("state"))&&!now.isBefore(until));
    }
    private JsonNode result(Map<String,Object> row) {
        ObjectNode n=mapper.createObjectNode();n.put("explanationId",row.get("explanation_id").toString()).put("comparisonId",row.get("comparison_id").toString()).put("createdAt",instant(row.get("created_at")).toString()).put("contextHash",row.get("context_hash").toString());
        try{n.set("context",mapper.readTree(row.get("context_json").toString()));n.set("paragraphs",mapper.readTree(row.get("paragraphs_json").toString()));return n;}catch(Exception e){throw ComparisonAiFacts.invalid();}
    }
    private String json(Object value,int limit) {try{String s=mapper.writeValueAsString(value);if(s.getBytes(StandardCharsets.UTF_8).length>limit)throw ComparisonAiFacts.invalid();return s;}catch(BusinessException e){throw e;}catch(Exception e){throw ComparisonAiFacts.invalid();}}
    private BusinessException missing(){return new BusinessException("RESOURCE_NOT_FOUND","未找到本人比较解释记录",HttpStatus.NOT_FOUND);}
    private BusinessException conflict(){return new BusinessException("AI_REQUEST_CONFLICT","请求编号或处理状态不一致，请查询原请求",HttpStatus.CONFLICT);}
}
