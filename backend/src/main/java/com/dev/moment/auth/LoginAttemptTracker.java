package com.dev.moment.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * US-03: sai mật khẩu 5 lần trong 15 phút cho cùng email thì khóa đăng nhập 15 phút.
 * Đếm trong bộ nhớ vì MVP chạy một instance (danh-sach-api.md mục 6); khởi động lại thì đếm lại từ đầu.
 */
@Component
class LoginAttemptTracker {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();
    private final Clock clock;

    LoginAttemptTracker(Clock clock) {
        this.clock = clock;
    }

    /** Thời gian còn bị khóa, rỗng nếu được thử đăng nhập. */
    Optional<Duration> remainingLock(String email) {
        Attempts attempts = attemptsByEmail.get(key(email));
        if (attempts == null) {
            return Optional.empty();
        }
        synchronized (attempts) {
            Instant now = clock.instant();
            if (attempts.lockedUntil != null && attempts.lockedUntil.isAfter(now)) {
                return Optional.of(Duration.between(now, attempts.lockedUntil));
            }
            return Optional.empty();
        }
    }

    void recordFailure(String email) {
        Attempts attempts = attemptsByEmail.computeIfAbsent(key(email), ignored -> new Attempts());
        synchronized (attempts) {
            Instant now = clock.instant();
            attempts.failures.addLast(now);
            while (!attempts.failures.isEmpty() && !attempts.failures.peekFirst().isAfter(now.minus(WINDOW))) {
                attempts.failures.removeFirst();
            }
            if (attempts.failures.size() >= MAX_FAILURES) {
                attempts.lockedUntil = now.plus(LOCK_DURATION);
                attempts.failures.clear();
            }
        }
    }

    void recordSuccess(String email) {
        attemptsByEmail.remove(key(email));
    }

    private static String key(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static final class Attempts {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant lockedUntil;
    }
}
