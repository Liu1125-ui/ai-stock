package com.aistock.user.account;

import com.aistock.common.BusinessException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** MS-01 database access only; public account views exclude credentials. */
public final class UserRepository {
    public record Account(UUID userId, String email, String nickname, String role,
                          String status, long credentialVersion, long revision, String avatarStyle) { }

    private static final String COLUMNS = "user_id,email_normalized,nickname,role_code,status,credential_version,revision,avatar_uri";
    private static final RowMapper<Account> MAPPER = (row, index) -> new Account(
            UUID.fromString(row.getString("user_id")), row.getString("email_normalized"),
            row.getString("nickname"), row.getString("role_code"), row.getString("status"),
            row.getLong("credential_version"), row.getLong("revision"), row.getString("avatar_uri"));
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Caller must have successfully verified the matching registration challenge. */
    public void insertVerifiedOrdinaryUser(UUID id, String email, String passwordHash,
                                          String nickname, Instant verifiedAt) {
        String normalized = EmailAddress.normalize(email);
        jdbc.update("""
                insert into user_account(user_id,email,email_normalized,email_verified_at,
                    password_hash,role_code,status,nickname,created_at)
                values (?,?,?,?,?,'USER','ENABLED',?,?)
                """, id.toString(), normalized, normalized, Timestamp.from(verifiedAt),
                passwordHash, validateNickname(nickname), Timestamp.from(verifiedAt));
    }

    public Optional<Account> findByEmail(String email) {
        return jdbc.query("select " + COLUMNS + " from user_account where email_normalized=?",
                MAPPER, EmailAddress.normalize(email)).stream().findFirst();
    }

    public Optional<Account> findById(UUID id) {
        return jdbc.query("select " + COLUMNS + " from user_account where user_id=?",
                MAPPER, id.toString()).stream().findFirst();
    }

    public Optional<Account> lockById(UUID id) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Account row lock requires an active transaction");
        }
        return jdbc.query("select " + COLUMNS + " from user_account where user_id=? for update",
                MAPPER, id.toString()).stream().findFirst();
    }

    public boolean rename(UUID id, String nickname, long revision) {
        return jdbc.update("update user_account set nickname=?,revision=revision+1 where user_id=? and revision=?",
                validateNickname(nickname), id.toString(), revision) == 1;
    }

    void updateAvatar(UUID id, String avatarStyle) {
        jdbc.update("update user_account set avatar_uri=? where user_id=?", avatarStyle, id.toString());
    }

    public static String validateNickname(String nickname) {
        String stored = nickname == null ? "" : nickname.strip();
        int length = stored.codePointCount(0, stored.length());
        if (length < 2 || length > 30 || nickname.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException("VALIDATION_FAILED", "昵称须为2至30个字符且不含控制字符", HttpStatus.BAD_REQUEST);
        }
        return stored;
    }
}
