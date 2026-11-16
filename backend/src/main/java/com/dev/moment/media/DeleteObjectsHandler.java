package com.dev.moment.media;

import com.dev.moment.job.MediaJobHandler;
import com.dev.moment.job.MediaJobType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Việc DELETE_OBJECTS: xóa file sau khi dữ liệu trỏ tới chúng đã bị xóa (DB mục 2 và mục 9). */
@Component
class DeleteObjectsHandler implements MediaJobHandler {

    private final ObjectStorage storage;

    DeleteObjectsHandler(ObjectStorage storage) {
        this.storage = storage;
    }

    @Override
    public MediaJobType type() {
        return MediaJobType.DELETE_OBJECTS;
    }

    // Xóa khóa đã không còn thì storage coi là thành công, nên chạy lại bao nhiêu lần cũng được.
    @Override
    public void handle(UUID momentId, JsonNode payload) {
        List<String> keys = new ArrayList<>();
        payload.path("keys").forEach(key -> keys.add(key.asString()));
        storage.deleteAll(keys);
    }
}
