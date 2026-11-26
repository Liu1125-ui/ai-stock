package com.aistock.scheduler.task;
import java.time.Clock;import com.fasterxml.jackson.databind.ObjectMapper;import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;
@Configuration public class TaskConfiguration {
 @Bean ComparisonTaskClient comparisonTaskClient(@Value("${aistock.comparison-update.market-url:http://127.0.0.1:18082}")String base,@Value("${aistock.internal.token:}")String token,ObjectMapper mapper,Clock clock){return new HttpComparisonTaskClient(base,token,mapper,clock);}
 @Bean Clock schedulerClock(){return Clock.systemUTC();}
 @Bean RevisionClient revisionClient(@Value("${aistock.revision.market-url:http://127.0.0.1:18082}")String market,@Value("${aistock.prediction.url:http://127.0.0.1:18083}")String prediction,@Value("${aistock.internal.token:}")String token,ObjectMapper mapper){return new HttpRevisionClient(market,prediction,token,mapper);}
 @Bean PredictionTaskClient predictionTaskClient(@Value("${aistock.prediction.url:http://127.0.0.1:18083}")String base,@Value("${aistock.internal.token:}")String token,ObjectMapper mapper){return new HttpPredictionTaskClient(base,token,mapper);}
}
