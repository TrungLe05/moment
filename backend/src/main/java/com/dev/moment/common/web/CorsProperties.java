package com.dev.moment.common.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Danh sách origin được gọi API từ trình duyệt; đọc từ biến môi trường CORS_ALLOWED_ORIGINS. */
@ConfigurationProperties("moment.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
