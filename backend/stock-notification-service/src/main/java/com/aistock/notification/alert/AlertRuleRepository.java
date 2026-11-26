package com.aistock.notification.alert;
import java.util.*;import java.util.function.Supplier;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DataSourceTransactionManager;import org.springframework.transaction.TransactionDefinition;import org.springframework.transaction.support.TransactionTemplate;import org.springframework.stereotype.Repository;
@Repository public class AlertRuleRepository {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;
 public AlertRuleRepository(JdbcTemplate jdbc){this.jdbc=jdbc;tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setTimeout(5);}
 JdbcTemplate jdbc(){return jdbc;}
 <T>T locked(UUID owner,Supplier<T> action){return tx.execute(s->{jdbc.update("insert into alert_user_guard(user_id) values(?) on duplicate key update user_id=values(user_id)",owner.toString());jdbc.queryForMap("select user_id from alert_user_guard where user_id=? for update",owner.toString());return action.get();});}
}
