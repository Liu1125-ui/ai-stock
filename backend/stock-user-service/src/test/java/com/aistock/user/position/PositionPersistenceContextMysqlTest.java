package com.aistock.user.position;
import com.aistock.user.support.AuthDatabaseFixture;
import org.junit.jupiter.api.*;
import java.util.UUID;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import static org.junit.jupiter.api.Assertions.*;
@Tag("mysql") class PositionPersistenceContextMysqlTest {
 @Test void flywayCreatesPositionTables(){
  var jdbc=new AuthDatabaseFixture().jdbc();
  assertEquals(3,jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=database() and table_name in ('position_book','position','position_operation_receipt')",Integer.class));
 }

 @Test void realMapperKeepsDecimalAndSharesJdbcRollback(){
  var f=new AuthDatabaseFixture();var jdbc=f.jdbc();var owner=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO user_account(user_id,email,email_normalized,email_verified_at,password_hash,role_code,status,nickname,credential_version,created_at) VALUES (?,?,?,now(3),'fixture','USER','ENABLED','fixture',1,now(3))",owner,owner+"@example.com",owner+"@example.com");
  jdbc.update("insert into position_book values(?,0,now(3))",owner);
  new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceTransactionManagerAutoConfiguration.class,MybatisPlusAutoConfiguration.class))
   .withBean(DataSource.class,()->jdbc.getDataSource()).withUserConfiguration(PositionPersistenceConfiguration.class).run(ctx->{
    assertNull(ctx.getStartupFailure());var mapper=ctx.getBean(PositionMapper.class);var tx=new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));
    var position=new PositionEntity();position.setPositionId(UUID.randomUUID().toString());position.setUserId(owner);position.setInstrumentId("SH600030");position.setQuantity(1L);position.setAverageCost(new BigDecimal("123.456789"));position.setRevision(1L);position.setCreatedAt(f.clock().instant());position.setUpdatedAt(f.clock().instant());
    assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{mapper.insert(position);jdbc.update("update position_book set revision=revision+1 where user_id=?",owner);throw new IllegalStateException("rollback probe");}));
    assertEquals(0L,mapper.selectCount(new QueryWrapper<PositionEntity>().eq("user_id",owner)));
    assertEquals(0L,jdbc.queryForObject("select revision from position_book where user_id=?",Long.class,owner));
    mapper.insert(position);assertEquals(new BigDecimal("123.456789"),mapper.selectById(position.getPositionId()).getAverageCost());
   });
 }

 @Test void repeatedMigrationPreservesExistingWatchlist(){
  var f=new AuthDatabaseFixture();var jdbc=f.jdbc();
  assertEquals("stock_user_test",jdbc.queryForObject("select database()",String.class));
  // Strictly disposable schema only: build V2 with pre-existing data before applying the current migration.
  org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource()).schemas("stock_user_test").cleanDisabled(false).load().clean();
  org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration").target("2").load().migrate();
  var owner=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO user_account(user_id,email,email_normalized,email_verified_at,password_hash,role_code,status,nickname,credential_version,created_at) VALUES (?,?,?,now(3),'fixture','USER','ENABLED','fixture',1,now(3))",owner,owner+"@example.com",owner+"@example.com");
  jdbc.update("insert into watchlist values(?,'SH600030',now(3))",owner);
  var latest=org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration").load();
  latest.migrate();
  assertEquals(1,jdbc.queryForObject("select count(*) from watchlist where user_id=?",Integer.class,owner));
  assertEquals(1,jdbc.queryForObject("select count(*) from flyway_schema_history where success=1 and version='3'",Integer.class));
  assertEquals(0,latest.migrate().migrationsExecuted);
  assertEquals(1,jdbc.queryForObject("select count(*) from watchlist where user_id=?",Integer.class,owner));
 }
}
