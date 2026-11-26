package com.aistock.scheduler.training;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="aistock.training.inbox-enabled",havingValue="true")
public class TrainingInboxConfiguration {
    @Bean TrainingInbox trainingInbox(JdbcTemplate jdbc,Clock clock) {
        return new TrainingInbox(jdbc,clock);
    }
}
