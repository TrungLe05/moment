package com.dev.moment.job;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

interface MediaJobRepository extends JpaRepository<MediaJob, Long> {

    // Câu lấy việc ở DB mục 7: SKIP LOCKED để nhiều worker cùng chạy mà không lấy trùng, không chờ nhau.
    // Thời điểm lấy từ Clock thay cho now() để test được thời gian chờ thử lại.
    // Không đánh dấu @Modifying vì cần đọc lại dòng qua RETURNING; MANDATORY để khóa sống trong transaction của bên gọi.
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            UPDATE media_jobs SET status = 'RUNNING', locked_at = :now, attempts = attempts + 1, updated_at = :now
            WHERE id = (
                SELECT id FROM media_jobs
                WHERE status = 'PENDING' AND run_after <= :now
                ORDER BY run_after
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            RETURNING *
            """, nativeQuery = true)
    Optional<MediaJob> claimNext(@Param("now") Instant now);

    @Modifying
    @Query("""
            update MediaJob j set j.status = com.dev.moment.job.MediaJob.Status.DONE, j.lockedAt = null,
                j.lastError = null, j.updatedAt = :now
            where j.id = :id
            """)
    int markDone(@Param("id") long id, @Param("now") Instant now);

    @Modifying
    @Query("""
            update MediaJob j set j.status = com.dev.moment.job.MediaJob.Status.PENDING, j.lockedAt = null,
                j.runAfter = :runAfter, j.lastError = :error, j.updatedAt = :now
            where j.id = :id
            """)
    int reschedule(@Param("id") long id, @Param("runAfter") Instant runAfter, @Param("error") String error,
            @Param("now") Instant now);

    @Modifying
    @Query("""
            update MediaJob j set j.status = com.dev.moment.job.MediaJob.Status.FAILED, j.lockedAt = null,
                j.lastError = :error, j.updatedAt = :now
            where j.id = :id
            """)
    int markFailed(@Param("id") long id, @Param("error") String error, @Param("now") Instant now);
}
