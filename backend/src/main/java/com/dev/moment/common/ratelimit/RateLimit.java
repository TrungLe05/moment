package com.dev.moment.common.ratelimit;

import java.time.Duration;

/** Các giới hạn tần suất của danh-sach-api.md mục 6. */
public enum RateLimit {
    LOGIN_PER_IP(30, Duration.ofMinutes(1)),
    REGISTER_PER_IP(5, Duration.ofHours(1)),
    EMAIL_REQUEST_PER_EMAIL(3, Duration.ofHours(1)),
    USERNAME_CHECK_PER_USER(30, Duration.ofMinutes(1)),
    INVITE_PER_IP(20, Duration.ofMinutes(1)),
    MOMENT_CREATE_PER_USER(60, Duration.ofHours(1)),
    DEFAULT_PER_USER(300, Duration.ofMinutes(1));

    private final long capacity;
    private final Duration period;

    RateLimit(long capacity, Duration period) {
        this.capacity = capacity;
        this.period = period;
    }

    public long capacity() {
        return capacity;
    }

    public Duration period() {
        return period;
    }
}
