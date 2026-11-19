package com.dev.moment.auth;

import com.dev.moment.common.error.ApiException;
import com.dev.moment.common.error.ErrorCode;
import com.dev.moment.job.MediaJobService;
import com.dev.moment.moment.MomentHousekeeping;
import com.dev.moment.user.UserAccount;
import com.dev.moment.user.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * US-09: xóa tài khoản và toàn bộ dữ liệu (API mục 3.2, D-026, D-046). Tài khoản có mật khẩu nhập lại mật khẩu; tài
 * khoản chỉ có Google phải đăng nhập lại trong 5 phút gần nhất (claim auth_time). Tài khoản demo không xóa được (D-027).
 */
@Service
class AccountDeletionService {

    /** D-026: "đăng nhập lại" nghĩa là xác thực trong 5 phút gần nhất. */
    static final Duration REAUTH_WINDOW = Duration.ofMinutes(5);

    private final UserService userService;
    private final MomentHousekeeping moments;
    private final MediaJobService mediaJobService;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    AccountDeletionService(UserService userService, MomentHousekeeping moments, MediaJobService mediaJobService,
            PasswordEncoder passwordEncoder, TransactionTemplate transactionTemplate, Clock clock) {
        this.userService = userService;
        this.moments = moments;
        this.mediaJobService = mediaJobService;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    void delete(UUID userId, String password, Instant authTime) {
        UserAccount account = userService.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (account.demo()) {
            throw new ApiException(ErrorCode.DEMO_ACCOUNT_READONLY);
        }
        if (account.hasPassword()) {
            // Băm so sánh ngoài transaction: BCrypt chậm, không giữ kết nối database trong lúc đó.
            if (password == null || !passwordEncoder.matches(password, account.passwordHash())) {
                throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
            }
        } else if (authTime == null || authTime.isBefore(clock.instant().minus(REAUTH_WINDOW))) {
            throw new ApiException(ErrorCode.REAUTH_REQUIRED);
        }
        transactionTemplate.executeWithoutResult(status -> {
            // Khóa dòng user trước khi đọc danh sách file, để không có khoảnh khắc nào xen vào giữa lúc đọc và lúc xóa.
            userService.lockInOrder(List.of(userId));
            List<String> keys = new ArrayList<>(moments.objectKeysOwnedBy(userId));
            if (account.avatarKey() != null) {
                keys.add(account.avatarKey());
            }
            userService.deleteAccount(userId);
            // File trên storage xóa sau khi transaction commit (CLAUDE.md, DB mục 2).
            mediaJobService.enqueueDeleteObjects(null, keys);
        });
    }
}
