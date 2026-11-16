package com.dev.moment.friend;

import com.dev.moment.common.security.SecureTokens;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mã mời và link mời (US-10, US-14, BR-04). */
@Service
public class InviteService {

    private static final int CODE_LENGTH = 8;
    private static final int MAX_GENERATION_ATTEMPTS = 5;
    // Mã do server sinh chỉ gồm chữ và số; chuỗi khác không cần tra database.
    private static final Pattern CODE_FORMAT = Pattern.compile("^[A-Za-z0-9]{1,16}$");

    private final InviteCodeRepository repository;
    private final Clock clock;
    private final String frontendBaseUrl;

    // Link mời dạng /invite?code=... để chạy được trên host tĩnh không có rewrite (D-032).
    InviteService(InviteCodeRepository repository, Clock clock,
            @Value("${moment.auth.frontend-base-url}") String frontendBaseUrl) {
        this.repository = repository;
        this.clock = clock;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    /** Mã đang hoạt động của tôi; tạo lần đầu khi chưa có. */
    @Transactional
    public Invite myInvite(UUID userId) {
        InviteCode invite = repository.findById(userId).orElseGet(() -> {
            repository.insertIfAbsent(userId, newUniqueCode(), clock.instant());
            return repository.findById(userId).orElseThrow();
        });
        return toInvite(invite);
    }

    /** US-14: tạo mã mới, mã cũ mất hiệu lực ngay. */
    @Transactional
    public Invite rotate(UUID userId) {
        String code = newUniqueCode();
        InviteCode invite = repository.findById(userId)
                .map(existing -> {
                    existing.replaceCode(code, clock.instant());
                    return existing;
                })
                .orElseGet(() -> repository.save(new InviteCode(userId, code, clock.instant())));
        return toInvite(invite);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findInviterId(String code) {
        if (code == null || !CODE_FORMAT.matcher(code).matches()) {
            return Optional.empty();
        }
        return repository.findByCode(code).map(InviteCode::getUserId);
    }

    // Không gian 57^8 nên trùng gần như không xảy ra; vẫn kiểm tra và có unique index làm chốt cuối.
    private String newUniqueCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String code = SecureTokens.newCode(CODE_LENGTH);
            if (!repository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Không sinh được mã mời không trùng");
    }

    private Invite toInvite(InviteCode invite) {
        return new Invite(invite.getCode(), frontendBaseUrl + "/invite?code=" + invite.getCode());
    }

    public record Invite(String code, String url) {
    }
}
