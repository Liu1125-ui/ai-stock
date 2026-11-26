package com.aistock.prediction.training;

import com.aistock.prediction.modelcatalog.ModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.UUID;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
@ConditionalOnProperty(name="aistock.training.dispatch-enabled",havingValue="true")
public class TrainingDispatchConfiguration {
    @Bean TrainingDispatchWorker trainingDispatchWorker(JdbcTemplate jdbc,Clock clock,
        @Value("${aistock.training.scheduler-url:http://127.0.0.1:18084}") String scheduler,
        @Value("${aistock.internal.token:}") String token) {
        return new TrainingDispatchWorker(new TrainingDispatchStore(jdbc,clock),
            new TrainingSchedulerClient(scheduler,token,Duration.ofSeconds(4)));
    }

    @Bean
    @ConditionalOnProperty(name="aistock.training.weekly-scan-enabled",havingValue="true")
    WeeklyTrainingScan weeklyTrainingScan(JdbcTemplate jdbc,Clock clock,ObjectMapper mapper,ModelRepository models,
        @Value("${aistock.training.weekly-calendar-path}") String calendar,
        @Value("${aistock.training.weekly-readiness-path}") String readiness,
        @Value("${aistock.training.weekly-preset-root}") String presets,
        @Value("${aistock.training.weekly-configuration-id}") String configuration,
        @Value("${aistock.training.weekly-owner}") UUID owner) {
        var evidence=new WeeklyTrainingEvidence(mapper,clock,Path.of(calendar),Path.of(readiness),
            Path.of(presets),configuration);
        return new WeeklyTrainingScan(new WeeklyTrainingPlanner(jdbc,models,clock),
            new TrainingAdmissionRepository(jdbc,mapper,clock),evidence,owner);
    }

}
