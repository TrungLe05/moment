package com.dev.moment.job;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Chỉ container worker (profile worker, có ffmpeg) hỏi hàng đợi định kỳ; container API thì không (ARCHITECTURE.md mục 2).
 * Lượt sau bắt đầu sau khi lượt trước xong (fixedDelay), nên một worker không chạy hai việc cùng lúc.
 */
@Configuration(proxyBeanMethods = false)
@Profile("worker")
@EnableScheduling
class MediaWorkerScheduler {

    private final MediaWorker worker;

    MediaWorkerScheduler(MediaWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${moment.worker.poll-interval:PT1S}")
    void poll() {
        worker.drain();
    }
}
