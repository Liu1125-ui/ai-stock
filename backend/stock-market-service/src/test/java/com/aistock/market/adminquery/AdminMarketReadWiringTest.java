package com.aistock.market.adminquery;
import com.aistock.market.security.*;import org.junit.jupiter.api.Test;import org.springframework.context.annotation.AnnotationConfigApplicationContext;import org.springframework.core.env.MapPropertySource;import org.springframework.boot.web.servlet.FilterRegistrationBean;import java.util.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class AdminMarketReadWiringTest {
 @Test void actualConfigurationRegistersPreciseNewFiltersWithoutBroadeningStatistics(){try(var c=new AnnotationConfigApplicationContext()){c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("aistock.internal.token","server-test")));c.registerBean(SessionIdentityClient.class,()->mock(SessionIdentityClient.class));c.register(MarketSecurityConfiguration.class);c.refresh();var access=c.getBean("adminMarketDataAccess",FilterRegistrationBean.class);var cors=c.getBean("adminMarketDataCors",FilterRegistrationBean.class);assertEquals(Set.of("/api/v1/admin/market-data","/api/v1/admin/market-data/*"),Set.copyOf(access.getUrlPatterns()));assertEquals(Set.copyOf(access.getUrlPatterns()),Set.copyOf(cors.getUrlPatterns()));assertEquals(-90,access.getOrder());assertEquals(-100,cors.getOrder());assertFalse(c.getBean("statisticsAccess",FilterRegistrationBean.class).getUrlPatterns().contains("/api/v1/admin/market-data/*"));}}
 @Test void configuredPreflightStopsBeforeIdentityAndRepository() throws Exception {
  try(var c=new AnnotationConfigApplicationContext()) {
   c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("aistock.internal.token","server-test")));
   var identity=mock(SessionIdentityClient.class);
   c.registerBean(SessionIdentityClient.class,()->identity);c.register(MarketSecurityConfiguration.class);c.refresh();
   var repository=mock(AdminMarketReadRepository.class);
   var cors=(jakarta.servlet.Filter)c.getBean("adminMarketDataCors",FilterRegistrationBean.class).getFilter();
   var access=(jakarta.servlet.Filter)c.getBean("adminMarketDataAccess",FilterRegistrationBean.class).getFilter();
   var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new AdminMarketReadController(repository,java.time.Clock.systemUTC())).addFilters(cors,access).build();
   for(var path:List.of("/api/v1/admin/market-data","/api/v1/admin/market-data/SH600030/versions","/api/v1/admin/market-data/SH600030/versions/00000000-0000-0000-0000-000000000001")) {
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options(path).header("Origin","http://127.0.0.1:15173").header("Access-Control-Request-Method","GET")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"));
   }
   verifyNoInteractions(identity,repository);
  }
 }
}
