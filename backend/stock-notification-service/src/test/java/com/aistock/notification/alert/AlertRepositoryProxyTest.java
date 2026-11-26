package com.aistock.notification.alert;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AlertRepositoryProxyTest {
 @Configuration static class Config {
  @Bean static PersistenceExceptionTranslationPostProcessor translation(){return new PersistenceExceptionTranslationPostProcessor();}
  @Bean JdbcTemplate jdbc(){var jdbc=mock(JdbcTemplate.class);when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);return jdbc;}
  @Bean AlertRuleRepository repository(JdbcTemplate jdbc){return new AlertRuleRepository(jdbc);}
 }
 @Test void springRepositoryProxySupportsPreferenceAndRuleReads(){try(var context=new AnnotationConfigApplicationContext(Config.class)){var repo=context.getBean(AlertRuleRepository.class);assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(repo));var owner=UUID.randomUUID();assertFalse(new NotificationPreferenceService(repo,Clock.systemUTC()).get(owner).emailEnabled());assertEquals(0,new AlertRuleService(repo,i->null,Clock.systemUTC(),50).list(owner,1,20).total());}}
}
