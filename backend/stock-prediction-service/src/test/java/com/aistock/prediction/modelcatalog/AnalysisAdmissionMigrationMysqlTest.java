package com.aistock.prediction.modelcatalog;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql") class AnalysisAdmissionMigrationMysqlTest {
    @Test void populatedV11UpgradePreservesCompletedResultsAndOperations(){
        String url=System.getenv("PREDICTION_TEST_DB_URL"),password=System.getenv("PREDICTION_TEST_DB_PASSWORD");
        if(!"jdbc:mysql://127.0.0.1:13316/stock_prediction_test?serverTimezone=UTC".equals(url)||password==null||password.isBlank())throw new IllegalStateException("Explicit isolated prediction test DB required");
        String schema="stock_prediction_v12_"+UUID.randomUUID().toString().replace("-","");
        if(!schema.matches("stock_prediction_v12_[0-9a-f]{32}"))throw new IllegalStateException("Unsafe fixture schema");
        var admin=new JdbcTemplate(new DriverManagerDataSource(url,"root",password));
        admin.execute("create database `"+schema+"`");
        try{
            var ds=new DriverManagerDataSource(url.replace("/stock_prediction_test?","/"+schema+"?"),"root",password);var jdbc=new JdbcTemplate(ds);
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("11").load().migrate();
            String result=UUID.randomUUID().toString(),user=UUID.randomUUID().toString(),version=UUID.randomUUID().toString(),op=UUID.randomUUID().toString(),hash="a".repeat(64);
            jdbc.update("insert into stock_analysis_result(analysis_id,user_id,instrument_id,range_from,range_to,market_version_id,context_hash,context_json,paragraphs_json,created_at) values(?,?,'SH600030','2026-07-07','2026-10-07',?,?,?,?,'2026-10-07 07:00:00')",result,user,version,hash,"{\"facts\":{\"STOCK\":\"SH600030\"}}","[{\"text\":\"fixture\"}]");
            jdbc.update("insert into stock_analysis_operation(operation_id,user_id,instrument_id,range_from,range_to,market_version_id,request_hash,context_hash,context_json,state,analysis_id,created_at,lease_until,finished_at) values(?,?,'SH600030','2026-07-07','2026-10-07',?,?,?,?,'SUCCEEDED',?,'2026-10-07 07:00:00','2026-10-07 07:00:50','2026-10-07 07:00:02')",op,user,version,hash,hash,"{\"facts\":{\"STOCK\":\"SH600030\"}}",result);
            var oldResult=jdbc.queryForMap("select * from stock_analysis_result where analysis_id=?",result);var oldOperation=jdbc.queryForMap("select * from stock_analysis_operation where operation_id=?",op);
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("12").load().migrate();
            assertEquals(oldResult,jdbc.queryForMap("select * from stock_analysis_result where analysis_id=?",result));
            var upgraded=jdbc.queryForMap("select * from stock_analysis_operation where operation_id=?",op);
            assertEquals(1,((Number)upgraded.remove("execution_generation")).longValue());assertEquals(1,((Number)upgraded.remove("fencing_revision")).longValue());
            var ready=upgraded.remove("context_ready");assertTrue(Boolean.TRUE.equals(ready)||ready instanceof Number n&&n.intValue()==1);
            assertEquals(oldOperation,upgraded);
        }finally{admin.execute("drop database `"+schema+"`");}
    }
}
