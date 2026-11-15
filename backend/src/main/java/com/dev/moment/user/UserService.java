package com.dev.moment.user;

import com.dev.moment.common.error.ApiException;
import com.dev.moment.common.error.ErrorCode;
import com.dev.moment.common.error.FieldViolation;
import com.dev.moment.friend.FriendService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ tài khoản và hồ sơ. Gói khác gọi qua service này, không dùng thẳng UserRepository. */
@Service
public class UserService {

    /** BR-02: 3–20 ký tự gồm a-z, 0-9, dấu chấm, gạch dưới. */
    private static final Pattern USERNAME_FORMAT = Pattern.compile("^[a-z0-9._]{3,20}$");

    private final UserRepository userRepository;
    private final FriendService friendService;
    private final Clock clock;

    public UserService(UserRepository userRepository, FriendService friendService, Clock clock) {
        this.userRepository = userRepository;
        this.friendService = friendService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findById(UUID userId) {
        return userRepository.findById(userId).map(User::toAccount);
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim()).map(User::toAccount);
    }

    /** Tạo tài khoản email + mật khẩu, chưa xác thực email (BR-01). */
    @Transactional
    public UserAccount createEmailUser(String email, String passwordHash, String locale) {
        String normalizedEmail = email.trim();
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        return save(new User(normalizedEmail, passwordHash, locale, now()), ErrorCode.EMAIL_TAKEN).toAccount();
    }

    /** Tạo tài khoản từ Google; email đã được Google xác thực nên đánh dấu xác thực luôn. */
    @Transactional
    public UserAccount createGoogleUser(String email, String locale) {
        User user = new User(email.trim(), null, locale, now());
        user.markEmailVerified(now());
        return save(user, ErrorCode.EMAIL_TAKEN).toAccount();
    }

    @Transactional
    public void markEmailVerified(UUID userId) {
        requireUser(userId).markEmailVerified(now());
    }

    @Transactional
    public void changePasswordHash(UUID userId, String passwordHash) {
        requireUser(userId).changePasswordHash(passwordHash, now());
    }

    /**
     * Google chứng minh được chủ email cho một tài khoản email chưa xác thực (Q-022, đang chờ chốt):
     * đánh dấu đã xác thực và bỏ mật khẩu cũ, vì mật khẩu đó có thể do người khác đặt khi giả danh email này.
     */
    @Transactional
    public void claimUnverifiedAccountByGoogle(UUID userId) {
        User user = requireUser(userId);
        user.markEmailVerified(now());
        user.changePasswordHash(null, now());
    }

    @Transactional
    public UserAccount updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        Instant now = now();
        if (request.username() != null) {
            String username = normalizeUsername(request.username());
            if (!username.equals(user.getUsername())) {
                if (userRepository.existsByUsernameIgnoreCase(username)) {
                    throw new ApiException(ErrorCode.USERNAME_TAKEN);
                }
                user.changeUsername(username, now);
            }
        }
        if (request.displayName() != null) {
            String displayName = request.displayName().strip();
            user.changeDisplayName(displayName.isEmpty() ? null : displayName, now);
        }
        if (request.locale() != null) {
            user.changeLocale(request.locale(), now);
        }
        return save(user, ErrorCode.USERNAME_TAKEN).toAccount();
    }

    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String rawUsername) {
        return !userRepository.existsByUsernameIgnoreCase(normalizeUsername(rawUsername));
    }

    @Transactional(readOnly = true)
    public UserResponse toResponse(UserAccount account) {
        // Ảnh đại diện (presigned URL) làm cùng phần tải ảnh đại diện ở T-116.
        return new UserResponse(account.id(), account.email(), account.username(), account.displayName(), null,
                account.locale(), account.needsOnboarding(), friendService.countFriends(account.id()),
                FriendService.FRIEND_LIMIT, account.demo());
    }

    private static String normalizeUsername(String rawUsername) {
        String username = rawUsername.strip().toLowerCase(Locale.ROOT);
        if (!USERNAME_FORMAT.matcher(username).matches()) {
            throw ApiException.withViolations(ErrorCode.VALIDATION_FAILED,
                    List.of(FieldViolation.of("username", "INVALID_FORMAT")));
        }
        return username;
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    // Unique index là chốt chặn cuối khi hai request cùng lúc vượt qua bước kiểm tra trước.
    private User save(User user, ErrorCode conflictCode) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(conflictCode);
        }
    }

    private Instant now() {
        return clock.instant();
    }
}
