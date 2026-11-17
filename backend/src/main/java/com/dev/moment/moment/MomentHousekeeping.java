package com.dev.moment.moment;

import com.dev.moment.job.MediaJobService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Job định kỳ của khoảnh khắc (DB mục 9): hết hạn khoảnh khắc chia sẻ, dọn bản nháp, dọn khoảnh khắc lỗi.
 * Mỗi lô một transaction ngắn; file trên storage xóa qua việc DELETE_OBJECTS tạo cùng transaction, không gọi storage
 * trong transaction (CLAUDE.md). Lịch chạy ở HousekeepingScheduler; test gọi thẳng các phương thức này.
 */
@Component
public class MomentHousekeeping {

    /** DB mục 4: bản nháp quá 1 giờ chưa tải xong thì xóa. */
    static final Duration DRAFT_LIFETIME = Duration.ofHours(1);
    /** D-029: khoảnh khắc lỗi quá 7 ngày chưa thử lại thì xóa. */
    static final Duration FAILED_LIFETIME = Duration.ofDays(7);
    private static final int BATCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(MomentHousekeeping.class);

    private final MomentRepository moments;
    private final MomentMediaRepository media;
    private final MediaJobService mediaJobService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    MomentHousekeeping(MomentRepository moments, MomentMediaRepository media, MediaJobService mediaJobService,
            TransactionTemplate transactionTemplate, Clock clock) {
        this.moments = moments;
        this.media = media;
        this.mediaJobService = mediaJobService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /** BR-14: khoảnh khắc chia sẻ quá 24 giờ sang EXPIRED, bỏ file, giữ bản ghi. Trả số khoảnh khắc đã đổi. */
    public int expireShared() {
        Instant now = clock.instant();
        int total = inBatches(() -> moments.lockExpiredShared(now, BATCH_SIZE), ids -> {
            Map<UUID, List<String>> keys = objectKeys(ids);
            media.deleteAllByMomentIdIn(ids);
            moments.markExpired(ids);
            keys.forEach(mediaJobService::enqueueDeleteObjects);
        });
        if (total > 0) {
            log.info("Đã chuyển {} khoảnh khắc chia sẻ sang hết hạn", total);
        }
        return total;
    }

    /** DB mục 4: xóa bản nháp tạo quá 1 giờ, kèm file đã lỡ tải lên. */
    public int deleteStaleDrafts() {
        Instant cutoff = clock.instant().minus(DRAFT_LIFETIME);
        int total = inBatches(() -> moments.lockStale(Moment.Status.DRAFT.name(), cutoff, BATCH_SIZE), this::delete);
        if (total > 0) {
            log.info("Đã xóa {} bản nháp quá hạn", total);
        }
        return total;
    }

    /** D-029: xóa khoảnh khắc FAILED tạo quá 7 ngày ở mọi chế độ, kèm file. */
    public int deleteStaleFailed() {
        Instant cutoff = clock.instant().minus(FAILED_LIFETIME);
        int total = inBatches(() -> moments.lockStale(Moment.Status.FAILED.name(), cutoff, BATCH_SIZE), this::delete);
        if (total > 0) {
            log.info("Đã xóa {} khoảnh khắc lỗi quá hạn", total);
        }
        return total;
    }

    private void delete(List<UUID> ids) {
        Map<UUID, List<String>> keys = objectKeys(ids);
        moments.deleteAllByIdIn(ids);
        keys.forEach(mediaJobService::enqueueDeleteObjects);
    }

    private Map<UUID, List<String>> objectKeys(List<UUID> ids) {
        return media.findAllByMomentIdIn(ids).stream().collect(Collectors.groupingBy(MomentMedia::getMomentId,
                Collectors.mapping(MomentMedia::getObjectKey, Collectors.toList())));
    }

    // Lấy và xử lý từng lô, mỗi lô một transaction, cho tới khi một lô không đầy (đã hết việc).
    private int inBatches(Supplier<List<UUID>> lockBatch, Consumer<List<UUID>> work) {
        int total = 0;
        int handled;
        do {
            Integer count = transactionTemplate.execute(status -> {
                List<UUID> ids = lockBatch.get();
                if (!ids.isEmpty()) {
                    work.accept(ids);
                }
                return ids.size();
            });
            handled = count == null ? 0 : count;
            total += handled;
        } while (handled == BATCH_SIZE);
        return total;
    }
}
