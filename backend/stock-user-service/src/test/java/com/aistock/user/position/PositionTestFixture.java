package com.aistock.user.position;
import java.util.*;import java.time.*;import java.math.*;import javax.sql.DataSource;
import com.aistock.user.support.AuthDatabaseFixture;import com.aistock.user.session.Identity;import com.aistock.user.watchlist.MarketIdentityClient;import com.aistock.common.ApiResponse;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;import org.springframework.boot.autoconfigure.AutoConfigurations;import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.transaction.PlatformTransactionManager;import com.fasterxml.jackson.databind.ObjectMapper;
class PositionTestFixture implements AutoCloseable {
 final AuthDatabaseFixture db=new AuthDatabaseFixture();final Identity owner=user("USER"),other=user("ADMIN");
 final org.springframework.context.ConfigurableApplicationContext context;final PositionRepository repo;final PositionService service;
 PositionTestFixture(){
  var c=new org.springframework.context.annotation.AnnotationConfigApplicationContext();
  c.registerBean(DataSource.class,()->db.jdbc().getDataSource());
  c.register(DataSourceTransactionManagerAutoConfiguration.class,MybatisPlusAutoConfiguration.class,PositionPersistenceConfiguration.class);c.refresh();context=c;
  repo=new PositionRepository(context.getBean(PositionMapper.class),context.getBean(PlatformTransactionManager.class),new ObjectMapper().findAndRegisterModules(),100);
  service=new PositionService(repo,(id,token)->ApiResponse.success(new MarketIdentityClient.InstrumentView(id,id,"fixture",false,"stock-market-service"),"t"),"fixture",Clock.fixed(db.clock().instant(),ZoneOffset.UTC),1000000000L,new BigDecimal("1000000"));
 }
 Identity user(String role){var id=UUID.randomUUID();db.jdbc().update("INSERT INTO user_account(user_id,email,email_normalized,email_verified_at,password_hash,role_code,status,nickname,credential_version,created_at) VALUES (?,?,?,now(3),'fixture',?,'ENABLED','fixture',1,now(3))",id.toString(),id+"@example.com",id+"@example.com",role);return new Identity(id,role,false,1);}
 PositionRecords.PositionMutation create(String stock){return new PositionRecords.PositionMutation(UUID.randomUUID(),"CREATE",null,stock,"1","20",null);}
 public void close(){context.close();}
}
