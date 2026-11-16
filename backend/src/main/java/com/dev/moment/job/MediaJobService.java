package com.dev.moment.job;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo việc nền. Bắt buộc chạy trong transaction của bên gọi để việc và dữ liệu sinh ra nó
 * cùng commit hoặc cùng hủy, không có khoảnh khắc nào kẹt ở PROCESSING vì quên tạo việc (ARCHITECTURE.md mục 4).
 */
@Service
public class MediaJobService {

    private final MediaJobRepository repository;
    private final Clock clock;

    MediaJobService(MediaJobRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueProcessMoment(UUID momentId) {
        repository.save(new MediaJob("PROCESS_MOMENT", momentId, "{}", clock.instant()));
    }
}
