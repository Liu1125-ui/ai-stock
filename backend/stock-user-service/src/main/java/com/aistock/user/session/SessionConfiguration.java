package com.aistock.user.session;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
@Configuration
public class SessionConfiguration {
    @Bean SessionService sessionService(JdbcTemplate jdbc,Environment env,Clock authClock){return new SessionService(jdbc,HexFormat.of().parseHex(env.getRequiredProperty("aistock.auth.digest-key")),authClock);}
}
