package com.aistock.scheduler.marketrefs;
import com.aistock.common.marketrefs.*;import com.aistock.common.marketrefs.MarketReferenceRecords.Source;import com.fasterxml.jackson.databind.ObjectMapper;import javax.sql.DataSource;import java.time.Clock;import java.util.*;import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.boot.web.servlet.FilterRegistrationBean;
@Configuration public class MarketReferenceOwnerConfiguration {
 @Bean public ReferenceScanner marketReferenceScanner(DataSource source){return new ReferenceScanner(source,"scheduler",List.of(
   new Source("tasks","任务产物标识","scheduled_task",List.of("version_id"),List.of()),
   new Source("attempts","任务尝试产物标识","task_attempt",List.of("version_id"),List.of()),
   new Source("verification_scan","验证扫描游标","verification_revision_scan",List.of("raw_version_id"),List.of()),
   new Source("alert_evidence_work","预警证据工作项","alert_evidence_work",List.of("raw_version_id"),List.of()),
   new Source("alert_jobs","预警任务","alert_job",List.of("raw_version_id"),List.of()),
   new Source("alert_scan","预警扫描游标","alert_scan_cursor",List.of("event_raw_version"),List.of()),
   new Source("comparison_context","对比更新上下文","comparison_update_context",List.of("raw_version_id"),List.of()),
   new Source("refresh_context","人工重取上下文","admin_market_refresh_context",List.of("base_version_id"),List.of()),
   new Source("refresh_operations","人工重取回执","admin_market_refresh_operation",List.of("base_version_id"),List.of())),Clock.systemUTC());}
 @Bean public FilterRegistrationBean<ReferenceTokenFilter> marketReferenceToken(@Value("${aistock.internal.token:}")String token,ObjectMapper mapper){var bean=new FilterRegistrationBean<ReferenceTokenFilter>(new ReferenceTokenFilter(token,mapper));bean.setOrder(-120);bean.addUrlPatterns(MarketReferenceInput.INTERNAL_BASE,MarketReferenceInput.INTERNAL_BASE+"/*");return bean;}
}
