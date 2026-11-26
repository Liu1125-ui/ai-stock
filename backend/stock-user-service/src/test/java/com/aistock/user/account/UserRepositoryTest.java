package com.aistock.user.account;
import com.aistock.user.support.AuthDatabaseFixture;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("mysql")
class UserRepositoryTest {
    @Test void nicknameLimitsApplyAfterTrimmingAndSupportUnicodeCharacters() {
        var db = new AuthDatabaseFixture();
        var users = new UserRepository(db.jdbc());
        UUID id = UUID.randomUUID();
        users.insertVerifiedOrdinaryUser(id, UUID.randomUUID() + "@example.com", "$2a$12$" + "a".repeat(53), "测试用户", db.clock().instant());
        assertThrows(com.aistock.common.BusinessException.class, () -> users.insertVerifiedOrdinaryUser(
                UUID.randomUUID(), UUID.randomUUID() + "@example.com", "$2a$12$" + "a".repeat(53), "A", db.clock().instant()));
        assertThrows(com.aistock.common.BusinessException.class, () -> users.rename(id, " A ", 0));
        assertThrows(com.aistock.common.BusinessException.class, () -> users.rename(id, "中".repeat(31), 0));
        assertTrue(users.rename(id, "  昵称  ", 0));
        assertEquals("昵称", users.findById(id).orElseThrow().nickname());
        assertTrue(users.rename(id, "😀".repeat(30), 1));
    }
    @Test void accountLockRequiresTransactionAndFindsTheAccount() {
        var db = new AuthDatabaseFixture();
        var users = new UserRepository(db.jdbc());
        UUID id = UUID.randomUUID();
        users.insertVerifiedOrdinaryUser(id, UUID.randomUUID() + "@example.com", "$2a$12$" + "a".repeat(53), "测试用户", db.clock().instant());
        assertThrows(IllegalStateException.class, () -> users.lockById(id));
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(db.jdbc().getDataSource()));
        transaction.executeWithoutResult(status -> assertEquals(id, users.lockById(id).orElseThrow().userId()));
    }
    @Test void verifiedAccountIsOrdinaryAndEmailIsUnique() {
        var db = new AuthDatabaseFixture();
        var users = new UserRepository(db.jdbc());
        String email = UUID.randomUUID() + "@example.com";
        UUID id = UUID.randomUUID();
        users.insertVerifiedOrdinaryUser(id, email, "$2a$12$" + "a".repeat(53), "测试用户", db.clock().instant());
        var user = users.findByEmail(email.toUpperCase(java.util.Locale.ROOT)).orElseThrow();
        assertEquals(id, user.userId());
        assertEquals("USER", user.role());
        assertEquals("ENABLED", user.status());
        assertThrows(DataIntegrityViolationException.class, () -> users.insertVerifiedOrdinaryUser(UUID.randomUUID(), email.toUpperCase(java.util.Locale.ROOT), "$2a$12$" + "a".repeat(53), "另一用户", db.clock().instant()));
        assertTrue(users.rename(id, "新昵称", 0));
        assertFalse(users.rename(id, "旧页面", 0));
        assertEquals("新昵称", users.findById(id).orElseThrow().nickname());
    }
    @Test void onlyOneActiveExperimentGrantCanExist() {
        var db = new AuthDatabaseFixture();
        var users = new UserRepository(db.jdbc());
        UUID id = UUID.randomUUID();
        users.insertVerifiedOrdinaryUser(id, UUID.randomUUID() + "@example.com", "$2a$12$" + "a".repeat(53), "测试用户", db.clock().instant());
        String sql = "insert into experiment_grant(grant_id,user_id,granted_by,state,granted_at) values (?,?,?,'ACTIVE',?)";
        db.jdbc().update(sql, UUID.randomUUID().toString(), id.toString(), id.toString(), java.sql.Timestamp.from(db.clock().instant()));
        assertThrows(DataIntegrityViolationException.class, () -> db.jdbc().update(sql, UUID.randomUUID().toString(), id.toString(), id.toString(), java.sql.Timestamp.from(db.clock().instant())));
        db.jdbc().update("update experiment_grant set state='REVOKED',revoked_at=? where user_id=?", java.sql.Timestamp.from(db.clock().instant()), id.toString());
        assertDoesNotThrow(() -> db.jdbc().update(sql, UUID.randomUUID().toString(), id.toString(), id.toString(), java.sql.Timestamp.from(db.clock().instant())));
    }
}
