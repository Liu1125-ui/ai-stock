package com.aistock.market.comparison;

import com.aistock.common.BusinessException;
import com.aistock.market.version.MarketVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.aistock.market.comparison.ComparisonEvidenceRecords.*;

final class ComparisonEvidenceRepository {
    private final MarketVersionRepository market;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final Clock clock;
    ComparisonEvidenceRepository(MarketVersionRepository market,ObjectMapper mapper,Clock clock) {
        this.market=market;this.jdbc=market.jdbc();this.mapper=mapper;this.clock=clock;
        tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
    }
    Publication publish(Candidate c,String requestHash,ComparisonEvidenceValidator validator) {
        return publish(c,requestHash,validator,()->{});
    }
    Publication publish(Candidate c,String requestHash,ComparisonEvidenceValidator validator,Runnable beforeReturn) {return com.aistock.market.marketlifecycle.MarketEvidenceGate.local(()->{com.aistock.market.marketlifecycle.MarketEvidenceGate.raw(c.rawVersionId().toString());
        return tx.execute(status -> {
            jdbc.update("INSERT INTO comparison_evidence_operation(operation_id,request_hash,created_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE operation_id=operation_id",c.operationId(),requestHash,Timestamp.from(clock.instant()));
            var op=jdbc.queryForMap("SELECT * FROM comparison_evidence_operation WHERE operation_id=? FOR UPDATE",c.operationId());
            if(!requestHash.equals(op.get("request_hash")))throw error("COMPARISON_OPERATION_CONFLICT",HttpStatus.CONFLICT);
            if(op.get("evidence_id")!=null) {
                var e=version(UUID.fromString((String)op.get("evidence_id")));
                beforeReturn.run();return new Publication(e.evidenceId(),e.contentHash(),true);
            }
            var rows=jdbc.queryForList("SELECT * FROM market_data_version WHERE version_id=? FOR SHARE",c.rawVersionId().toString());
            if(rows.isEmpty())throw error("RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND);
            var raw=rows.get(0);
            if(!c.instrumentId().equals(raw.get("instrument_id"))||!"baostock".equals(raw.get("provider"))
                ||!"RAW".equals(raw.get("price_basis"))||!"AVAILABLE".equals(raw.get("status")))
                throw ComparisonEvidenceValidator.invalid("COMPARISON_RAW_INCOMPATIBLE");
            com.aistock.market.marketlifecycle.MarketEvidenceGate.publishable(jdbc,c.rawVersionId().toString());var v=validator.validate(c,market.bars(c.rawVersionId().toString()));
            var supplied=new HashMap<java.time.LocalDate,Boolean>();
            c.calendar().forEach(d -> supplied.put(d.date(),d.open()));
            if(market.calendar(c.rawVersionId().toString()).stream().anyMatch(d -> supplied.containsKey(d.date())&&supplied.get(d.date())!=d.open()))
                throw ComparisonEvidenceValidator.invalid("COMPARISON_CALENDAR_MISMATCH");
            var id=UUID.randomUUID();
            var evidence=new Evidence(id,c.instrumentId(),c.rawVersionId(),(String)raw.get("content_hash"),c.provider(),
                "BACKWARD",c.from(),c.to(),v.calendarHash(),v.contentHash(),c.sourcePayloadHash(),c.fetchedAt(),v.days(),List.copyOf(c.calendar()));
            jdbc.update("INSERT INTO comparison_evidence_version(evidence_id,instrument_id,raw_version_id,content_hash,evidence_json,created_at) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE evidence_id=evidence_id",
                id.toString(),c.instrumentId(),c.rawVersionId().toString(),v.contentHash(),json(evidence),Timestamp.from(clock.instant()));
            var saved=jdbc.queryForObject("SELECT evidence_id FROM comparison_evidence_version WHERE content_hash=? FOR UPDATE",String.class,v.contentHash());
            jdbc.update("UPDATE comparison_evidence_operation SET evidence_id=? WHERE operation_id=?",saved,c.operationId());
            beforeReturn.run();return new Publication(UUID.fromString(saved),v.contentHash(),false);
        });
    });}
    ComparisonEvidenceOptions.Options options(List<String> ids,String period) {
        ComparisonEvidenceOptions.validate(ids,period);
        var read=new TransactionTemplate(Objects.requireNonNull(tx.getTransactionManager()));
        read.setReadOnly(true);read.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);
        return read.execute(status->{
            var candidates=new LinkedHashMap<String,List<Evidence>>();var heads=new HashMap<String,java.time.LocalDate>();
            for(String id:ids){
                var active=jdbc.queryForList("SELECT instrument_id FROM instrument WHERE instrument_id=? AND listing_status='ACTIVE' AND demo_only=false",String.class,id);
                if(active.isEmpty()){candidates.put(id,List.of());continue;}
                var latest=jdbc.queryForList("SELECT v.evidence_id FROM comparison_evidence_version v "+activationJoin()+" WHERE v.instrument_id=? ORDER BY JSON_UNQUOTE(JSON_EXTRACT(v.evidence_json,'$.to')) DESC,"+activationOrder()+" LIMIT 1",String.class,id);
                candidates.put(id,latest.isEmpty()?List.of():List.of(version(UUID.fromString(latest.get(0)))));
                var dates=jdbc.queryForList("SELECT v.coverage_end FROM instrument_data_head h JOIN market_data_version v ON v.version_id=h.version_id WHERE h.instrument_id=? AND h.provider='baostock' AND h.price_basis='RAW' AND v.status='AVAILABLE'",java.sql.Date.class,id);
                if(!dates.isEmpty())heads.put(id,dates.get(0).toLocalDate());
            }
            var today=clock.instant().atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate();
            var first=ComparisonEvidenceOptions.select(ids,period,candidates,heads,today);
            if(first.to()==null)return first;
            for(String id:ids){if(candidates.get(id).isEmpty())continue;
                var chosen=latestCovering(id,first.from(),first.to());
                if(chosen!=null){var latest=candidates.get(id).get(0);candidates.put(id,chosen.evidenceId().equals(latest.evidenceId())?List.of(chosen):List.of(chosen,latest));}
            }
            return ComparisonEvidenceOptions.select(ids,period,candidates,heads,today);
        });
    }
    // Shared by public comparison selection and update readiness.
    Evidence latestCovering(String id,java.time.LocalDate from,java.time.LocalDate to) {
        var ids=jdbc.queryForList("SELECT v.evidence_id FROM comparison_evidence_version v "+activationJoin()+
            " WHERE v.instrument_id=? AND JSON_UNQUOTE(JSON_EXTRACT(v.evidence_json,'$.from'))<=? AND JSON_UNQUOTE(JSON_EXTRACT(v.evidence_json,'$.to'))>=? ORDER BY "+activationOrder()+" LIMIT 1",
            String.class,id,from.toString(),to.toString());
        return ids.isEmpty()?null:version(UUID.fromString(ids.get(0)));
    }
    private static String activationJoin(){return "LEFT JOIN (SELECT evidence_id,MAX(publication_sequence) seq FROM comparison_evidence_operation WHERE evidence_id IS NOT NULL GROUP BY evidence_id) a ON a.evidence_id=v.evidence_id";}
    private static String activationOrder(){return "COALESCE(a.seq,0) DESC,v.created_at DESC,v.evidence_id DESC";}
    Evidence version(UUID id) {
        if(id==null)throw error("RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND);
        var rows=jdbc.queryForList("SELECT evidence_json FROM comparison_evidence_version WHERE evidence_id=?",String.class,id.toString());
        if(rows.isEmpty())throw error("RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND);
        try{var result=mapper.readValue(rows.get(0),Evidence.class);com.aistock.market.marketlifecycle.MarketEvidenceGate.evidence(result.evidenceId().toString(),result.instrumentId(),result.rawVersionId().toString());return result;}
        catch(com.aistock.common.BusinessException e){throw e;}catch(Exception e){throw error("COMPARISON_RECORD_INVALID",HttpStatus.INTERNAL_SERVER_ERROR);}
    }
    private String json(Object value) {
        try{return mapper.writeValueAsString(value);}
        catch(Exception e){throw error("COMPARISON_RECORD_INVALID",HttpStatus.INTERNAL_SERVER_ERROR);}
    }
    static BusinessException error(String code,HttpStatus status){
        return new BusinessException(code,"股票对比历史证据暂不可用",status);
    }
}