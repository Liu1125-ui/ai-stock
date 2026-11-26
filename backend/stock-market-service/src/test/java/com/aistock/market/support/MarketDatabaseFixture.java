package com.aistock.market.support;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
public final class MarketDatabaseFixture {
    private final JdbcTemplate jdbc;
    public MarketDatabaseFixture() {
        var url=System.getenv("MARKET_TEST_DB_URL");var password=System.getenv("MARKET_TEST_DB_PASSWORD");
        if(!"jdbc:mysql://127.0.0.1:13316/stock_market_test?serverTimezone=UTC".equals(url) || password==null || password.isBlank())
            throw new IllegalStateException("Only explicitly isolated market test database is allowed");
        var source=new DriverManagerDataSource(url,"root",password);source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        jdbc=new JdbcTemplate(source);
        if(!"stock_market_test".equals(jdbc.queryForObject("select database()",String.class))) throw new IllegalStateException("Wrong database");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        for(var table:new String[]{"stock_valuation_snapshot","index_market_snapshot","market_local_alert_use","market_local_comparison_use","market_version_command_guard","market_version_action_event","market_version_action","market_version_use_request","market_version_use","market_version_use_bundle","market_version_lifecycle","market_version_review_operation","market_version_review","comparison_refresh_receipt","comparison_evidence_operation","comparison_evidence_version","alert_evidence_operation","alert_evidence_version","independent_calendar_operation","independent_calendar_head","independent_calendar_version","market_revision_event","realization_operation","realization_version","forecast_input_operation","forecast_input_adjustment","forecast_input_calendar","forecast_input_version","market_refresh_receipt","market_ingestion_operation","instrument_data_head","market_calendar_day","market_daily_bar","market_data_version","source_code_map"}) jdbc.update("delete from "+table);
        jdbc.update("delete from instrument where demo_only=false");
        jdbc.update("update market_revision_sequence set last_id=0 where singleton_id=1");
    }
    public JdbcTemplate jdbc(){return jdbc;}
}
