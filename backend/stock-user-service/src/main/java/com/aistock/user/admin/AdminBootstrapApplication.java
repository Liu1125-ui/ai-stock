package com.aistock.user.admin;

import com.aistock.common.BusinessException;
import java.time.Clock;
import java.util.Map;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Separate non-web context: no component scan, schedulers, public API or default security user. */
@Configuration @Profile("admin-bootstrap")
@EnableAutoConfiguration(exclude={org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
public class AdminBootstrapApplication {
    public static int execute(){
        var app=new SpringApplication(AdminBootstrapApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);app.setAdditionalProfiles("admin-bootstrap");
        app.addInitializers(context->context.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("bootstrapSafety",Map.of(
                        "spring.cloud.nacos.discovery.enabled",false,"spring.cloud.nacos.discovery.register-enabled",false))));
        app.setDefaultProperties(Map.of("spring.cloud.nacos.discovery.enabled","false","spring.cloud.nacos.discovery.register-enabled","false",
                "spring.main.banner-mode","off","logging.level.root","ERROR"));
        try(var context=app.run()){
            var command=new AdminBootstrapCommand(context.getBean(JdbcTemplate.class),Clock.systemUTC());
            command.initialize(System.getenv("AISTOCK_ADMIN_EMAIL"),System.getenv("AISTOCK_ADMIN_PASSWORD"),System.getenv("AISTOCK_ADMIN_NICKNAME"),"local-admin-bootstrap");
            System.out.println("ADMIN_BOOTSTRAP_OK");return 0;
        }catch(BusinessException rejected){System.err.println("ADMIN_BOOTSTRAP_FAILED: "+rejected.code());return 1;}
        catch(Exception unavailable){System.err.println("ADMIN_BOOTSTRAP_FAILED: configuration or database unavailable");return 1;}
    }
}
