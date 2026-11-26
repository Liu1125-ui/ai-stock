package com.aistock.scheduler.statistics;
import com.aistock.common.*;import com.aistock.common.statistics.*;import com.aistock.scheduler.security.*;import com.fasterxml.jackson.databind.ObjectMapper;import jakarta.servlet.http.Cookie;import java.time.*;import java.util.*;import org.junit.jupiter.api.Test;import org.springframework.test.web.servlet.setup.MockMvcBuilders;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class TaskStatisticsAccessTest {
 @Test void requiresAuthoritativeAdminAndExactReadOnlyRoute()throws Exception{
  var repo=mock(TaskStatisticsRepository.class);var identity=mock(SessionIdentityClient.class);var now=TaskStatisticsMysqlTest.NOW;var meta=StatisticsMeta.of(TaskStatisticsMysqlTest.window(),now);var duration=new com.aistock.common.statistics.StatisticsRecords.DurationSummary("0","0",null);
  when(repo.summary(any(),any())).thenReturn(new TaskStatisticsRecords.TasksSummary(meta,"0",List.of(),List.of(),List.of(),"0",List.of(),duration,"0",duration));
  when(identity.resolve(any(),any())).thenReturn(ApiResponse.success(new SessionIdentityClient.Identity(UUID.randomUUID(),"ADMIN",false,1),"test"));
  var mvc=MockMvcBuilders.standaloneSetup(new TaskStatisticsController(repo,Clock.fixed(now,ZoneOffset.UTC))).setControllerAdvice(new ApiExceptionHandler()).addFilters(new AdminTaskAccessFilter(identity,"server-test",new ObjectMapper().findAndRegisterModules())).build();var path="/api/v1/admin/statistics/tasks";var cookie=new Cookie("AISTOCK_SESSION","admin");
  mvc.perform(get(path)).andExpect(status().isUnauthorized());mvc.perform(get(path).cookie(cookie)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(get(path).cookie(cookie,cookie)).andExpect(status().isUnauthorized());mvc.perform(get(path).cookie(cookie).with(r->{r.addHeader("Cookie","x=1");r.addHeader("Cookie","y=2");return r;})).andExpect(status().isUnauthorized());
  when(identity.resolve(any(),any())).thenReturn(ApiResponse.success(new SessionIdentityClient.Identity(UUID.randomUUID(),"USER",false,1),"test"));mvc.perform(get(path).cookie(cookie).header("X-Role","ADMIN")).andExpect(status().isForbidden());
  mvc.perform(post(path).cookie(cookie)).andExpect(status().isNotFound());verify(repo,times(1)).summary(any(),any());
 }
}
