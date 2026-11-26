package com.aistock.market.quotation;

import com.aistock.common.BusinessException;
import com.aistock.market.instrument.InstrumentQueryService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockValuationStore {
    public record Sample(LocalDate tradeDate, String peTtm, String pbMrq) {}
    public record Target(String instrumentId, String versionId, String provider, LocalDate tradeDate, BigDecimal close) {}
    public record View(String state, String instrumentId, String versionId, LocalDate tradeDate, String provider,
                       String peTtm, String pbMrq) {}

    private final JdbcTemplate jdbc;
    private final InstrumentQueryService instruments;

    public StockValuationStore(JdbcTemplate jdbc, InstrumentQueryService instruments) {
        this.jdbc = jdbc;
        this.instruments = instruments;
    }

    public Target target(String id, String versionId) {
        instruments.get(id);
        String version;
        try {
            version = UUID.fromString(versionId).toString();
            if (!version.equalsIgnoreCase(versionId)) throw InstrumentQueryService.invalid();
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw InstrumentQueryService.invalid();
        }
        return jdbc.query("""
                select v.provider,b.trade_date,b.close_price
                from market_data_version v
                left join market_daily_bar b on b.version_id=v.version_id and b.trade_status=1
                where v.version_id=? and v.instrument_id=? and v.price_basis='RAW' and v.status='AVAILABLE'
                order by b.trade_date desc limit 1
                """, (row, n) -> new Target(id, version, row.getString(1), row.getObject(2, LocalDate.class), row.getBigDecimal(3)),
                version, id).stream().findFirst().orElseThrow(() ->
                new BusinessException("RESOURCE_NOT_FOUND", "未找到该股票的日线版本", HttpStatus.NOT_FOUND));
    }

    public View read(String id, String version) {
        var target = target(id, version);
        var absent = new View("NO_DATA", id, target.versionId(), target.tradeDate(), null, null, null);
        if (target.tradeDate() == null) return absent;
        return jdbc.query("""
                select pe_ttm,pb_mrq from stock_valuation_snapshot
                where instrument_id=? and provider=? and trade_date=?
                """, (row, n) -> new View("AVAILABLE", id, target.versionId(), target.tradeDate(), target.provider(),
                decimal(row.getBigDecimal(1)), decimal(row.getBigDecimal(2))),
                id, target.provider(), target.tradeDate()).stream().findFirst().orElse(absent);
    }

    @Transactional(rollbackFor = Exception.class, timeout = 2)
    public boolean save(Target target, Sample sample, Instant fetchedAt) {
        if (!"baostock".equals(target.provider()) || target.tradeDate() == null || sample == null
                || !target.tradeDate().equals(sample.tradeDate()) || fetchedAt == null) return false;
        BigDecimal pe = ratio(sample.peTtm());
        BigDecimal pb = ratio(sample.pbMrq());
        if (pe == null && pb == null) return false;
        jdbc.update("""
                insert into stock_valuation_snapshot(instrument_id,provider,trade_date,evidence_version_id,pe_ttm,pb_mrq,fetched_at)
                values(?,?,?,?,?,?,?) on duplicate key update
                evidence_version_id=if(values(fetched_at)>=fetched_at,values(evidence_version_id),evidence_version_id),
                pe_ttm=if(values(fetched_at)>=fetched_at,values(pe_ttm),pe_ttm),
                pb_mrq=if(values(fetched_at)>=fetched_at,values(pb_mrq),pb_mrq),
                fetched_at=greatest(fetched_at,values(fetched_at))
                """, target.instrumentId(), target.provider(), target.tradeDate(), target.versionId(), pe, pb, Timestamp.from(fetchedAt));
        return true;
    }

    private static BigDecimal ratio(String value) {
        if (value == null || !value.matches("-?[0-9]{1,12}(\\.[0-9]{1,8})?")) return null;
        BigDecimal number = new BigDecimal(value);
        return number.signum() == 0 ? null : number;
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
