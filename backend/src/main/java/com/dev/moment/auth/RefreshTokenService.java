package com.dev.moment.auth;

import com.dev.moment.common.security.SecureTokens;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refresh token xoay vòng theo family (danh-sach-api.md mục 2): mỗi lần dùng đổi sang token mới;
 * token đã xoay mà bị dùng lại thì thu hồi cả family vì nhiều khả năng token đã lộ.
 */
@Service
class RefreshTokenService {

    private static final int USER_AGENT_MAX = 255;

    private final RefreshTokenRepository repository;
    private final AuthProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    RefreshTokenService(RefreshTokenRepository repository, AuthProperties properties,
            TransactionTemplate transactionTemplate, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /** Mở family mới khi đăng nhập; trả token gốc để gửi cho client, database chỉ giữ bản băm. */
    String startFamily(UUID userId, String userAgent) {
        return transactionTemplate.execute(status -> create(userId, UUID.randomUUID(), userAgent).rawToken());
    }

    /**
     * Xoay vòng. Kết quả REUSED vẫn phải commit việc thu hồi family, nên không ném exception trong transaction;
     * bên gọi tự chuyển kết quả thành lỗi.
     */
    Rotation rotate(String rawToken, String userAgent) {
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            Optional<RefreshToken> found = repository.findByTokenHashForUpdate(SecureTokens.sha256Hex(rawToken));
            if (found.isEmpty()) {
                return Rotation.invalid();
            }
            RefreshToken current = found.get();
            if (current.wasRotated()) {
                repository.revokeFamily(current.getFamilyId(), now);
                return Rotation.reused();
            }
            if (current.isRevoked() || current.isExpired(now)) {
                return Rotation.invalid();
            }
            Created next = create(current.getUserId(), current.getFamilyId(), userAgent);
            current.rotateTo(next.id(), now);
            Instant authTime = repository.findFamilyStart(current.getFamilyId());
            return Rotation.rotated(current.getUserId(), next.rawToken(), authTime);
        });
    }

    /** Đăng xuất: thu hồi token hiện tại; không tìm thấy thì bỏ qua. */
    void revoke(String rawToken) {
        transactionTemplate.executeWithoutResult(status -> repository.findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .ifPresent(token -> token.revoke(clock.instant())));
    }

    /** Thu hồi mọi phiên của người dùng, ví dụ sau khi đặt lại mật khẩu (US-05). */
    void revokeAll(UUID userId) {
        transactionTemplate.executeWithoutResult(status -> repository.revokeAllForUser(userId, clock.instant()));
    }

    private Created create(UUID userId, UUID familyId, String userAgent) {
        Instant now = clock.instant();
        String rawToken = SecureTokens.newToken();
        String trimmedAgent = userAgent == null ? null
                : userAgent.substring(0, Math.min(userAgent.length(), USER_AGENT_MAX));
        RefreshToken token = repository.save(new RefreshToken(userId, familyId, SecureTokens.sha256Hex(rawToken),
                now.plus(properties.refreshTokenTtl()), trimmedAgent, now));
        return new Created(token.getId(), rawToken);
    }

    private record Created(UUID id, String rawToken) {
    }

    enum RotationOutcome { ROTATED, INVALID, REUSED }

    record Rotation(RotationOutcome outcome, UUID userId, String rawToken, Instant authTime) {

        static Rotation rotated(UUID userId, String rawToken, Instant authTime) {
            return new Rotation(RotationOutcome.ROTATED, userId, rawToken, authTime);
        }

        static Rotation invalid() {
            return new Rotation(RotationOutcome.INVALID, null, null, null);
        }

        static Rotation reused() {
            return new Rotation(RotationOutcome.REUSED, null, null, null);
        }
    }
}
