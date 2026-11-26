package com.aistock.user.account;

import com.aistock.user.support.AuthDatabaseFixture;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("mysql")
class AuthDatabaseTest {
    @Test void databaseRejectsUnverifiedAccountsAndUnknownRoles() {
        var fixture = new AuthDatabaseFixture();
        var users = new UserRepository(fixture.jdbc());
        UUID id = UUID.randomUUID();
        users.insertVerifiedOrdinaryUser(id, UUID.randomUUID() + "@example.com", "$2a$12$" + "a".repeat(53), "测试用户", fixture.clock().instant());
        assertThrows(DataIntegrityViolationException.class, () -> fixture.jdbc().update(
                "update user_account set email_verified_at=null where user_id=?", id.toString()));
        // MySQL 3819 uses HY000: Spring's default translator does not classify CHECK violations.
        var roleError = assertThrows(org.springframework.jdbc.UncategorizedSQLException.class, () -> fixture.jdbc().update(
                "update user_account set role_code='ROOT' where user_id=?", id.toString()));
        assertEquals(3819, roleError.getSQLException().getErrorCode());
        var statusError = assertThrows(org.springframework.jdbc.UncategorizedSQLException.class, () -> fixture.jdbc().update(
                "update user_account set status='UNKNOWN' where user_id=?", id.toString()));
        assertEquals(3819, statusError.getSQLException().getErrorCode());
    }
    @Test void integrationFixtureRejectsProductionAndReadsRealMysql() {
        var fixture = new AuthDatabaseFixture();
        assertEquals("stock_user_test", fixture.jdbc().queryForObject("select database()", String.class));
        assertTrue(fixture.jdbc().queryForObject("select version()", String.class).startsWith("8."));
    }
}
