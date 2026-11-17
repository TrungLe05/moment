package com.dev.moment.job;

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

/** T-161: dọn hàng đợi media_jobs (DB mục 9, ADR 0003). */
@IntegrationTest
class QueueHousekeepingTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MutableClock clock;
    @Autowired
    private MediaJobService jobService;
    @Autowired
    private MediaWorker worker;

    @BeforeEach
    void setUp() {
        clock.reset();
    }

    @Test
    void shouldDeleteDoneJobsOlderThanSevenDaysOnly() {
        Instant now = clock.instant();
        long oldDone = insertJob("DONE", 1, null, now.minus(Duration.ofDays(7)).minusSeconds(60));
        long recentDone = insertJob("DONE", 1, null, now.minus(Duration.ofDays(6)));
        long oldFailed = insertJob("FAILED", 3, null, now.minus(Duration.ofDays(30)));

        jobService.deleteFinishedJobs();

        assertThat(jobExists(oldDone)).isFalse();
        assertThat(jobExists(recentDone)).isTrue();
        // Việc FAILED giữ lại để chẩn đoán.
        assertThat(jobExists(oldFailed)).isTrue();
    }

    // Worker chết giữa chừng: việc RUNNING bị khóa quá 10 phút trả về PENDING để worker khác lấy.
    @Test
    void shouldReturnJobsStuckInRunningForMoreThanTenMinutesToPending() {
        Instant now = clock.instant();
        long stuck = insertJob("RUNNING", 1, now.minus(Duration.ofMinutes(11)), now.minus(Duration.ofMinutes(11)));
        long busy = insertJob("RUNNING", 1, now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(5)));

        worker.releaseStuckJobs();

        assertThat(jobStatus(stuck)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT locked_at IS NULL AND run_after <= ? FROM media_jobs WHERE id = ?",
                Boolean.class, Timestamp.from(now), stuck)).isTrue();
        assertThat(jobStatus(busy)).isEqualTo("RUNNING");
    }

    // ADR 0003: thử tối đa 3 lần. Việc đã dùng hết lượt mà còn kẹt thì thất bại hẳn, khoảnh khắc sang FAILED.
    @Test
    void shouldFailStuckJobThatUsedAllAttemptsAndMarkItsMomentFailed() {
        Instant now = clock.instant();
        UUID owner = insertUser();
        UUID moment = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO moments (id, owner_id, media_type, source, mode, audience, status, expires_at, kept_at)
                VALUES (?, ?, 'PHOTO', 'CAMERA', 'KEPT', 'ALL', 'PROCESSING', ?, ?)
                """, moment, owner, Timestamp.from(now.plus(Duration.ofHours(24))), Timestamp.from(now));
        long stuck = insertJob("RUNNING", MediaWorker.MAX_ATTEMPTS, now.minus(Duration.ofMinutes(11)),
                now.minus(Duration.ofMinutes(11)), moment);

        worker.releaseStuckJobs();

        assertThat(jobStatus(stuck)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForMap("SELECT status, failure_reason FROM moments WHERE id = ?", moment))
                .containsEntry("status", "FAILED").containsEntry("failure_reason", MediaWorker.RETRIES_EXHAUSTED);
    }

    private long insertJob(String status, int attempts, Instant lockedAt, Instant updatedAt) {
        return insertJob(status, attempts, lockedAt, updatedAt, null);
    }

    private long insertJob(String status, int attempts, Instant lockedAt, Instant updatedAt, UUID momentId) {
        String type = momentId == null ? "DELETE_OBJECTS" : "PROCESS_MOMENT";
        return jdbcTemplate.queryForObject("""
                INSERT INTO media_jobs (type, moment_id, payload, status, attempts, run_after, locked_at, created_at,
                    updated_at)
                VALUES (?, ?, '{"keys": []}'::jsonb, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, type, momentId, status, attempts, Timestamp.from(updatedAt),
                lockedAt == null ? null : Timestamp.from(lockedAt), Timestamp.from(updatedAt),
                Timestamp.from(updatedAt));
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, email) VALUES (?, ?)", id, id + "@example.test");
        return id;
    }

    private boolean jobExists(long id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM media_jobs WHERE id = ?)", Boolean.class, id));
    }

    private String jobStatus(long id) {
        return jdbcTemplate.queryForObject("SELECT status FROM media_jobs WHERE id = ?", String.class, id);
    }
}
