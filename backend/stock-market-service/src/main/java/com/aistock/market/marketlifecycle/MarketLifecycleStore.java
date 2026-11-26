package com.aistock.market.marketlifecycle;

import com.aistock.common.BusinessException;
import com.aistock.common.marketlifecycle.*;
import com.aistock.common.marketlifecycle.MarketUseRecords.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared lock order and safe SQL boundaries. No remote IO is allowed inside these transactions. */
final class MarketLifecycleStore {
    final JdbcTemplate jdbc;
    final Clock clock;
    private final TransactionTemplate tx;
    private static final ObjectMapper JSON=new ObjectMapper();
    record Meta(String id,String stock) {}
    MarketLifecycleStore(JdbcTemplate jdbc,Clock clock) {
        this.jdbc=jdbc;this.clock=clock;
        var manager=new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource()));
        manager.setValidateExistingTransaction(true);
        tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setTimeout(8);
    }
    <T> T write(Supplier<T> operation) {return safe(()->tx.execute(s->operation.get()));}
    <T> T safe(Supplier<T> operation) {
        try{return operation.get();}catch(DataAccessException|TransactionException e){throw unavailable();}
    }
    Timestamp now(){return Timestamp.from(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));}
    static String useGuardIdentity(UseIdentity identity){return identity.owner()+"|"+identity.ownerOperationId()+"|"+identity.generation();}
    static String guardKey(String kind,String identity){return digest(kind+"|"+identity);}
    void guard(String kind,String identity) {
        var key=guardKey(kind,identity);
        jdbc.update("insert into market_version_command_guard(guard_key,guard_type,created_at) values(?,?,?) on duplicate key update guard_key=guard_key",key,kind,now());
        var actual=jdbc.queryForObject("select guard_type from market_version_command_guard where guard_key=? for update",String.class,key);
        if(!kind.equals(actual))throw unavailable();
    }
    Meta meta(String version,boolean lock) {
        var rows=jdbc.query("select version_id,instrument_id,provider,price_basis,status from market_data_version where version_id=?"+(lock?" for update":""),
            (r,n)->{if(!"baostock".equals(r.getString("provider"))||!"RAW".equals(r.getString("price_basis"))||!"AVAILABLE".equals(r.getString("status")))throw blocked();return new Meta(r.getString(1),r.getString(2));},version);
        if(rows.size()!=1)throw notFound();var meta=rows.get(0);if(!Integer.valueOf(1).equals(jdbc.queryForObject("select count(*) from instrument where instrument_id=? and demo_only=false",Integer.class,meta.stock())))throw notFound();return meta;
    }
    Map<String,String> heads(Collection<String> stocks) {
        var result=new TreeMap<String,String>();
        for(var stock:new TreeSet<>(stocks)) {
            var rows=jdbc.query("select version_id from instrument_data_head where instrument_id=? and provider='baostock' and price_basis='RAW' for update",(r,n)->r.getString(1),stock);
            // No phantom/missing-head assumption: only already-initialized instruments are supported in this slice.
            if(rows.size()!=1||rows.get(0)==null)throw notReady();result.put(stock,rows.get(0));
        }
        return result;
    }
    void lockVersions(List<String> versions) {for(var id:versions)meta(id,true);}
    State state(String version) {
        var rows=jdbc.query("select use_state,revision,cycle_id from market_version_lifecycle where version_id=? for update",(r,n)->{
            try {var cycle=r.getString(3);return new State(UseState.valueOf(r.getString(1)),r.getLong(2),cycle==null?null:UUID.fromString(cycle));}
            catch(IllegalArgumentException e){throw unavailable();}
        },version);
        if(rows.size()!=1)throw notReady();return rows.get(0);
    }
    long active(String version) {return Objects.requireNonNull(jdbc.queryForObject("select count(*) from market_version_use where version_id=? and state='ACTIVE'",Long.class,version));}
    void save(String version,State state) {
        jdbc.update("update market_version_lifecycle set use_state=?,revision=?,cycle_id=?,updated_at=? where version_id=?",state.useState().name(),state.revision(),state.cycleId()==null?null:state.cycleId().toString(),now(),version);
    }
    static List<String> versions(String json) {
        try {var node=JSON.readTree(json);if(!node.isArray())throw unavailable();var values=new ArrayList<String>();for(var n:node){if(!n.isTextual())throw unavailable();values.add(n.asText());}return MarketLifecycleContract.versionSet(values);}
        catch(Exception e){throw unavailable();}
    }
    static String json(Object value){try{return JSON.writeValueAsString(value);}catch(Exception e){throw unavailable();}}
    static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw unavailable();}}
    static Instant time(ResultSet r,String field)throws SQLException {var value=r.getTimestamp(field);return value==null?null:value.toInstant();}
    static BusinessException conflict(String code){return new BusinessException(code,"版本状态或原请求已改变，请核对原操作",HttpStatus.CONFLICT);}
    static BusinessException blocked(){return new BusinessException("MARKET_VERSION_USE_BLOCKED","该行情版本暂不接受新使用",HttpStatus.CONFLICT);}
    static BusinessException notFound(){return new BusinessException("MARKET_VERSION_RESOURCE_NOT_FOUND","未找到所属行情版本或原操作",HttpStatus.NOT_FOUND);}
    static BusinessException notReady(){return new BusinessException("MARKET_LIFECYCLE_NOT_READY","版本保护尚未就绪，请稍后核对",HttpStatus.SERVICE_UNAVAILABLE);}
    static BusinessException unavailable(){return new BusinessException("MARKET_LIFECYCLE_UNAVAILABLE","版本操作暂不可用，已有数据保留",HttpStatus.SERVICE_UNAVAILABLE);}
}
