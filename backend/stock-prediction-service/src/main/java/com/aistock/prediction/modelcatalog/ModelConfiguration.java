package com.aistock.prediction.modelcatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
@Configuration
public class ModelConfiguration {
    @Bean Clock modelClock(){return Clock.systemUTC();}
    @Bean ModelRepository modelRepository(JdbcTemplate jdbc,ObjectMapper mapper){return new ModelRepository(jdbc,mapper);}
    @Bean ArtifactInspector artifactInspector(@Value("${aistock.algorithm.url:http://127.0.0.1:18090}")String url,@Value("${aistock.internal.token:}")String token,ObjectMapper mapper){return new AlgorithmArtifactInspector(url,token,mapper);}
    @Bean ModelCatalogService modelCatalog(ModelRepository repo,ArtifactInspector inspector,Clock clock,ObjectMapper mapper){return new ModelCatalogService(repo,inspector,clock,mapper);}
    @Bean ModelReleaseService modelRelease(ModelRepository repo,ArtifactInspector inspector,Clock clock,ObjectMapper mapper){return new ModelReleaseService(repo,inspector,clock,mapper);}
}
