package com.dev.moment.job;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tạo việc nền. Bắt buộc chạy trong transaction của bên gọi để việc và dữ liệu sinh ra nó
 * cùng commit hoặc cùng hủy, không có khoảnh khắc nào kẹt ở PROCESSING vì quên tạo việc (ARCHITECTURE.md mục 4).
 */
@Service
public class MediaJobService {

    private final MediaJobRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    MediaJobService(MediaJobRepository repository, JsonMapper jsonMapper, Clock clock) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueProcessMoment(UUID momentId) {
        repository.save(new MediaJob(MediaJobType.PROCESS_MOMENT, momentId, "{}", clock.instant()));
    }

    /** Xóa file ở storage sau khi transaction xóa dữ liệu commit; momentId chỉ để tra cứu, có thể null. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueDeleteObjects(UUID momentId, Collection<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        String payload = jsonMapper.writeValueAsString(Map.of("keys", List.copyOf(objectKeys)));
        repository.save(new MediaJob(MediaJobType.DELETE_OBJECTS, momentId, payload, clock.instant()));
    }
}
