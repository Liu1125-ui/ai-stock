package com.aistock.user.registration;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
public class RegistrationConfiguration {
    @Bean Clock authClock() { return Clock.systemUTC(); }
    @Bean RegistrationPayloadCodec registrationPayloadCodec(Environment env) { return new RegistrationPayloadCodec(HexFormat.of().parseHex(env.getRequiredProperty("aistock.auth.payload-key"))); }
    @Bean RegistrationService registrationService(JdbcTemplate jdbc,Environment env,Clock authClock,MailDeliveryClient client) {
        return new RegistrationService(jdbc,HexFormat.of().parseHex(env.getRequiredProperty("aistock.auth.digest-key")),HexFormat.of().parseHex(env.getRequiredProperty("aistock.auth.payload-key")),authClock,client,env.getRequiredProperty("aistock.internal.token"));
    }
    @Bean EmailChangeService emailChangeService(JdbcTemplate jdbc,Environment env,RegistrationPayloadCodec codec,Clock authClock,RegistrationService registration) {
        return new EmailChangeService(jdbc,HexFormat.of().parseHex(env.getRequiredProperty("aistock.auth.digest-key")),codec,authClock,registration);
    }
    @Bean RegistrationDispatcher registrationDispatcher(JdbcTemplate jdbc,RegistrationPayloadCodec codec,MailDeliveryClient client,Environment env) {
        return new RegistrationDispatcher(jdbc,codec,client,env.getRequiredProperty("aistock.internal.token"));
    }
    @Bean DispatchScan dispatchScan(RegistrationDispatcher dispatcher,Clock authClock) { return new DispatchScan(dispatcher,authClock); }
    public static final class DispatchScan {
        private final RegistrationDispatcher dispatcher; private final Clock clock;
        DispatchScan(RegistrationDispatcher dispatcher,Clock clock) { this.dispatcher=dispatcher; this.clock=clock; }
        @Scheduled(fixedDelayString="${aistock.auth.dispatch-delay-ms:2000}") public void scan() {
            try { dispatcher.runOnce(clock.instant()); }
            catch(RuntimeException error) { org.slf4j.LoggerFactory.getLogger(DispatchScan.class).warn("Registration dispatch scan temporarily unavailable"); }
        }
    }
}
