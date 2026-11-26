package com.aistock.user.support;

import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class AuthDatabaseFixture {
    private final JdbcTemplate jdbc;
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-30T00:00:00Z"));
    public AuthDatabaseFixture() {
        String url = System.getenv("AUTH_TEST_DB_URL");
        String password = System.getenv("AUTH_TEST_DB_PASSWORD");
        if (url == null || !url.equals("jdbc:mysql://127.0.0.1:13316/stock_user_test?serverTimezone=UTC") || password == null || password.isBlank()) {
            throw new IllegalStateException("Use the isolated auth test database runner; production URLs are rejected");
        }
        var source = new DriverManagerDataSource(url, "root", password);
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        jdbc = new JdbcTemplate(source);
        if (!"stock_user_test".equals(jdbc.queryForObject("select database()", String.class))) {
            throw new IllegalStateException("Not an isolated test database");
        }
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        // This fixture rejects every database except the explicitly disposable isolated test database.
        for(String table : new String[]{"position_operation_receipt","position","position_book","watchlist_market_queue","watchlist","user_audit","experiment_grant","login_session","registration_receipt","registration_dispatch","registration_challenge","user_account","auth_rate_bucket"}) {
            jdbc.update("delete from " + table);
        }
    }
    public JdbcTemplate jdbc() { return jdbc; }
    public MutableClock clock() { return clock; }
}
