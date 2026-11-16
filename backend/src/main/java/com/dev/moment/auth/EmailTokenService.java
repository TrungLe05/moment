package com.dev.moment.auth;

import com.dev.moment.common.error.ApiException;
import com.dev.moment.common.error.ErrorCode;
import com.dev.moment.common.security.SecureTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Token dùng một lần gửi qua email (US-02, US-05). */
@Service
class EmailTokenService {

    private final EmailTokenRepository repository;
    private final Clock clock;

    EmailTokenService(EmailTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Tạo token mới và vô hiệu các token chưa dùng cùng mục đích. Trả token gốc để gắn vào link. */
    @Transactional(propagation = Propagation.MANDATORY)
    String issue(UUID userId, EmailToken.Purpose purpose, Duration ttl) {
        Instant now = clock.instant();
        repository.invalidateUnused(userId, purpose, now);
        String rawToken = SecureTokens.newToken();
        repository.save(new EmailToken(userId, purpose, SecureTokens.sha256Hex(rawToken), now.plus(ttl), now));
        return rawToken;
    }

    /** Dùng token: sai, đã dùng hoặc khác mục đích → TOKEN_INVALID; quá hạn → TOKEN_EXPIRED. */
    @Transactional(propagation = Propagation.MANDATORY)
    UUID consume(String rawToken, EmailToken.Purpose purpose) {
        Instant now = clock.instant();
        EmailToken token = repository.findByTokenHashForUpdate(SecureTokens.sha256Hex(rawToken))
                .filter(found -> found.getPurpose() == purpose && !found.isUsed())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        if (token.isExpired(now)) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }
        token.markUsed(now);
        return token.getUserId();
    }
}
