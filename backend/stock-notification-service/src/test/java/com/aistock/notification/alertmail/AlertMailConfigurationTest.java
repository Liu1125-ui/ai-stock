package com.aistock.notification.alertmail;
import org.junit.jupiter.api.Test;import org.springframework.mock.env.MockEnvironment;import static org.junit.jupiter.api.Assertions.*;
class AlertMailConfigurationTest {
 @Test void defaultsOffAndMissingSmtpCannotAdvertiseEnabled(){var env=new MockEnvironment();var config=new AlertMailConfiguration();try(var smtp=config.alertMailTransport(env)){assertFalse(smtp.ready());}assertFalse(config.policy(env).enabled());env.setProperty("aistock.alert.mail-worker-enabled","true");assertTrue(config.policy(env).enabled());try(var smtp=config.alertMailTransport(env)){assertFalse(smtp.ready());}}
 @Test void invalidLeaseAttemptsTtlAndPollFailBeforeStartingWorker(){var config=new AlertMailConfiguration();for(String key:new String[]{"aistock.alert.mail-max-attempts","aistock.alert.mail-ttl-seconds","aistock.alert.mail-lease-seconds","aistock.alert.mail-poll-ms","aistock.alert.mail-retry-initial-seconds","aistock.alert.mail-retry-later-seconds"})assertThrows(IllegalArgumentException.class,()->config.policy(new MockEnvironment().withProperty(key,"0")));}
}
