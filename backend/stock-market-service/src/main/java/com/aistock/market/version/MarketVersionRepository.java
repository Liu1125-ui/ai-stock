package com.aistock.market.version;

import com.aistock.common.BusinessException;
import com.aistock.market.ingestion.MarketCandidate;
import com.aistock.market.ingestion.Publication;
import com.aistock.market.quotation.RawBar;
import com.aistock.market.quotation.TradingDay;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class MarketVersionRepository {
    private final JdbcTemplate jdbc;private final TransactionTemplate transaction;
    public MarketVersionRepository(JdbcTemplate jdbc) {
        this.jdbc=jdbc;this.transaction=new TransactionTemplate(new DataSourceTransactionManager(java.util.Objects.requireNonNull(jdbc.getDataSource())));
    }
    public Publication publish(MarketCandidate c,String requestHash,String contentHash) {
        return publish(c,requestHash,contentHash,null,java.time.Clock.systemUTC());
    }
    public Publication publish(MarketCandidate c,String requestHash,String contentHash,Instant deadline,java.time.Clock clock) {
        return transaction.execute(status->{
            checkDeadline(deadline,clock);
            lockReceipt(c,deadline);
            var now=Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO market_ingestion_operation(operation_id,request_hash,instrument_id,provider,state,created_at) VALUES (?,?,?,?,'PENDING',?) ON DUPLICATE KEY UPDATE operation_id=operation_id",
                c.operationId(),requestHash,c.instrumentId(),c.provider(),now);
            var operation=jdbc.queryForMap("SELECT * FROM market_ingestion_operation WHERE operation_id=? FOR UPDATE",c.operationId());
            if(!requestHash.equals(operation.get("request_hash"))) throw conflict("MARKET_OPERATION_CONFLICT");
            if("AVAILABLE".equals(operation.get("state"))) return new Publication(c.operationId(),(String)operation.get("version_id"),"AVAILABLE",true,((Number)operation.get("head_revision")).longValue());
            if("REJECTED".equals(operation.get("state"))) throw conflict("MARKET_OPERATION_REJECTED");
            boolean verifiedMetadata=c.displayNameVerified() || !"UNKNOWN".equals(c.listingStatus());
            // Price collection alone is not a fresh verification of security metadata.
            String newer="(metadata_checked_at IS NULL OR VALUES(metadata_checked_at)>metadata_checked_at)";
            jdbc.update("INSERT INTO instrument(instrument_id,ticker,display_name,demo_only,listing_status,name_verified,metadata_source,metadata_checked_at) VALUES (?,?,?,false,?,?,?,?) ON DUPLICATE KEY UPDATE "
                +"display_name=IF(VALUES(name_verified) AND "+newer+",VALUES(display_name),display_name), "
                +"name_verified=IF(VALUES(name_verified) AND "+newer+",true,name_verified), "
                +"listing_status=IF(VALUES(listing_status)<>'UNKNOWN' AND "+newer+",VALUES(listing_status),listing_status), "
                +"metadata_source=IF(VALUES(metadata_checked_at) IS NOT NULL AND "+newer+",VALUES(metadata_source),metadata_source), "
                +"metadata_checked_at=COALESCE(GREATEST(metadata_checked_at,VALUES(metadata_checked_at)),metadata_checked_at,VALUES(metadata_checked_at))",
                c.instrumentId(),c.ticker(),c.displayName(),c.listingStatus(),c.displayNameVerified(),
                verifiedMetadata?c.provider():null,verifiedMetadata?Timestamp.from(c.fetchedAt()):null);
            jdbc.update("INSERT INTO instrument_data_head(instrument_id,provider,price_basis,revision) VALUES (?,?,?,0) ON DUPLICATE KEY UPDATE instrument_id=instrument_id",c.instrumentId(),c.provider(),c.priceBasis());
            var head=jdbc.queryForMap("SELECT * FROM instrument_data_head WHERE instrument_id=? AND provider=? AND price_basis=? FOR UPDATE",c.instrumentId(),c.provider(),c.priceBasis());
            long revision=((Number)head.get("revision")).longValue();
            if(c.expectedRevision()!=null && c.expectedRevision()!=revision) throw conflict("MARKET_REVISION_CONFLICT");
            var end=c.bars().get(c.bars().size()-1).date();
            if(head.get("version_id")!=null) {
                LocalDate previousEnd=jdbc.queryForObject("SELECT coverage_end FROM market_data_version WHERE version_id=?",(rs,n)->rs.getDate(1).toLocalDate(),head.get("version_id"));
                if(end.isBefore(previousEnd)) throw conflict("MARKET_COVERAGE_REGRESSION");
            }
            var existing=jdbc.queryForList("SELECT version_id FROM market_data_version WHERE instrument_id=? AND provider=? AND price_basis=? AND content_hash=? AND status='AVAILABLE'",String.class,c.instrumentId(),c.provider(),c.priceBasis(),contentHash);
            String version;
            if(existing.isEmpty()) {
                version=UUID.randomUUID().toString();
                LocalDate calendarStart=c.calendar().isEmpty()?null:c.calendar().get(0).date();
                LocalDate calendarEnd=c.calendar().isEmpty()?null:c.calendar().get(c.calendar().size()-1).date();
                jdbc.update("INSERT INTO market_data_version(version_id,instrument_id,provider,price_basis,status,quality,content_hash,source_payload_hash,source_version_id,fetched_at,published_at,coverage_start,coverage_end,calendar_start,calendar_end) VALUES (?,?,?,?,'STAGING',?,?,?,?,?,?,?,?,?,?)",
                    version,c.instrumentId(),c.provider(),c.priceBasis(),c.calendar().isEmpty()?"CALENDAR_UNKNOWN":"ROW_VALIDATED",contentHash,c.sourcePayloadHash(),c.sourceVersionId(),Timestamp.from(c.fetchedAt()),now,c.bars().get(0).date(),end,calendarStart,calendarEnd);
                for(var row:c.bars()) jdbc.update("INSERT INTO market_daily_bar VALUES (?,?,?,?,?,?,?,?,?,?)",version,row.date(),row.open(),row.high(),row.low(),row.close(),row.volumeShares(),row.amountYuan(),row.tradeStatus(),row.sourceAdjustmentRatio());
                for(var day:c.calendar()) jdbc.update("INSERT INTO market_calendar_day VALUES (?,?,?)",version,day.date(),day.open());
                jdbc.update("UPDATE market_data_version SET status='AVAILABLE' WHERE version_id=? AND status='STAGING'",version);
                if("baostock".equals(c.provider())&&"RAW".equals(c.priceBasis()))jdbc.update("INSERT INTO market_version_lifecycle(version_id,use_state,revision,updated_at) VALUES (?,'ENABLED',0,?)",version,now);
            } else {
                version=existing.get(0);
                if("baostock".equals(c.provider())&&"RAW".equals(c.priceBasis())) {
                    jdbc.queryForObject("SELECT version_id FROM market_data_version WHERE version_id=? FOR UPDATE",String.class,version);
                    var states=jdbc.queryForList("SELECT use_state FROM market_version_lifecycle WHERE version_id=? FOR UPDATE",String.class,version);
                    if(states.size()!=1)throw new BusinessException("MARKET_LIFECYCLE_NOT_READY","行情版本使用状态缺失，请核对后重试",HttpStatus.CONFLICT);
                    if(!"ENABLED".equals(states.get(0)))throw conflict("MARKET_VERSION_USE_BLOCKED");
                }
            }
            var sourceCode=c.provider().equals("baostock")?c.ticker().substring(0,2).toLowerCase()+"."+c.ticker().substring(2):c.ticker().substring(2)+"."+c.ticker().substring(0,2);
            jdbc.update("INSERT INTO source_code_map VALUES (?,?,?) ON DUPLICATE KEY UPDATE instrument_id=instrument_id",c.provider(),sourceCode,c.instrumentId());
            checkDeadline(deadline,clock);
            if(!version.equals(head.get("version_id"))) {
                revision++;
                if("baostock".equals(c.provider()) && "RAW".equals(c.priceBasis())) {
                    long sequence=java.util.Objects.requireNonNull(jdbc.queryForObject("select last_id from market_revision_sequence where singleton_id=1 for update",Long.class));
                    long event=Math.addExact(sequence,1);
                    jdbc.update("insert into market_revision_event(event_id,instrument_id,raw_version_id,created_at) values(?,?,?,?)",event,c.instrumentId(),version,now);
                    jdbc.update("update market_revision_sequence set last_id=? where singleton_id=1",event);
                }
            }
            jdbc.update("UPDATE instrument_data_head SET version_id=?,revision=?,checked_at=?,last_error_code=NULL WHERE instrument_id=? AND provider=? AND price_basis=?",version,revision,now,c.instrumentId(),c.provider(),c.priceBasis());
            jdbc.update("UPDATE market_ingestion_operation SET state='AVAILABLE',version_id=?,head_revision=?,finished_at=? WHERE operation_id=?",version,revision,now,c.operationId());
            if(deadline!=null) jdbc.update("UPDATE market_refresh_receipt SET state='AVAILABLE',version_id=?,error_code=NULL,finished_at=? WHERE operation_id=? AND state='RUNNING'",version,Timestamp.from(clock.instant()),c.operationId());
            checkDeadline(deadline,clock);
            return new Publication(c.operationId(),version,"AVAILABLE",false,revision);
        });
    }
    public void reject(MarketCandidate c,String requestHash,String errorCode) {
        if(c==null || c.operationId()==null || !c.operationId().matches("[A-Za-z0-9_-]{1,128}")) return;
        transaction.executeWithoutResult(status->{
            var now=Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO market_ingestion_operation(operation_id,request_hash,instrument_id,provider,state,error_code,created_at,finished_at) VALUES (?,?,?,?,'REJECTED',?,?,?) ON DUPLICATE KEY UPDATE operation_id=operation_id",c.operationId(),requestHash,safeInstrument(c.instrumentId()),safeProvider(c.provider()),errorCode,now,now);
            if(safeInstrument(c.instrumentId())!=null && safeProvider(c.provider())!=null) jdbc.update("UPDATE instrument_data_head SET last_error_code=? WHERE instrument_id=? AND provider=? AND price_basis='RAW'",errorCode,c.instrumentId(),c.provider());
        });
    }
    private void lockReceipt(MarketCandidate c,Instant deadline) {
        if(deadline==null)return;
        var rows=jdbc.queryForList("SELECT instrument_id,lease_until,state FROM market_refresh_receipt WHERE operation_id=? FOR UPDATE",c.operationId());
        if(rows.isEmpty())throw conflict("REFRESH_OPERATION_CONFLICT");
        var row=rows.get(0);
        var stored=((java.time.LocalDateTime)row.get("lease_until")).toInstant(java.time.ZoneOffset.UTC);
        if(!c.instrumentId().equals(row.get("instrument_id")) || !"RUNNING".equals(row.get("state")) ||
            !deadline.truncatedTo(java.time.temporal.ChronoUnit.MILLIS).equals(stored))throw conflict("REFRESH_OPERATION_CONFLICT");
    }
    private void checkDeadline(Instant deadline,java.time.Clock clock){if(deadline!=null && !clock.instant().isBefore(deadline))throw new BusinessException("ATTEMPT_EXPIRED","执行期限已过，已有行情保留",HttpStatus.CONFLICT);}
    public JdbcTemplate jdbc(){return jdbc;}
    public List<RawBar> bars(String version) {
        return jdbc.query("SELECT * FROM market_daily_bar WHERE version_id=? ORDER BY trade_date",(rs,n)->new RawBar(rs.getDate("trade_date").toLocalDate(),rs.getBigDecimal("open_price"),rs.getBigDecimal("high_price"),rs.getBigDecimal("low_price"),rs.getBigDecimal("close_price"),rs.getBigDecimal("volume_shares"),rs.getBigDecimal("amount_yuan"),rs.getInt("trade_status"),rs.getBigDecimal("source_adjustment_ratio")),version);
    }
    public List<TradingDay> calendar(String version) {
        return jdbc.query("SELECT * FROM market_calendar_day WHERE version_id=? ORDER BY calendar_date",(rs,n)->new TradingDay(rs.getDate("calendar_date").toLocalDate(),rs.getBoolean("is_open")),version);
    }
    private String safeInstrument(String id) { return id!=null && id.matches("(SH|SZ)[0-9]{6}") ? id : null; }
    private String safeProvider(String provider) { return "baostock".equals(provider)||"tushare".equals(provider) ? provider : null; }
    private BusinessException conflict(String code) {return new BusinessException(code,"行情操作或版本状态已改变，请重新核对后提交",HttpStatus.CONFLICT);}
}
