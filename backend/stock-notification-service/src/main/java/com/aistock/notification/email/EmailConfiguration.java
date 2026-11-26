package com.aistock.notification.email;

import java.time.Clock;
import java.util.HexFormat;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class EmailConfiguration {
    @Bean Clock emailClock() { return Clock.systemUTC(); }
    @Bean MailPayloadCodec mailPayloadCodec(Environment env) {
        return new MailPayloadCodec(HexFormat.of().parseHex(env.getRequiredProperty("aistock.mail.payload-key")),
                HexFormat.of().parseHex(env.getRequiredProperty("aistock.mail.digest-key")));
    }
    @Bean MailTransport mailTransport(Environment env) {
        var sender = new JavaMailSenderImpl();
        String host = env.getProperty("aistock.smtp.host", "");
        String username = env.getProperty("aistock.smtp.username", "");
        String password = env.getProperty("aistock.smtp.password", "");
        String from = env.getProperty("aistock.smtp.from", "");
        boolean ssl = env.getProperty("aistock.smtp.ssl-enabled", Boolean.class, true);
        boolean starttls = env.getProperty("aistock.smtp.starttls-required", Boolean.class, false);
        sender.setHost(host); sender.setPort(env.getProperty("aistock.smtp.port", Integer.class, 465));
        sender.setUsername(username); sender.setPassword(password); sender.setDefaultEncoding("UTF-8");
        var props = sender.getJavaMailProperties();
        props.setProperty("mail.smtp.auth", "true");
        props.setProperty("mail.smtp.ssl.enable", Boolean.toString(ssl));
        props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        props.setProperty("mail.smtp.starttls.enable", Boolean.toString(starttls));
        props.setProperty("mail.smtp.starttls.required", Boolean.toString(starttls));
        props.setProperty("mail.smtp.connectiontimeout", "5000");
        props.setProperty("mail.smtp.timeout", "5000"); props.setProperty("mail.smtp.writetimeout", "5000");
        boolean ready = !host.isBlank() && !username.isBlank() && !password.isBlank() && !from.isBlank() && (ssl || starttls);
        return new SmtpMailTransport(sender, from, ready);
    }
    @Bean EmailDeliveryService emailDeliveryService(JdbcTemplate jdbc, MailPayloadCodec codec, MailTransport transport, Clock emailClock) {
        return new EmailDeliveryService(jdbc, codec, transport, emailClock);
    }
    @Bean EmailWorker emailWorker(JdbcTemplate jdbc, MailPayloadCodec codec, MailTransport transport) { return new EmailWorker(jdbc, codec, transport); }
    @Bean EmailScan emailScan(EmailWorker worker, Clock emailClock) { return new EmailScan(worker, emailClock); }
    public static final class EmailScan {
        private final EmailWorker worker; private final Clock clock;
        EmailScan(EmailWorker worker, Clock clock) { this.worker = worker; this.clock = clock; }
        @Scheduled(fixedDelayString="${aistock.mail.scan-delay-ms:2000}") public void scan() {
            try { worker.runOnce(clock.instant()); }
            catch (RuntimeException unavailable) { org.slf4j.LoggerFactory.getLogger(EmailScan.class).warn("Email delivery scan temporarily unavailable"); }
        }
    }
}
