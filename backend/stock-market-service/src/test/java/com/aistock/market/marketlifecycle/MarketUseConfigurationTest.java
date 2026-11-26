package com.aistock.market.marketlifecycle;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class MarketUseConfigurationTest {
    private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(MarketUseConfiguration.class)
        .withBean(Clock.class,Clock::systemUTC)
        .withBean(com.fasterxml.jackson.databind.ObjectMapper.class,()->new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules())
        .withBean(com.aistock.market.comparison.ComparisonRefreshRepository.class,()->org.mockito.Mockito.mock(com.aistock.market.comparison.ComparisonRefreshRepository.class))
        .withBean(JdbcTemplate.class,()->new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:lifecycle-config-no-io","sa","")));
    @Test void defaultBuildRegistersProtocolButDoesNotRunReconciliation() {
        runner.run(context->{assertNull(context.getStartupFailure());assertNotNull(context.getBean(MarketLifecycleService.class));assertEquals(new MarketUseReconciler.Result(0,0,0),context.getBean(MarketUseReconciler.class).runOnce());assertFalse(context.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"));});
    }
    @Test void incompleteBuildCannotPretendEnforcementIsReady() {
        runner.withPropertyValues("aistock.market.lifecycle.enforced=true").run(context->{assertNotNull(context.getStartupFailure());assertTrue(context.getStartupFailure().getMessage().contains("marketLifecycleService"));});
    }
    @Test void localComparisonCannotBypassGateReadiness(){runner.withPropertyValues("aistock.market.lifecycle.local-comparison-enabled=true").run(c->{assertNotNull(c.getStartupFailure());});runner.withPropertyValues("aistock.market.lifecycle.local-comparison-enabled=true","aistock.market.lifecycle.protocol-enabled=true","aistock.market.lifecycle.evidence-gates-enabled=true").run(c->{assertNull(c.getStartupFailure());assertTrue(c.getBean(MarketComparisonUseOwner.class).enabled());});}

    @Test void localAlertCannotBypassGateReadiness(){runner.withPropertyValues("aistock.market.lifecycle.local-alert-enabled=true").run(c->{assertNotNull(c.getStartupFailure());});runner.withPropertyValues("aistock.market.lifecycle.local-alert-enabled=true","aistock.market.lifecycle.protocol-enabled=true","aistock.market.lifecycle.evidence-gates-enabled=true").run(c->{assertNull(c.getStartupFailure());assertTrue(c.getBean(com.aistock.market.alert.AlertLocalUseOwner.class).enabled());});}

}
