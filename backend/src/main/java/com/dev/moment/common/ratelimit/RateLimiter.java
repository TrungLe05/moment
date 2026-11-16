package com.dev.moment.common.ratelimit;

import com.dev.moment.common.error.ApiException;
import com.dev.moment.common.error.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Giới hạn tần suất trong bộ nhớ bằng Bucket4j (T-119). MVP chạy một instance nên đủ;
 * chạy nhiều instance thì chuyển sang Redis (ARCHITECTURE.md mục 8).
 */
@Component
public class RateLimiter {

    // Giới hạn số bucket giữ trong RAM; bucket ít dùng nhất bị bỏ trước (tức là được đếm lại từ đầu).
    private static final int MAX_BUCKETS = 50_000;

    private final Map<String, Bucket> buckets = Collections.synchronizedMap(
            new LinkedHashMap<>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                    return size() > MAX_BUCKETS;
                }
            });

    /** Trừ một lượt; hết lượt thì ném RATE_LIMITED kèm Retry-After (giây). */
    public void consume(RateLimit limit, String key) {
        Bucket bucket = buckets.computeIfAbsent(limit.name() + ':' + key, ignored -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
            throw ApiException.withHeaders(ErrorCode.RATE_LIMITED, Map.of("Retry-After", Long.toString(retryAfterSeconds)));
        }
    }

    private static Bucket newBucket(RateLimit limit) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.capacity())
                        .refillGreedy(limit.capacity(), limit.period())
                        .build())
                .build();
    }
}
