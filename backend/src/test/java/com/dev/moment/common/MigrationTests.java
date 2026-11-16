package com.dev.moment.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dev.moment.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class MigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateAllElevenTablesFromDatabaseDesign() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                """, String.class);
        assertThat(tables).containsExactlyInAnyOrder(
                "users", "auth_identities", "refresh_tokens", "email_tokens", "invite_codes", "friendships",
                "moments", "moment_recipients", "moment_media", "reactions", "media_jobs");
    }

    @Test
    void shouldRejectDuplicateEmailIgnoringCase() {
        jdbcTemplate.update("INSERT INTO users (email) VALUES ('Case.Test@Example.com')");
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO users (email) VALUES ('case.test@example.com')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbcTemplate.update("DELETE FROM users WHERE lower(email) = 'case.test@example.com'");
    }

    @Test
    void shouldRejectUsernameOutsideAllowedFormat() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (email, username) VALUES ('fmt@example.com', 'Bad Name')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
