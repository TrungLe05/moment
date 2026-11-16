package com.dev.moment.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.dev.moment.IntegrationTest;
import com.dev.moment.media.ObjectStorage;
import com.dev.moment.support.MutableClock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** T-132: hàng đợi media_jobs (ADR 0003, DB mục 7 "Lấy việc trong hàng đợi"). */
@IntegrationTest
class MediaWorkerTests {

    @Autowired
    private MediaWorker worker;
    @Autowired
    private MediaJobService jobs;
    @Autowired
    private MediaJobRepository repository;
    @Autowired
    private ObjectStorage storage;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock.reset();
        jdbcTemplate.update("UPDATE media_jobs SET status = 'DONE' WHERE status IN ('PENDING', 'RUNNING')");
    }

    @Test
    void shouldDeleteObjectsListedInDeleteJob() throws Exception {
        String first = storedObject();
        String second = storedObject();
        transactionTemplate.executeWithoutResult(status -> jobs.enqueueDeleteObjects(null, List.of(first, second)));

        assertThat(worker.drain()).isEqualTo(1);

        assertThat(storage.find(first)).isEmpty();
        assertThat(storage.find(second)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM media_jobs WHERE type = 'DELETE_OBJECTS' ORDER BY id DESC LIMIT 1", String.class))
                .isEqualTo("DONE");
    }

    // Hai worker cùng lấy việc: việc đang bị khóa thì bỏ qua sang việc khác, không chờ, không lấy trùng.
    @Test
    void shouldSkipJobLockedByAnotherWorker() throws Exception {
        transactionTemplate.executeWithoutResult(status -> {
            jobs.enqueueDeleteObjects(null, List.of("missing/" + UUID.randomUUID()));
            jobs.enqueueDeleteObjects(null, List.of("missing/" + UUID.randomUUID()));
        });
        CountDownLatch claimed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Long> holder = CompletableFuture.supplyAsync(() -> transactionTemplate.execute(status -> {
            long id = repository.claimNext(clock.instant()).orElseThrow().getId();
            claimed.countDown();
            await(release);
            return id;
        }));
        assertThat(claimed.await(10, TimeUnit.SECONDS)).isTrue();

        Optional<Long> other = transactionTemplate.execute(status ->
                repository.claimNext(clock.instant()).map(MediaJob::getId));
        release.countDown();
        long held = holder.get(10, TimeUnit.SECONDS);

        assertThat(other).isPresent();
        assertThat(other.get()).isNotEqualTo(held);
    }

    @Test
    void shouldPickJobsInRunAfterOrderAndOnlyWhenDue() {
        transactionTemplate.executeWithoutResult(status -> jobs.enqueueDeleteObjects(null, List.of("a/" + UUID.randomUUID())));
        clock.advance(java.time.Duration.ofSeconds(1));
        transactionTemplate.executeWithoutResult(status -> jobs.enqueueDeleteObjects(null, List.of("b/" + UUID.randomUUID())));
        clock.advance(java.time.Duration.ofSeconds(-1));

        Optional<MediaJob> first = transactionTemplate.execute(status -> repository.claimNext(clock.instant()));
        Optional<MediaJob> notDue = transactionTemplate.execute(status -> repository.claimNext(clock.instant()));

        assertThat(first).isPresent();
        assertThat(first.get().getAttempts()).isEqualTo((short) 1);
        assertThat(notDue).isEmpty();
    }

    private String storedObject() throws Exception {
        String key = "test/" + UUID.randomUUID() + ".txt";
        Path file = Files.createTempFile("moment-job-", ".txt");
        try {
            Files.writeString(file, "x", StandardCharsets.UTF_8);
            storage.upload(key, file, "text/plain");
        } finally {
            Files.deleteIfExists(file);
        }
        assertThat(storage.find(key)).isPresent();
        return key;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
