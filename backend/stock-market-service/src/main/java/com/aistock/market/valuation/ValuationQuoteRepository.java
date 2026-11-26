package com.aistock.market.valuation;
import java.time.*;import java.util.*;import java.math.BigDecimal;import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
@Repository public class ValuationQuoteRepository {
 public record Head(UUID versionId,String contentHash){}
 public record Price(LocalDate date,BigDecimal close){}
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;
 public ValuationQuoteRepository(JdbcTemplate jdbc){this.jdbc=jdbc;tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));tx.setTimeout(5);tx.setReadOnly(true);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);}
 public <T>T read(Supplier<T> work){return tx.execute(s->work.get());}
 public Optional<Head> head(String id){var pinned=com.aistock.market.marketlifecycle.MarketEvidenceGate.pinned(id);if(pinned!=null)return jdbc.query("select version_id,content_hash from market_data_version where version_id=? and instrument_id=? and provider='baostock' and price_basis='RAW' and status='AVAILABLE'",(rs,n)->new Head(UUID.fromString(rs.getString(1)),rs.getString(2)),pinned,id).stream().findFirst();return jdbc.query("select v.version_id,v.content_hash from instrument_data_head h join market_data_version v on v.version_id=h.version_id and v.instrument_id=h.instrument_id and v.provider=h.provider and v.price_basis=h.price_basis where h.instrument_id=? and h.provider='baostock' and h.price_basis='RAW' and v.status='AVAILABLE'",(rs,n)->new Head(UUID.fromString(rs.getString(1)),rs.getString(2)),id).stream().findFirst();}
 public Optional<Price> price(UUID version,LocalDate cutoff){return jdbc.query("select trade_date,close_price from market_daily_bar where version_id=? and trade_date<=? and trade_status=1 and close_price>0 order by trade_date desc limit 1",(rs,n)->new Price(rs.getDate(1).toLocalDate(),rs.getBigDecimal(2)),version.toString(),cutoff).stream().findFirst();}
 public boolean retained(UUID version,LocalDate cutoff){return jdbc.query("select trade_status from market_daily_bar where version_id=? and trade_date<=? order by trade_date desc limit 1",(rs,n)->rs.getInt(1)==0,version.toString(),cutoff).stream().findFirst().orElse(false);}
}
