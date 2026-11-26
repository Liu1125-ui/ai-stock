package com.aistock.market.marketlifecycle;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.junit.jupiter.api.function.Executable;
import java.sql.SQLException;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class MarketLifecycleMigrationMysqlTest {
    private static final String BASE="jdbc:mysql://127.0.0.1:13316/stock_market_test?serverTimezone=UTC";
    private static final String URL="jdbc:mysql://127.0.0.1:13316/stock_market_lifecycle_migration_test?serverTimezone=UTC";
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;

    @BeforeEach void isolatedSchemaOnly() {
        var password=System.getenv("MARKET_TEST_DB_PASSWORD");
        if(!BASE.equals(System.getenv("MARKET_TEST_DB_URL"))||password==null||password.isBlank())
            throw new IllegalStateException("Only isolated localhost test MySQL is allowed");
        var admin=new JdbcTemplate(new DriverManagerDataSource(BASE,"root",password));
        assertEquals("stock_market_test",admin.queryForObject("select database()",String.class));
        admin.execute("CREATE DATABASE IF NOT EXISTS stock_market_lifecycle_migration_test CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
        source=new DriverManagerDataSource(URL,"root",password);jdbc=new JdbcTemplate(source);
        assertEquals("stock_market_lifecycle_migration_test",jdbc.queryForObject("select database()",String.class));
        Flyway.configure().dataSource(source).locations("classpath:db/migration").cleanDisabled(false).load().clean();
    }
    private Flyway migrate(String target) {
        var flyway=Flyway.configure().dataSource(source).locations("classpath:db/migration").target(target).load();
        flyway.migrate();return flyway;
    }
    private String version(String provider,String state,String hash) {
        var id=UUID.randomUUID().toString();
        jdbc.update("insert into market_data_version(version_id,instrument_id,provider,price_basis,status,quality,content_hash,source_payload_hash,fetched_at,published_at,coverage_start,coverage_end) values(?,'SH600030',?,'RAW',?,'ROW_VALIDATED',?,?,now(3),now(3),'2026-01-01','2026-10-23')",id,provider,state,hash.repeat(64),"b".repeat(64));
        return id;
    }
    @Test void freshDatabaseMigratesAndRejectsBrokenState() {
        assertEquals("15",migrate("15").info().current().getVersion().toString());
        var version=version("baostock","AVAILABLE","a");
        jdbc.update("insert into market_version_lifecycle(version_id,use_state,revision,updated_at) values(?,'ENABLED',0,now(3))",version);
        rejects(3819,()->jdbc.update("update market_version_lifecycle set use_state='DRAINING' where version_id=?",version));
        rejects(3819,()->jdbc.update("update market_version_lifecycle set revision=-1 where version_id=?",version));
        rejects(1451,()->jdbc.update("delete from market_data_version where version_id=?",version));
    }
    @Test void upgradeBackfillsOnlySupportedPublishedRawWithoutChangingOldData() {
        migrate("14");
        var raw=version("baostock","AVAILABLE","a");
        version("tushare","AVAILABLE","c");version("baostock","UNAVAILABLE","d");
        jdbc.update("insert into instrument_data_head(instrument_id,provider,price_basis,version_id,revision) values('SH600030','baostock','RAW',?,7)",raw);
        jdbc.update("insert into market_daily_bar(version_id,trade_date,open_price,high_price,low_price,close_price,volume_shares,amount_yuan,trade_status) values(?,'2026-10-23',10,11,9,10,100,1000,1)",raw);
        jdbc.update("insert into forecast_input_version(input_version_id,raw_version_id,instrument_id,provider,definition_code,content_hash,calendar_hash,source_payload_hash,fetched_at,origin_date,calendar_start,calendar_end,status) values(?,?,'SH600030','baostock','test',?,?,?,now(3),'2026-10-23','2026-01-01','2026-11-01','AVAILABLE')",UUID.randomUUID().toString(),raw,"e".repeat(64),"f".repeat(64),"b".repeat(64));
        var tables=List.of("market_data_version","instrument_data_head","market_daily_bar","forecast_input_version");
        var before=new ArrayList<List<Map<String,Object>>>();for(var table:tables)before.add(jdbc.queryForList("select * from "+table));
        var latest=migrate("15");assertEquals("15",latest.info().current().getVersion().toString());
        assertEquals(1,jdbc.queryForObject("select count(*) from market_version_lifecycle",Integer.class));
        assertEquals(raw,jdbc.queryForObject("select version_id from market_version_lifecycle where use_state='ENABLED' and revision=0 and cycle_id is null",String.class));
        for(int i=0;i<tables.size();i++)assertEquals(before.get(i),jdbc.queryForList("select * from "+tables.get(i)));
        assertEquals(0,latest.migrate().migrationsExecuted);
        assertEquals(1,jdbc.queryForObject("select count(*) from market_version_lifecycle",Integer.class));
    }
    @Test void bundleIdentityAndPerVersionUsesCannotDuplicateOrOutliveTheirParents() {
        migrate("15");var raw=version("baostock","AVAILABLE","a");
        jdbc.update("insert into market_version_lifecycle(version_id,use_state,revision,updated_at) values(?,'ENABLED',0,now(3))",raw);
        var bundle=UUID.randomUUID().toString();var op=UUID.randomUUID().toString();
        insertBundle(bundle,op,raw);
        rejects(1062,()->insertBundle(UUID.randomUUID().toString(),op,raw));
        rejects(3819,()->jdbc.update("update market_version_use_bundle set generation=0 where bundle_id=?",bundle));
        rejects(3819,()->jdbc.update("update market_version_use_bundle set owner='notification' where bundle_id=?",bundle));
        var use=UUID.randomUUID().toString();
        jdbc.update("insert into market_version_use(use_id,bundle_id,version_id,state,created_at) values(?,?,?,'ACTIVE',now(3))",use,bundle,raw);
        rejects(1062,()->jdbc.update("insert into market_version_use(use_id,bundle_id,version_id,state,created_at) values(?,?,?,'ACTIVE',now(3))",UUID.randomUUID().toString(),bundle,raw));
        rejects(1451,()->jdbc.update("delete from market_version_use_bundle where bundle_id=?",bundle));
        rejects(3819,()->jdbc.update("update market_version_use set state='RELEASED' where use_id=?",use));
        jdbc.update("update market_version_use set state='RELEASED',released_at=now(3),terminal_proof_hash=? where use_id=?","c".repeat(64),use);
        assertEquals("RELEASED",jdbc.queryForObject("select state from market_version_use where use_id=?",String.class,use));
    }
    @Test void concurrentRegistrationsShareOneBusinessIdentity() throws Exception {
        migrate("15");var raw=version("baostock","AVAILABLE","a");var op=UUID.randomUUID().toString();
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var attempts=new ArrayList<Future<Boolean>>();
            for(int i=0;i<2;i++)attempts.add(pool.submit(()->{
                start.await();
                try {insertBundle(UUID.randomUUID().toString(),op,raw);return true;}
                catch(DataAccessException e){assertEquals(1062,sqlCause(e).getErrorCode());return false;}
            }));
            start.countDown();int success=0;for(var attempt:attempts)if(attempt.get(10,TimeUnit.SECONDS))success++;
            assertEquals(1,success);
            assertEquals(1,jdbc.queryForObject("select count(*) from market_version_use_bundle",Integer.class));
        } finally {pool.shutdownNow();}
    }
    private static void rejects(int vendorCode,Executable operation) {
        var error=assertThrows(DataAccessException.class,operation);
        assertEquals(vendorCode,sqlCause(error).getErrorCode());
    }
    private static SQLException sqlCause(Throwable error) {
        while(error!=null){if(error instanceof SQLException sql)return sql;error=error.getCause();}
        throw new AssertionError("Expected a concrete MySQL constraint error");
    }
    private void insertBundle(String bundle,String op,String raw) {
        jdbc.update("insert into market_version_use_bundle(bundle_id,owner,purpose,owner_operation_id,generation,selector_hash,versions_hash,version_ids_json,state,created_at) values(?,'prediction','FORECAST',?,1,?,?,?,'ACTIVE',now(3))",bundle,op,"a".repeat(64),"b".repeat(64),"[\""+raw+"\"]");
    }
}
