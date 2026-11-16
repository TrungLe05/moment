package com.dev.moment.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Đồng hồ cho test: đứng yên ở thời điểm thật lúc tạo hoặc reset, chỉ chạy khi test gọi advance. */
public class MutableClock extends Clock {

    // Cắt về micro giây như cột timestamptz của PostgreSQL, để giá trị đọc lại từ DB so sánh đúng với đồng hồ.
    private final AtomicReference<Instant> now = new AtomicReference<>(currentMicros());

    public void advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
    }

    public void reset() {
        now.set(currentMicros());
    }

    private static Instant currentMicros() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now.get();
    }
}
