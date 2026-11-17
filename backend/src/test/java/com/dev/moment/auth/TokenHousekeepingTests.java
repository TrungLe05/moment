package com.dev.moment.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.dev.moment.IntegrationTest;
import com.dev.moment.support.MutableClock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** T-161: dọn refresh token và token email đã hết hạn quá 7 ngày (DB mục 9, D-039). */
@IntegrationTest
class TokenHousekeepingTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MutableClock clock;
    @Autowired
    private TokenHousekeeping housekeeping;

    private UUID user;

    @BeforeEach
    void setUp() {
        clock.reset();
        user = jdbcTemplate.queryForObject("INSERT INTO users (email) VALUES (?) RETURNING id", UUID.class,
                UUID.randomUUID() + "@example.test");
    }

    @Test
    void shouldDeleteRefreshFamilyWhoseTokensAllExpiredMoreThanSevenDaysAgo() {
        UUID family = UUID.randomUUID();
        UUID first = insertRefresh(family, expiredDaysAgo(40));
        UUID last = insertRefresh(family, expiredDaysAgo(8));

        housekeeping.deleteExpiredTokens();

        assertThat(refreshExists(first)).isFalse();
        assertThat(refreshExists(last)).isFalse();
    }

    // Family còn token chưa quá hạn dọn thì giữ cả family: token đầu tiên là mốc auth_time (D-026), token cũ đã xoay
    // vòng còn dùng để phát hiện dùng lại (API mục 2).
    @Test
    void shouldKeepWholeFamilyWhileAnyTokenIsWithinRetention() {
        UUID family = UUID.randomUUID();
        UUID old = insertRefresh(family, expiredDaysAgo(40));
        UUID current = insertRefresh(family, clock.instant().plus(Duration.ofDays(20)));
        UUID recentlyExpired = insertRefresh(UUID.randomUUID(), expiredDaysAgo(6));

        housekeeping.deleteExpiredTokens();

        assertThat(refreshExists(old)).isTrue();
        assertThat(refreshExists(current)).isTrue();
        assertThat(refreshExists(recentlyExpired)).isTrue();
    }

    @Test
    void shouldDeleteEmailTokensExpiredMoreThanSevenDaysAgo() {
        UUID old = insertEmailToken(expiredDaysAgo(8));
        UUID recent = insertEmailToken(expiredDaysAgo(6));
        UUID valid = insertEmailToken(clock.instant().plus(Duration.ofHours(1)));

        housekeeping.deleteExpiredTokens();

        assertThat(emailTokenExists(old)).isFalse();
        assertThat(emailTokenExists(recent)).isTrue();
        assertThat(emailTokenExists(valid)).isTrue();
    }

    private Instant expiredDaysAgo(int days) {
        return clock.instant().minus(Duration.ofDays(days));
    }

    // Băm giả sinh lúc chạy (64 ký tự hex), không có giá trị cố định trong code.
    private static String randomHash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }

    private UUID insertRefresh(UUID family, Instant expiresAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, UUID.class, user, family, randomHash(), Timestamp.from(expiresAt),
                Timestamp.from(expiresAt.minus(Duration.ofDays(30))));
    }

    private UUID insertEmailToken(Instant expiresAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO email_tokens (user_id, purpose, token_hash, expires_at) VALUES (?, 'VERIFY_EMAIL', ?, ?)
                RETURNING id
                """, UUID.class, user, randomHash(), Timestamp.from(expiresAt));
    }

    private boolean refreshExists(UUID id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM refresh_tokens WHERE id = ?)", Boolean.class, id));
    }

    private boolean emailTokenExists(UUID id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM email_tokens WHERE id = ?)", Boolean.class, id));
    }
}
