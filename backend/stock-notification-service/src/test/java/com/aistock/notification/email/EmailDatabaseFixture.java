package com.aistock.notification.email;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

final class EmailDatabaseFixture {
    final JdbcTemplate jdbc;
    final Clock clock = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC);
    EmailDatabaseFixture() {
        String url = System.getenv("MAIL_TEST_DB_URL");
        String password = System.getenv("MAIL_TEST_DB_PASSWORD");
        if (!"jdbc:mysql://127.0.0.1:13316/stock_notification_test?serverTimezone=UTC".equals(url)
                || password == null || password.isBlank()) throw new IllegalStateException("Use isolated mail database runner");
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "root", password));
        if (!"stock_notification_test".equals(jdbc.queryForObject("select database()", String.class))) throw new IllegalStateException("Wrong test database");
        Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration").load().migrate();
        jdbc.update("delete from email_delivery");
    }
    MailPayloadCodec codec() { return new MailPayloadCodec(new byte[32], "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII)); }
}
