package com.aistock.notification.alertmail;

import com.aistock.notification.alert.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;import java.util.*;import java.util.concurrent.*;
import org.flywaydb.core.Flyway;import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;

class AlertMailDatabaseFixture {
 JdbcTemplate jdbc;UUID owner,rule,trigger;Clock clock;AlertMailRepository repo;TransactionTemplate tx;
 @BeforeEach void setup()throws Exception{
  var url=System.getenv("MAIL_TEST_DB_URL");var pw=System.getenv("MAIL_TEST_DB_PASSWORD");if(!"jdbc:mysql://127.0.0.1:13316/stock_notification_test?serverTimezone=UTC".equals(url)||pw==null)throw new IllegalStateException("isolated DB required");
  jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"root",pw));Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate();
  jdbc.update("delete from alert_mail_delivery");owner=UUID.randomUUID();rule=UUID.randomUUID();trigger=UUID.randomUUID();clock=Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"),ZoneOffset.UTC);repo=new AlertMailRepository(jdbc);tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
  jdbc.update("insert into alert_rule(rule_id,user_id,instrument_id,condition_code,threshold_value,enabled,revision,effective_after_date,boundary_raw_version,boundary_json,created_at,updated_at) values(?,?,'SH600030','PRICE_ABOVE',25,true,0,'2026-10-01',?,'{}',?,?)",rule.toString(),owner.toString(),UUID.randomUUID().toString(),java.sql.Timestamp.from(clock.instant()),java.sql.Timestamp.from(clock.instant()));
 }
 AlertMailAccountClient.AccountContext context(boolean verified){return new AlertMailAccountClient.AccountContext(owner,"ENABLED",7,verified,clock.instant());}
 void preference(boolean enabled,long revision){jdbc.update("insert into notification_preference(user_id,email_enabled,revision,updated_at) values(?,?,?,?) on duplicate key update email_enabled=values(email_enabled),revision=values(revision)",owner.toString(),enabled,revision,java.sql.Timestamp.from(clock.instant()));}
 void insertTrigger(){jdbc.update("insert into alert_trigger(trigger_id,owner_id,instrument_id,rule_id,trade_date,rule_revision,snapshot_json,created_at) values(?,?,'SH600030',?,'2026-10-09',0,'{}',?)",trigger.toString(),owner.toString(),rule.toString(),java.sql.Timestamp.from(clock.instant()));jdbc.update("insert into inbox_message(message_id,trigger_id,owner_id,created_at) values(?,?,?,?)",UUID.randomUUID().toString(),trigger.toString(),owner.toString(),java.sql.Timestamp.from(clock.instant()));}
 AlertMailRecords.Decision capture(boolean enabled,boolean verified){return repo.locked(owner,()->{insertTrigger();return new AlertMailOutbox(repo,enabled,Duration.ofHours(24)).capture(trigger,owner,rule,0,context(verified),clock.instant());});}
 int count(String table){return jdbc.queryForObject("select count(*) from "+table+" where owner_id=?",Integer.class,owner.toString());}

}