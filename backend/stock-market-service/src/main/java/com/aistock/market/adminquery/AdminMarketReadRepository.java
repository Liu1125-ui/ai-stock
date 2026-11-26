package com.aistock.market.adminquery;

import static com.aistock.market.adminquery.AdminMarketRecords.*;
import com.aistock.common.BusinessException;
import com.aistock.common.adminmarket.AdminMarketQuery;
import com.aistock.common.adminmarket.AdminMarketQuery.*;
import java.sql.*;import java.time.*;import java.util.*;import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;import org.springframework.transaction.*;import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class AdminMarketReadRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate read;
    private final CoverageDiagnostics diagnostics=new CoverageDiagnostics();
    private final VersionCursor cursors=new VersionCursor();
    private static final String MATCH="v.version_id is not null and v.instrument_id=i.instrument_id and binary v.provider=binary h.provider and binary v.price_basis=binary h.price_basis";
    private static final String STATE="case when h.version_id is null then 'NO_VERSION' when not ("+MATCH+") then 'REFERENCE_ERROR' else v.status end";
    private static final String CURRENT="exists(select 1 from instrument_data_head h where h.instrument_id=v.instrument_id and h.provider=v.provider and h.price_basis=v.price_basis and h.version_id=v.version_id)";
    private static final String VERSION_COLUMNS="v.version_id,v.provider,v.price_basis,v.status,v.quality,v.coverage_start,v.coverage_end,v.fetched_at,v.published_at,"+CURRENT+" current_ref";
    public AdminMarketReadRepository(JdbcTemplate jdbc) {
        this.jdbc=jdbc;read=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);
    }
    public HeadPage heads(ListFilter f,Instant now) {
        if(f.page()<1||f.page()>AdminMarketQuery.MAX_PAGE||f.size()<1||f.size()>AdminMarketQuery.MAX_SIZE)throw AdminMarketQuery.invalid();
        return query(()->{
            var sql=new StringBuilder("select i.instrument_id,i.ticker,i.display_name,i.name_verified,i.listing_status,h.provider,h.price_basis,h.version_id head_id,h.revision,h.checked_at,h.last_error_code,v.status,v.quality,v.coverage_start,v.coverage_end,v.fetched_at,v.published_at,("+MATCH+") relation_ok,"+STATE+" head_state from instrument i left join instrument_data_head h on h.instrument_id=i.instrument_id");
            var args=new ArrayList<Object>();if(f.provider()!=null){sql.append(" and lower(h.provider)=?");args.add(f.provider());}
            sql.append(" left join market_data_version v on v.version_id=h.version_id where i.demo_only=false");
            if(f.q()!=null) {sql.append(" and (i.ticker like ? escape '!' or i.display_name like ? escape '!')");var term="%"+f.q().replace("!","!!").replace("%","!%").replace("_","!_")+"%";args.add(term);args.add(term);}
            if(f.headState()!=null){sql.append(" and ").append(STATE).append("=?");args.add(f.headState());}
            sql.append(" order by i.instrument_id,h.provider,h.price_basis limit ? offset ?");args.add(f.size()+1);args.add((f.page()-1)*f.size());
            var rows=jdbc.query(sql.toString(),(rs,n)->{
                boolean match=rs.getBoolean("relation_ok"),present=rs.getString("provider")!=null;
                return new HeadRow(rs.getString("instrument_id"),rs.getString("ticker"),rs.getString("display_name"),rs.getBoolean("name_verified"),rs.getString("listing_status"),rs.getString("provider"),rs.getString("price_basis"),present,rs.getString("head_state"),rs.getString("head_id"),present?Long.toString(rs.getLong("revision")):null,match?rs.getString("status"):null,match?rs.getString("quality"):null,match?date(rs,"coverage_start"):null,match?date(rs,"coverage_end"):null,instant(rs,"checked_at"),match?instant(rs,"fetched_at"):null,match?instant(rs,"published_at"):null,AdminMarketErrors.safe(rs.getString("last_error_code")));
            },args.toArray());
            return new HeadPage(now,DEFINITION,f,rows.stream().limit(f.size()).toList(),rows.size()>f.size());
        });
    }
    public VersionPage versions(String instrumentId,HistoryFilter f,Instant now) {
        if(f.size()<1||f.size()>AdminMarketQuery.MAX_SIZE)throw AdminMarketQuery.invalid();
        var cursor=f.cursor()==null?null:cursors.decode(f.cursor(),instrumentId,f.provider());
        return query(()->{
            requireInstrument(instrumentId);
            var sql=new StringBuilder("select "+VERSION_COLUMNS+" from market_data_version v where v.instrument_id=?");var args=new ArrayList<Object>();args.add(instrumentId);
            if(f.provider()!=null){sql.append(" and lower(v.provider)=?");args.add(f.provider());}
            if(cursor!=null){sql.append(" and (v.published_at<? or (v.published_at=? and v.version_id<?))");args.add(Timestamp.from(cursor.lastPublishedAt()));args.add(Timestamp.from(cursor.lastPublishedAt()));args.add(cursor.lastVersionId().toString());}
            sql.append(" order by v.published_at desc,v.version_id desc limit ?");args.add(f.size()+1);
            var rows=jdbc.query(sql.toString(),(rs,n)->version(rs),args.toArray());var items=rows.stream().limit(f.size()).toList();String next=null;
            if(rows.size()>f.size()){var last=items.get(items.size()-1);next=cursors.encode(new VersionCursor.Cursor(instrumentId,f.provider(),last.publishedAt(),UUID.fromString(last.versionId())));}
            var ingestionSql="select operation_id,state,version_id,error_code,created_at,finished_at from market_ingestion_operation where instrument_id=?"+(f.provider()==null?"":" and lower(provider)=?")+" order by created_at desc,operation_id desc limit 11";
            Object[] ingestionArgs=f.provider()==null?new Object[]{instrumentId}:new Object[]{instrumentId,f.provider()};
            var ingestions=jdbc.query(ingestionSql,(rs,n)->new IngestionSummary(rs.getString("operation_id"),rs.getString("state"),rs.getString("version_id"),AdminMarketErrors.safe(rs.getString("error_code")),instant(rs,"created_at"),instant(rs,"finished_at")),ingestionArgs);
            List<RefreshSummary> refreshes=f.provider()!=null&&!f.provider().equals("baostock")?List.of():jdbc.query("select operation_id,state,target_date,version_id,error_code,created_at,finished_at from market_refresh_receipt where instrument_id=? order by created_at desc,operation_id desc limit 11",(rs,n)->new RefreshSummary(rs.getString("operation_id"),rs.getString("state"),date(rs,"target_date"),rs.getString("version_id"),AdminMarketErrors.safe(rs.getString("error_code")),instant(rs,"created_at"),instant(rs,"finished_at")),instrumentId);
            return new VersionPage(now,DEFINITION,instrumentId,f.provider(),items,next,ingestions.stream().limit(10).toList(),refreshes.stream().limit(10).toList(),ingestions.size()>10,refreshes.size()>10);
        });
    }
    public VersionDetail detail(String instrumentId,UUID versionId,DetailFilter f,Instant now) {
        return query(()->{
            requireInstrument(instrumentId);
            var versions=jdbc.query("select "+VERSION_COLUMNS+",v.content_hash,v.source_payload_hash,(select h.revision from instrument_data_head h where h.instrument_id=v.instrument_id and h.provider=v.provider and h.price_basis=v.price_basis and h.version_id=v.version_id) head_revision from market_data_version v where v.instrument_id=? and v.version_id=?",(rs,n)->new VersionMetadata(rs.getString("version_id"),rs.getString("provider"),rs.getString("price_basis"),rs.getString("status"),rs.getString("quality"),date(rs,"coverage_start"),date(rs,"coverage_end"),instant(rs,"fetched_at"),instant(rs,"published_at"),rs.getBoolean("current_ref"),hash(rs.getString("content_hash")),hash(rs.getString("source_payload_hash")),rs.getObject("head_revision")==null?null:Long.toString(rs.getLong("head_revision"))),instrumentId,versionId.toString());
            if(versions.isEmpty())throw notFound();var v=versions.get(0);
            var bars=jdbc.query("select trade_date,open_price,high_price,low_price,close_price,volume_shares,amount_yuan,trade_status from market_daily_bar where version_id=? and trade_date>=? and trade_date<=? order by trade_date",(rs,n)->new DiagnosticBar(date(rs,"trade_date"),rs.getBigDecimal("open_price"),rs.getBigDecimal("high_price"),rs.getBigDecimal("low_price"),rs.getBigDecimal("close_price"),rs.getBigDecimal("volume_shares"),rs.getBigDecimal("amount_yuan"),rs.getInt("trade_status")),versionId.toString(),f.from(),f.to());
            var calendar=jdbc.query("select calendar_date,is_open from market_calendar_day where version_id=? and calendar_date>=? and calendar_date<=? order by calendar_date",(rs,n)->new CalendarDay(date(rs,"calendar_date"),rs.getBoolean("is_open")),versionId.toString(),f.from(),f.to());
            var d=diagnostics.calculate(f,new VersionBounds(v.coverageStart(),v.coverageEnd()),bars,calendar);
            return new VersionDetail(now,DEFINITION,instrumentId,v,new Window(f.from(),f.to(),AdminMarketQuery.ZONE.getId()),d);
        });
    }
    private void requireInstrument(String id) {if(id==null||!id.matches(AdminMarketQuery.INSTRUMENT))throw notFound();if(Objects.requireNonNull(jdbc.queryForObject("select count(*) from instrument where instrument_id=? and demo_only=false",Integer.class,id))!=1)throw notFound();}
    private <T> T query(Supplier<T> action) {
        try{return read.execute(status->action.get());}
        catch(DataAccessException|TransactionException|IllegalArgumentException e){throw new BusinessException("MARKET_ADMIN_UNAVAILABLE","行情管理数据暂不可用，请稍后重试",HttpStatus.SERVICE_UNAVAILABLE);}
    }
    private static VersionSummary version(ResultSet rs)throws SQLException {return new VersionSummary(rs.getString("version_id"),rs.getString("provider"),rs.getString("price_basis"),rs.getString("status"),rs.getString("quality"),date(rs,"coverage_start"),date(rs,"coverage_end"),instant(rs,"fetched_at"),instant(rs,"published_at"),rs.getBoolean("current_ref"));}
    private static Instant instant(ResultSet rs,String key)throws SQLException {var t=rs.getTimestamp(key);return t==null?null:t.toInstant();}
    private static LocalDate date(ResultSet rs,String key)throws SQLException {var d=rs.getDate(key);return d==null?null:d.toLocalDate();}
    private static String hash(String value) {return value!=null&&value.matches("[a-fA-F0-9]{64}")?value:null;}
    private static BusinessException notFound() {return new BusinessException("MARKET_ADMIN_RESOURCE_NOT_FOUND","未找到该股票或所属行情版本",HttpStatus.NOT_FOUND);}
}
