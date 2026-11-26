package com.aistock.scheduler.training;

import com.aistock.scheduler.task.HttpTrainingExecutionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="aistock.training.execution-enabled",havingValue="true")
public class TrainingExecutionConfiguration {
    @Bean TrainingExecutionWorker trainingExecutionWorker(JdbcTemplate jdbc,Clock clock,ObjectMapper mapper,
        @Value("${aistock.training.algorithm-url:http://127.0.0.1:18090}") String algorithm,
        @Value("${aistock.internal.token:}") String token) {
        return new TrainingExecutionWorker(new TrainingExecutionQueue(jdbc,clock),
            new HttpTrainingExecutionClient(algorithm,token,mapper));
    }
    @Bean
    @ConditionalOnProperty(name="aistock.training.registration-enabled",havingValue="true")
    com.aistock.scheduler.task.TrainingCandidateRegistrationWorker trainingRegistrationWorker(JdbcTemplate jdbc,Clock clock,ObjectMapper mapper,
        @Value("${aistock.training.algorithm-url:http://127.0.0.1:18090}") String algorithm,
        @Value("${aistock.prediction.url:http://127.0.0.1:18083}") String prediction,
        @Value("${aistock.internal.token:}") String token) {
        return new com.aistock.scheduler.task.TrainingCandidateRegistrationWorker(jdbc,clock,mapper,algorithm,prediction,token);
    }
    @Bean(destroyMethod="close")
    TrainingPolling trainingPolling(TrainingExecutionWorker execution,
        org.springframework.beans.factory.ObjectProvider<com.aistock.scheduler.task.TrainingCandidateRegistrationWorker> registration,
        @Value("${aistock.training.execution-delay-ms:15000}") long executionDelay,
        @Value("${aistock.training.registration-delay-ms:30000}") long registrationDelay) {
        var candidate=registration.getIfAvailable();
        return new TrainingPolling(execution::tick,executionDelay,candidate==null?null:candidate::tick,registrationDelay);
    }
}
