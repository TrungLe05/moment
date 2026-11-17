package com.dev.moment.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Job định kỳ dọn token (DB mục 9): refresh token và token email hết hạn quá 7 ngày. */
@Component
public class TokenHousekeeping {

    /** DB mục 9: giữ token đã hết hạn thêm 7 ngày rồi xóa. */
    static final Duration EXPIRED_RETENTION = Duration.ofDays(7);

    private static final Logger log = LoggerFactory.getLogger(TokenHousekeeping.class);

    private final RefreshTokenRepository refreshTokens;
    private final EmailTokenRepository emailTokens;
    private final Clock clock;

    TokenHousekeeping(RefreshTokenRepository refreshTokens, EmailTokenRepository emailTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.emailTokens = emailTokens;
        this.clock = clock;
    }

    /** Trả tổng số token đã xóa. */
    @Transactional
    public int deleteExpiredTokens() {
        Instant cutoff = clock.instant().minus(EXPIRED_RETENTION);
        int refresh = refreshTokens.deleteFamiliesExpiredBefore(cutoff);
        int email = emailTokens.deleteExpiredBefore(cutoff);
        if (refresh + email > 0) {
            log.info("Đã xóa {} refresh token và {} token email hết hạn", refresh, email);
        }
        return refresh + email;
    }
}
