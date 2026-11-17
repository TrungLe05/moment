package com.dev.moment.housekeeping;

import com.dev.moment.auth.TokenHousekeeping;
import com.dev.moment.job.MediaJobService;
import com.dev.moment.job.MediaWorker;
import com.dev.moment.moment.MomentHousekeeping;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Lịch các job định kỳ (DB mục 9). Chạy trong tiến trình API, một instance trong MVP (ARCHITECTURE.md mục 4);
 * container worker không chạy (profile worker chỉ hỏi hàng đợi). Test tắt bằng moment.housekeeping.enabled=false
 * rồi gọi thẳng từng job. Lượt sau bắt đầu sau khi lượt trước xong (fixedDelay); lỗi của một lượt Spring ghi log,
 * lượt sau vẫn chạy. Chờ lần đầu để không chạy chồng lúc ứng dụng vừa khởi động.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!worker")
@ConditionalOnProperty(name = "moment.housekeeping.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
class HousekeepingScheduler {

    private final MomentHousekeeping moments;
    private final TokenHousekeeping tokens;
    private final MediaJobService jobs;
    private final MediaWorker worker;

    HousekeepingScheduler(MomentHousekeeping moments, TokenHousekeeping tokens, MediaJobService jobs,
            MediaWorker worker) {
        this.moments = moments;
        this.tokens = tokens;
        this.jobs = jobs;
        this.worker = worker;
    }

    // BR-14: hết hạn khoảnh khắc chia sẻ, mỗi 5 phút.
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
    void expireSharedMoments() {
        moments.expireShared();
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT15M")
    void deleteStaleDrafts() {
        moments.deleteStaleDrafts();
    }

    // D-029: dọn khoảnh khắc lỗi, mỗi ngày.
    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "P1D")
    void deleteStaleFailedMoments() {
        moments.deleteStaleFailed();
    }

    @Scheduled(initialDelayString = "PT4M", fixedDelayString = "P1D")
    void deleteExpiredTokens() {
        tokens.deleteExpiredTokens();
    }

    // Dọn việc DONE mỗi ngày như DB mục 9; trả việc RUNNING bị kẹt về hàng đợi ở chu kỳ 5 phút, vì chờ một ngày thì
    // khoảnh khắc của người dùng treo ở trạng thái đang xử lý quá lâu (D-039).
    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "P1D")
    void deleteFinishedJobs() {
        jobs.deleteFinishedJobs();
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
    void releaseStuckJobs() {
        worker.releaseStuckJobs();
    }
}
