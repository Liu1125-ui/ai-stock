package com.aistock.prediction.security;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;
@Configuration
public class PredictionSecurityConfiguration {
    @Bean ObjectMapper predictionMapper(){return new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);}
    @Bean org.springframework.web.servlet.config.annotation.WebMvcConfigurer predictionJson(ObjectMapper mapper){return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer(){@Override public void extendMessageConverters(List<org.springframework.http.converter.HttpMessageConverter<?>> converters){converters.stream().filter(c->c instanceof org.springframework.http.converter.json.MappingJackson2HttpMessageConverter).forEach(c->((org.springframework.http.converter.json.MappingJackson2HttpMessageConverter)c).setObjectMapper(mapper));}};}
    @Bean CookieCsrfTokenRepository predictionCsrf(Environment env){var repo=CookieCsrfTokenRepository.withHttpOnlyFalse();repo.setCookieCustomizer(c->c.path("/").sameSite("Lax").secure(env.getProperty("aistock.auth.cookie-secure",Boolean.class,false)));return repo;}
    @Bean SecurityFilterChain predictionSecurity(HttpSecurity http,SessionIdentityClient client,ObjectMapper mapper,CookieCsrfTokenRepository csrf,@Value("${aistock.internal.token:}")String token,Environment env)throws Exception{
        var errors=new SecurityErrorWriter(mapper);var origins=Set.copyOf(Arrays.stream(env.getProperty("aistock.auth.allowed-origins","http://127.0.0.1:15173,http://localhost:15173").split(",")).map(String::strip).toList());
        var cors=new CorsConfiguration();cors.setAllowedOrigins(List.copyOf(origins));cors.setAllowCredentials(true);cors.setAllowedMethods(List.of("GET","POST","OPTIONS"));cors.setAllowedHeaders(List.of("Content-Type","X-XSRF-TOKEN","X-Trace-Id"));var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/**",cors);
        var handler=new CsrfTokenRequestAttributeHandler();handler.setCsrfRequestAttributeName("_csrf");
        http.cors(c->c.configurationSource(source)).csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(handler).ignoringRequestMatchers("/internal/**"))
            .sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable()).requestCache(c->c.disable())
            .exceptionHandling(c->c.authenticationEntryPoint((r,s,e)->errors.write(r,s,401,"AUTH_REQUIRED","请先登录")).accessDeniedHandler((r,s,e)->errors.write(r,s,403,"ACCESS_DENIED","没有执行该操作的权限")))
            .authorizeHttpRequests(c->c.requestMatchers("/internal/**","/actuator/health","/api/v1/system/status").permitAll().requestMatchers("/api/v1/admin/statistics/predictions","/api/v1/admin/models","/api/v1/admin/models/**").hasRole("ADMIN").requestMatchers("/api/v1/training-submissions","/api/v1/training-submissions/**","/api/v1/portfolio-questions","/api/v1/portfolio-questions/**","/api/v1/portfolio-analyses","/api/v1/portfolio-analyses/**","/api/v1/comparison-explanations","/api/v1/comparison-explanations/**","/api/v1/comparisons","/api/v1/comparisons/**","/api/v1/models","/api/v1/predictions","/api/v1/predictions/**","/api/v1/stock-analyses","/api/v1/stock-analyses/**","/api/v1/stock-questions","/api/v1/stock-questions/**").authenticated().anyRequest().denyAll())
            .addFilterBefore(new ModelRequestFilter(token,errors),CsrfFilter.class).addFilterBefore(new OriginPolicy(origins,errors),CsrfFilter.class).addFilterBefore(new PredictionSessionFilter(client,token,errors),UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
