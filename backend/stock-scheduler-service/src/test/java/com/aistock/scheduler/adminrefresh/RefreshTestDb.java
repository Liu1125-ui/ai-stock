package com.aistock.scheduler.adminrefresh;
import com.aistock.common.adminrefresh.AdminMarketRefreshRecords.*;
import java.time.*;import java.util.*;import org.flywaydb.core.Flyway;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DriverManagerDataSource;
final class RefreshTestDb {
 static final Instant NOW=Instant.parse("2026-10-27T12:00:00Z");
 static final UUID ACTOR=UUID.fromString("00000000-0000-0000-0000-000000000001");
 static final String VERSION="11111111-1111-1111-1111-111111111111";
 final JdbcTemplate jdbc;
 RefreshTestDb(){var url=System.getenv("SCHEDULER_TEST_DB_URL");var password=System.getenv("SCHEDULER_TEST_DB_PASSWORD");if(!"jdbc:mysql://127.0.0.1:13316/stock_scheduler_test?serverTimezone=UTC".equals(url)||password==null||password.isBlank())throw new IllegalStateException("isolated scheduler test DB required");var ds=new DriverManagerDataSource(url,"root",password);ds.setDriverClassName("com.mysql.cj.jdbc.Driver");Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();jdbc=new JdbcTemplate(ds);for(var table:List.of("admin_market_refresh_operation","admin_market_refresh_guard","admin_market_refresh_context","admin_task_operation","comparison_update_context","task_attempt","scheduled_task"))jdbc.update("delete from "+table);}
 static Context context(){return new Context("SH600030","baostock","RAW",true,null,VERSION,"7",LocalDate.parse("2026-01-01"),LocalDate.parse("2026-10-26"),NOW);}
 static Command command(){return new Command(UUID.randomUUID(),UUID.fromString(VERSION),"7",LocalDate.parse("2026-10-27"),"RECHECK_DATA");}
 AdminMarketRefreshRepository repo(){return new AdminMarketRefreshRepository(jdbc,true,true,60);}
 int count(String table){return jdbc.queryForObject("select count(*) from "+table,Integer.class);}
}