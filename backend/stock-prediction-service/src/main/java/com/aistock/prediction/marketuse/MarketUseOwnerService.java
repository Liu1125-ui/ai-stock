package com.aistock.prediction.marketuse;

import com.aistock.common.BusinessException;
import com.aistock.common.marketlifecycle.MarketLifecycleContract;
import com.aistock.prediction.analysis.AnalysisRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.aistock.prediction.maas.MaasOperationGate.*;

/** Analysis owner only. No cross-database writes, and no network call under the local transaction. */
public class MarketUseOwnerService {
    private record Binding(MarketUseClient.Request request,boolean started,MarketUseClient.Receipt receipt,boolean acked){}
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final AnalysisRepository analyses;private final MarketUseClient client;private final Clock clock;private final boolean enabled;private final TransactionTemplate tx;
    public MarketUseOwnerService(JdbcTemplate jdbc,ObjectMapper json,AnalysisRepository analyses,MarketUseClient client,Clock clock,boolean enabled){this.jdbc=jdbc;this.json=json;this.analyses=analyses;this.client=client;this.clock=clock;this.enabled=enabled;tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));tx.setTimeout(5);}
    public boolean enabled(){return enabled;}
    public MarketUseContext.Scope acquire(UUID operation,long generation){
        if(!enabled)return MarketUseContext.empty();var binding=prepare(operation,generation);client.requireReady();
        if(binding.receipt()==null){
            MarketUseClient.Receipt receipt;
            if(binding.started())receipt=client.find(binding.request()).orElseThrow(MarketUseClient::unavailable);
            else{if(start(operation,generation))receipt=client.register(binding.request());else receipt=client.find(binding.request()).orElseThrow(MarketUseClient::unavailable);}
            attach(operation,generation,receipt);binding=read(operation,generation);
        }
        return MarketUseContext.open(binding.request(),binding.receipt());
    }
    private Binding prepare(UUID operation,long generation){return tx.execute(s->{
        var op=operation(operation,generation);var prior=read(operation,generation);if(prior!=null)return prior;
        if(!"RUNNING".equals(op.get("state"))||ready(op)||!clock.instant().isBefore(instant(op.get("lease_until"))))throw MarketUseClient.unavailable();
        jdbc.update("insert into stock_analysis_market_use(operation_id,execution_generation,request_id,raw_version_id,created_at,last_checked_at) values(?,?,?,?,?,?)",operation.toString(),generation,UUID.randomUUID().toString(),op.get("market_version_id"),ts(clock.instant()),ts(clock.instant()));return read(operation,generation);
    });}
    private boolean start(UUID operation,long generation){return Boolean.TRUE.equals(tx.execute(s->{var op=operation(operation,generation);if(!"RUNNING".equals(op.get("state"))||!clock.instant().isBefore(instant(op.get("lease_until"))))throw MarketUseClient.unavailable();return jdbc.update("update stock_analysis_market_use set registration_started=true where operation_id=? and execution_generation=? and registration_started=false",operation.toString(),generation)==1;}));}
    private void attach(UUID operation,long generation,MarketUseClient.Receipt receipt){tx.executeWithoutResult(s->{
        var op=operation(operation,generation);var b=read(operation,generation);if(b==null||!b.started()||!List.of(op.get("market_version_id").toString()).equals(receipt.versionIds()))throw MarketUseClient.invalid();
        if(b.receipt()!=null){if(!b.receipt().bundleId().equals(receipt.bundleId())||!b.receipt().versionIds().equals(receipt.versionIds()))throw MarketUseClient.invalid();return;}
        jdbc.update("update stock_analysis_market_use set bundle_id=?,receipt_json=? where operation_id=? and execution_generation=? and bundle_id is null",receipt.bundleId(),encode(receipt),operation.toString(),generation);
    });}
    private Map<String,Object> operation(UUID operation,long generation){if(generation<1)throw MarketUseClient.invalid();var rows=jdbc.queryForList("select * from stock_analysis_operation where operation_id=? for update",operation.toString());if(rows.size()!=1||((Number)rows.get(0).get("execution_generation")).longValue()!=generation)throw MarketUseClient.unavailable();return rows.get(0);}
    private boolean ready(Map<String,Object> op){var value=op.get("context_ready");return Boolean.TRUE.equals(value)||value instanceof Number n&&n.intValue()==1;}
    private Binding read(UUID operation,long generation){var rows=jdbc.queryForList("select * from stock_analysis_market_use where operation_id=?",operation.toString());if(rows.isEmpty())return null;var r=rows.get(0);if(((Number)r.get("execution_generation")).longValue()!=generation)throw MarketUseClient.unavailable();try{return new Binding(new MarketUseClient.Request(operation.toString(),Long.toString(generation),r.get("request_id").toString(),r.get("raw_version_id").toString()),Boolean.TRUE.equals(r.get("registration_started")),r.get("receipt_json")==null?null:json.readValue(r.get("receipt_json").toString(),MarketUseClient.Receipt.class),Boolean.TRUE.equals(r.get("completion_acked")));}catch(java.io.IOException e){throw MarketUseClient.invalid();}}
    public JsonNode proof(String purpose,UUID operation,String generation){
        if(!"STOCK_ANALYSIS".equals(purpose))throw MarketUseClient.unavailable();long gen=MarketLifecycleContract.generation(generation);
        return tx.execute(s->{var state=analyses.executionTerminal(operation,gen,clock.instant());var b=read(operation,gen);if(b==null||b.receipt()==null)throw MarketUseClient.unavailable();var result=((ObjectNode)state).deepCopy();result.put("owner","prediction").put("purpose","STOCK_ANALYSIS").put("ownerOperationId",operation.toString()).put("generation",generation);result.set("versionIds",json.valueToTree(b.receipt().versionIds()));return result;});
    }
    /** Best-effort notification is after business commit; failure preserves both local terminal and pending receipt. */
    public void notifyCompleted(UUID operation,long generation){if(!enabled)return;try{var b=read(operation,generation);if(b==null||b.receipt()==null||b.acked())return;proof("STOCK_ANALYSIS",operation,Long.toString(generation));markChecked(operation,generation);var released=client.complete(b.request(),b.receipt());if(released==null||!"RELEASED".equals(released.state())||!b.receipt().bundleId().equals(released.bundleId())||!b.receipt().versionIds().equals(released.versionIds()))throw MarketUseClient.invalid();jdbc.update("update stock_analysis_market_use set completion_acked=true where operation_id=? and execution_generation=? and bundle_id=?",operation.toString(),generation,b.receipt().bundleId());}catch(RuntimeException ignored){/* Durable pending row remains for bounded recovery. */}}
    public void recoverOne(UUID operation,long generation){if(!enabled)return;try{markChecked(operation,generation);var b=read(operation,generation);if(b==null||b.acked()||!b.started())return;if(b.receipt()==null){var found=client.find(b.request());if(found.isEmpty())return;attach(operation,generation,found.get());}notifyCompleted(operation,generation);}catch(RuntimeException ignored){/* Unknown receipt or proof is never permission to release or re-register. */}}
    public void recoverBatch(){if(!enabled)return;var rows=jdbc.queryForList("select operation_id,execution_generation from stock_analysis_market_use where registration_started=true and completion_acked=false order by last_checked_at,operation_id limit 25");for(var r:rows)recoverOne(UUID.fromString(r.get("operation_id").toString()),((Number)r.get("execution_generation")).longValue());}
    private void markChecked(UUID operation,long generation){jdbc.update("update stock_analysis_market_use set last_checked_at=greatest(?,timestampadd(microsecond,1000,last_checked_at)) where operation_id=? and execution_generation=?",ts(clock.instant()),operation.toString(),generation);}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(java.io.IOException e){throw MarketUseClient.invalid();}}
}
