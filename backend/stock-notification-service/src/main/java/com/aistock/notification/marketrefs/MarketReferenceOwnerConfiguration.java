package com.aistock.notification.marketrefs;
import com.aistock.common.marketrefs.*;import com.aistock.common.marketrefs.MarketReferenceRecords.Source;import com.fasterxml.jackson.databind.ObjectMapper;import javax.sql.DataSource;import java.time.Clock;import java.util.*;import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.boot.web.servlet.FilterRegistrationBean;
@Configuration public class MarketReferenceOwnerConfiguration {
 @Bean public ReferenceScanner marketReferenceScanner(DataSource source){return new ReferenceScanner(source,"notification",List.of(
   new Source("alert_evaluation_uses","预警评价使用凭据","alert_evaluation_market_use",List.of("raw_version_id"),List.of("receipt_json")),
   new Source("alert_rules","预警规则边界","alert_rule",List.of("boundary_raw_version"),List.of("boundary_json")),
   new Source("alert_rule_operations","预警规则回执","alert_rule_request",List.of(),List.of("response_json")),
   new Source("alert_triggers","预警触发快照","alert_trigger",List.of(),List.of("snapshot_json"))),Clock.systemUTC());}
 @Bean public FilterRegistrationBean<ReferenceTokenFilter> marketReferenceToken(@Value("${aistock.internal.token:}")String token,ObjectMapper mapper){var bean=new FilterRegistrationBean<ReferenceTokenFilter>(new ReferenceTokenFilter(token,mapper));bean.setOrder(-120);bean.addUrlPatterns(MarketReferenceInput.INTERNAL_BASE,MarketReferenceInput.INTERNAL_BASE+"/*");return bean;}
}
