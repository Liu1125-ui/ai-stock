package com.aistock.prediction.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="aistock.training.admission-enabled",havingValue="true")
public class TrainingAdmissionConfiguration {
    @Bean TrainingAdmissionRepository trainingAdmissionRepository(JdbcTemplate jdbc,ObjectMapper mapper,Clock clock) {
        return new TrainingAdmissionRepository(jdbc,mapper,clock);
    }

    @Bean TrainingConfigurationCatalog trainingConfigurationCatalog(ObjectMapper mapper,
        @Value("${aistock.training.catalog:{}}") String entries) {
        return new TrainingConfigurationCatalog(mapper,entries);
    }
    @Bean TrainingProgressService trainingProgressService(TrainingProgressClients.Scheduler scheduler,
        TrainingProgressClients.Algorithm algorithm,@Value("${aistock.internal.token:}") String token) {
        return new TrainingProgressService(scheduler,algorithm,token);
    }
}
