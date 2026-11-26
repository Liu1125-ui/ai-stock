package com.aistock.market.refresh;

import com.aistock.common.BusinessException;
import com.aistock.common.adminrefresh.AdminMarketRefreshInput;
import com.aistock.common.adminrefresh.AdminMarketRefreshRecords.Context;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/** Read-only current-head facts; never reconciles receipts or calls a collector. */
@Repository
public class AdminRefreshContextRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate read;
    public AdminRefreshContextRepository(JdbcTemplate jdbc) {
        this.jdbc=jdbc;
        read=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);
    }
    public Context find(String instrumentId,Instant queriedAt) {
        AdminMarketRefreshInput.instrument(instrumentId);Objects.requireNonNull(queriedAt);
        try {
            return read.execute(tx->{
                var rows=jdbc.query("""
                    select i.instrument_id,h.version_id as head_version_id,h.revision,
                           v.version_id,v.status,v.coverage_start,v.coverage_end
                    from instrument i
                    left join instrument_data_head h on h.instrument_id=i.instrument_id
                        and h.provider='baostock' and h.price_basis='RAW'
                    left join market_data_version v on v.version_id=h.version_id
                        and v.instrument_id=h.instrument_id and v.provider=h.provider and v.price_basis=h.price_basis
                    where i.instrument_id=? and i.demo_only=false
                    """,(rs,n)->{
                        var version=rs.getString("version_id");var revision=rs.getObject("revision",Long.class);
                        var start=rs.getObject("coverage_start",LocalDate.class);var end=rs.getObject("coverage_end",LocalDate.class);
                        if(version==null||revision==null||revision<0||!"AVAILABLE".equals(rs.getString("status"))
                            ||start==null||end==null||start.isBefore(LocalDate.of(1990,1,1))||end.isBefore(start))
                            return denied(instrumentId,queriedAt);
                        return new Context(instrumentId,"baostock","RAW",true,null,version,Long.toString(revision),start,end,queriedAt);
                    },instrumentId);
                if(rows.size()!=1)throw new BusinessException("MARKET_ADMIN_RESOURCE_NOT_FOUND","未找到可管理的真实股票",HttpStatus.NOT_FOUND);
                return rows.get(0);
            });
        }catch(DataAccessException|TransactionException e){throw new BusinessException("MARKET_ADMIN_UNAVAILABLE","行情管理暂不可用，请稍后重试",HttpStatus.SERVICE_UNAVAILABLE);}
    }
    private Context denied(String id,Instant at){return new Context(id,"baostock","RAW",false,"REAL_MARKET_VERSION_REQUIRED",null,null,null,null,at);}
}