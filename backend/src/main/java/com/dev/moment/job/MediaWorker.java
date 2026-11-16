package com.dev.moment.job;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lấy việc từ media_jobs và chạy (ADR 0003). Lịch chạy định kỳ chỉ bật ở profile worker
 * ({@link MediaWorkerScheduler}); bean này có ở mọi profile để test gọi trực tiếp.
 */
@Component
public class MediaWorker {

    /** ADR 0003: thử tối đa 3 lần, rồi khoảnh khắc chuyển FAILED (DB mục 4). */
    static final int MAX_ATTEMPTS = 3;
    /** Chờ trước lần thử lại thứ n: 15 giây × 4^(n−1), tức 15 giây rồi 60 giây. */
    static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(15);
    /** Lý do khi hết lượt thử vì lỗi tạm thời (D-033). */
    public static final String RETRIES_EXHAUSTED = "PROCESSING_ERROR";
    // Một lượt chạy không giữ worker quá lâu, để lượt sau còn thấy việc mới.
    private static final int MAX_JOBS_PER_DRAIN = 20;
    private static final int MAX_ERROR_LENGTH = 1000;

    private static final Logger log = LoggerFactory.getLogger(MediaWorker.class);

    private final Map<MediaJobType, MediaJobHandler> handlers = new EnumMap<>(MediaJobType.class);
    private final MediaJobRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    MediaWorker(List<MediaJobHandler> handlers, MediaJobRepository repository, TransactionTemplate transactionTemplate,
            JsonMapper jsonMapper, Clock clock) {
        for (MediaJobHandler handler : handlers) {
            if (this.handlers.put(handler.type(), handler) != null) {
                throw new IllegalStateException("Hai bộ xử lý cho cùng loại việc " + handler.type());
            }
        }
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** Chạy các việc đã tới giờ, lần lượt từng việc; trả số việc đã lấy. */
    public int drain() {
        int count = 0;
        while (count < MAX_JOBS_PER_DRAIN && runNext()) {
            count++;
        }
        return count;
    }

    private boolean runNext() {
        // Transaction lấy việc kết thúc ngay sau khi đổi sang RUNNING; xử lý nằm ngoài transaction (CLAUDE.md).
        Optional<MediaJob> claimed = transactionTemplate.execute(status -> repository.claimNext(clock.instant()));
        if (claimed == null || claimed.isEmpty()) {
            return false;
        }
        run(claimed.get());
        return true;
    }

    private void run(MediaJob job) {
        MediaJobHandler handler = handlers.get(job.getType());
        if (handler == null) {
            fail(job, null, RETRIES_EXHAUSTED, "Không có bộ xử lý cho " + job.getType());
            return;
        }
        try {
            handler.handle(job.getMomentId(), jsonMapper.readTree(job.getPayload()));
            transactionTemplate.executeWithoutResult(status -> repository.markDone(job.getId(), clock.instant()));
        } catch (NonRetryableJobException rejected) {
            log.info("Việc {} ({}) thất bại, không thử lại: {}", job.getId(), job.getType(), rejected.reason());
            fail(job, handler, rejected.reason(), describe(rejected));
        } catch (RuntimeException exception) {
            if (job.getAttempts() >= MAX_ATTEMPTS) {
                log.warn("Việc {} ({}) lỗi lần {}, dừng thử", job.getId(), job.getType(), job.getAttempts(), exception);
                fail(job, handler, RETRIES_EXHAUSTED, describe(exception));
            } else {
                Instant runAfter = clock.instant().plus(retryDelay(job.getAttempts()));
                log.warn("Việc {} ({}) lỗi lần {}, thử lại lúc {}", job.getId(), job.getType(), job.getAttempts(),
                        runAfter, exception);
                transactionTemplate.executeWithoutResult(status -> repository.reschedule(job.getId(), runAfter,
                        describe(exception), clock.instant()));
            }
        }
    }

    // Đánh dấu việc và dữ liệu liên quan thất bại trong cùng một transaction.
    private void fail(MediaJob job, MediaJobHandler handler, String reason, String error) {
        transactionTemplate.executeWithoutResult(status -> {
            repository.markFailed(job.getId(), error, clock.instant());
            if (handler != null) {
                handler.onFailed(job.getMomentId(), reason);
            }
        });
    }

    static Duration retryDelay(int attempts) {
        return FIRST_RETRY_DELAY.multipliedBy(1L << (2 * (attempts - 1)));
    }

    // Lưu để chẩn đoán; thông điệp lỗi không chứa presigned URL hay nội dung người dùng.
    private static String describe(Throwable exception) {
        String text = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
