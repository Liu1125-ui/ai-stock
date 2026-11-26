package com.aistock.market.marketlifecycle;

import java.time.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods=false)
public class MarketUseConfiguration {
    @Bean MarketLifecycleService marketLifecycleService(JdbcTemplate jdbc,Clock clock,@Value("${aistock.market.lifecycle.enforced:false}")boolean enforced) {
        if(enforced)throw new IllegalStateException("All owner completion fences are not integrated; enforcement cannot be enabled in this build");
        return new MarketLifecycleService(jdbc,clock,()->false);
    }
    @Bean MarketEvidenceGate marketEvidenceGate(MarketLifecycleService service,@Value("${aistock.market.lifecycle.evidence-gates-enabled:false}")boolean enabled){return new MarketEvidenceGate(service,enabled);}
    @Bean MarketComparisonUseOwner marketComparisonUseOwner(JdbcTemplate jdbc,com.fasterxml.jackson.databind.ObjectMapper json,com.aistock.market.comparison.ComparisonRefreshRepository records,MarketLifecycleService lifecycle,MarketEvidenceGate gate,Clock clock,@Value("${aistock.market.lifecycle.local-comparison-enabled:false}")boolean enabled,@Value("${aistock.market.lifecycle.protocol-enabled:false}")boolean protocol){if(enabled&&(!protocol||!gate.enabled()))throw new IllegalStateException("Local comparison owner requires protocol and evidence gates");return new MarketComparisonUseOwner(jdbc,json,records,lifecycle,gate,clock,enabled);}
    @Bean LocalComparisonRecovery localComparisonRecovery(MarketComparisonUseOwner uses,@Value("${aistock.market.lifecycle.local-comparison-recovery-enabled:false}")boolean enabled){return new LocalComparisonRecovery(uses,enabled);}
    public static final class LocalComparisonRecovery {private final MarketComparisonUseOwner uses;private final boolean enabled;LocalComparisonRecovery(MarketComparisonUseOwner uses,boolean enabled){this.uses=uses;this.enabled=enabled;}@org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${aistock.market.lifecycle.reconcile-delay-ms:60000}",initialDelayString="${aistock.market.lifecycle.reconcile-delay-ms:60000}")public void recover(){if(enabled)uses.recoverBatch();}}
    @Configuration(proxyBeanMethods=false) @ConditionalOnProperty(name="aistock.market.lifecycle.local-comparison-recovery-enabled",havingValue="true") @EnableScheduling static class LocalComparisonScheduling{}
    @Bean com.aistock.market.alert.AlertLocalUseOwner alertLocalUseOwner(JdbcTemplate jdbc,com.fasterxml.jackson.databind.ObjectMapper json,MarketLifecycleService lifecycle,MarketEvidenceGate gate,Clock clock,@Value("${aistock.market.lifecycle.local-alert-enabled:false}")boolean enabled,@Value("${aistock.market.lifecycle.protocol-enabled:false}")boolean protocol){if(enabled&&(!protocol||!gate.enabled()))throw new IllegalStateException("Local alert owner requires protocol and evidence gates");return new com.aistock.market.alert.AlertLocalUseOwner(jdbc,json,lifecycle,gate,clock,enabled);}
    @Bean LocalAlertRecovery localAlertRecovery(com.aistock.market.alert.AlertLocalUseOwner uses,@Value("${aistock.market.lifecycle.local-alert-recovery-enabled:false}")boolean enabled){return new LocalAlertRecovery(uses,enabled);}
    public static final class LocalAlertRecovery {private final com.aistock.market.alert.AlertLocalUseOwner uses;private final boolean enabled;LocalAlertRecovery(com.aistock.market.alert.AlertLocalUseOwner uses,boolean enabled){this.uses=uses;this.enabled=enabled;}@org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${aistock.market.lifecycle.reconcile-delay-ms:60000}",initialDelayString="${aistock.market.lifecycle.reconcile-delay-ms:60000}")public void recover(){if(enabled)uses.recoverBatch();}}
    @Configuration(proxyBeanMethods=false) @ConditionalOnProperty(name="aistock.market.lifecycle.local-alert-recovery-enabled",havingValue="true") @EnableScheduling static class LocalAlertScheduling{}
    @Bean MarketUseProofClient marketUseProofClient(
        @Value("${aistock.references.prediction-url:http://127.0.0.1:18083}")String prediction,
        @Value("${aistock.references.notification-url:http://127.0.0.1:18085}")String notification,
        @Value("${server.port:8082}")int port,@Value("${aistock.internal.token:}")String token) {
        return new MarketUseProofClient(Map.of("prediction",prediction,"notification",notification,"market","http://127.0.0.1:"+port),token,Duration.ofSeconds(4));
    }
    @Bean MarketUseReconciler marketUseReconciler(MarketLifecycleService service,MarketUseProofClient client,
        @Value("${aistock.market.lifecycle.protocol-enabled:false}")boolean protocol,
        @Value("${aistock.market.lifecycle.reconcile-enabled:false}")boolean reconcile) {
        return new MarketUseReconciler(service,client,protocol&&reconcile);
    }
    @Configuration(proxyBeanMethods=false)
    @ConditionalOnProperty(name="aistock.market.lifecycle.reconcile-enabled",havingValue="true")
    @EnableScheduling
    static class UseScheduling {}
}
