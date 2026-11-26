package com.aistock.market.refresh;
import com.aistock.market.calendar.*;import com.aistock.common.BusinessException;import com.fasterxml.jackson.databind.*;
import java.net.URI;import java.net.http.*;import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Component;import org.springframework.http.HttpStatus;
@Component public class CalendarCollectorClient implements CalendarCollector {
 private final String url,token;private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
 private final ObjectMapper mapper= com.fasterxml.jackson.databind.json.JsonMapper.builder().addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
 public CalendarCollectorClient(@Value("${aistock.algorithm.url:http://127.0.0.1:18090}")String url,@Value("${aistock.internal.token:}")String token){this.url=url;this.token=token;}
 public CalendarRecords.Candidate collect(CalendarRecords.Request input){
  try{var request=HttpRequest.newBuilder(URI.create(url+"/internal/v1/market/calendar-collect")).timeout(Duration.ofSeconds(30)).header("X-Service-Token",token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(input))).build();var response=client.send(request,info->new AlgorithmCollectorClient.LimitedBody(262144));
   if(response.statusCode()!=200)throw unavailable();var root=mapper.readTree(response.body());var fields=new java.util.HashSet<String>();root.fieldNames().forEachRemaining(fields::add);if(!fields.equals(java.util.Set.of("operationId","state","candidate","errorCode"))||!input.operationId().equals(root.path("operationId").asText())||!"AVAILABLE".equals(root.path("state").asText())||!root.path("errorCode").isNull())throw unavailable();return mapper.treeToValue(root.get("candidate"),CalendarRecords.Candidate.class);
  }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw unavailable();}catch(Exception error){throw unavailable();}
 }
 private BusinessException unavailable(){return new BusinessException("CALENDAR_SOURCE_UNAVAILABLE","交易日历来源暂不可用",HttpStatus.SERVICE_UNAVAILABLE);}
}
