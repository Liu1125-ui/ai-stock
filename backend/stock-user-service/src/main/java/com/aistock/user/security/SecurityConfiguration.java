package com.aistock.user.security;
import com.aistock.user.session.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;

@Configuration @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean CookieWriter cookieWriter(Environment env){return new CookieWriter(env.getProperty("aistock.auth.cookie-secure",Boolean.class,false));}
    @Bean CookieCsrfTokenRepository csrfRepository(Environment env){
        var repository=CookieCsrfTokenRepository.withHttpOnlyFalse();
        boolean secure=env.getProperty("aistock.auth.cookie-secure",Boolean.class,false);
        repository.setCookieCustomizer(cookie->cookie.path("/").sameSite("Lax").secure(secure));
        return repository;
    }
    @Bean ObjectMapper securityMapper(){return new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);}
    @Bean org.springframework.web.servlet.config.annotation.WebMvcConfigurer accountJsonContract(ObjectMapper mapper){
        return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer(){
            @Override public void extendMessageConverters(java.util.List<org.springframework.http.converter.HttpMessageConverter<?>> converters){
                converters.stream().filter(c->c instanceof org.springframework.http.converter.json.MappingJackson2HttpMessageConverter)
                        .forEach(c->((org.springframework.http.converter.json.MappingJackson2HttpMessageConverter)c).setObjectMapper(mapper));
            }
        };
    }
    @Bean SecurityErrorWriter securityErrors(ObjectMapper mapper){return new SecurityErrorWriter(mapper);}
    @Bean SecurityFilterChain accountSecurity(HttpSecurity http,SessionService sessions,SecurityErrorWriter errors,CookieCsrfTokenRepository csrf,Environment env)throws Exception{
        var origins=Set.copyOf(Arrays.stream(env.getProperty("aistock.auth.allowed-origins","http://127.0.0.1:15173,http://localhost:15173").split(",")).map(String::strip).toList());
        var cors=new CorsConfiguration();cors.setAllowedOrigins(List.copyOf(origins));cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET","POST","PATCH","PUT","DELETE","OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type","X-XSRF-TOKEN","X-Trace-Id"));
        var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/**",cors);
        var handler=new CsrfTokenRequestAttributeHandler();handler.setCsrfRequestAttributeName("_csrf");
        http.cors(config->config.configurationSource(source))
                .csrf(config->config.csrfTokenRepository(csrf).csrfTokenRequestHandler(handler).ignoringRequestMatchers("/internal/**"))
                .sessionManagement(config->config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(config->config.disable()).httpBasic(config->config.disable()).logout(config->config.disable())
                .requestCache(config->config.disable())
                .exceptionHandling(config->config
                    .authenticationEntryPoint((request,response,error)->errors.write(request,response,401,"AUTH_REQUIRED","请先登录"))
                    .accessDeniedHandler((request,response,error)->errors.write(request,response,403,"ACCESS_DENIED","没有执行该操作的权限")))
                .authorizeHttpRequests(config->config
                    .requestMatchers("/api/v1/auth/**","/actuator/health","/api/v1/engineering/status","/internal/**").permitAll()
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/v1/users/**").authenticated().anyRequest().denyAll())
                .addFilterBefore(new OriginPolicy(origins,errors),CsrfFilter.class)
                .addFilterBefore(new SessionAuthenticationFilter(sessions,errors),UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
