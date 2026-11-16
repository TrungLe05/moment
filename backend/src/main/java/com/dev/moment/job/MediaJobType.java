package com.dev.moment.job;

/** Loại việc nền, khớp ràng buộc CHECK của cột media_jobs.type. */
public enum MediaJobType {
    /** Tạo DISPLAY, VIDEO, THUMBNAIL từ file người dùng tải lên (DB mục 6). */
    PROCESS_MOMENT,
    /** Xóa danh sách khóa file trong object storage; payload {"keys": [...]}. */
    DELETE_OBJECTS
}
