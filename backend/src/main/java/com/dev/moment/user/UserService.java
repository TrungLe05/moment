package com.dev.moment.user;

import com.dev.moment.common.error.ApiException;
import com.dev.moment.common.error.ErrorCode;
import com.dev.moment.common.error.FieldViolation;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ tài khoản và hồ sơ. Gói khác gọi qua service này, không dùng thẳng UserRepository. */
@Service
public class UserService {

    /** BR-02: 3–20 ký tự gồm a-z, 0-9, dấu chấm, gạch dưới. */
    private static final Pattern USERNAME_FORMAT = Pattern.compile("^[a-z0-9._]{3,20}$");

    private final UserRepository userRepository;
    private final Clock clock;

    public UserService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
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

    /**
     * US-09: xóa hẳn tài khoản; dữ liệu liên quan xóa theo khóa ngoại. Bắt buộc trong transaction của bên gọi để
     * việc xóa file tạo cùng lúc (D-046).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAccount(UUID userId) {
        userRepository.deleteUser(userId);
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

    /**
     * Khóa dòng users của từng người theo đúng thứ tự trong danh sách, trong transaction của bên gọi.
     * Bên gọi phải truyền thứ tự cố định để hai transaction ngược chiều không deadlock (BR-06).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockInOrder(List<UUID> userIds) {
        for (UUID userId : userIds) {
            userRepository.findByIdForUpdate(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        }
    }

    @Transactional(readOnly = true)
    public Optional<UserSummary> findSummary(UUID userId) {
        return userRepository.findById(userId).map(UserService::toSummary);
    }

    /** Lấy nhiều user tóm tắt trong một câu truy vấn để tránh N+1. */
    @Transactional(readOnly = true)
    public Map<UUID, UserSummary> findSummaries(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllByIdIn(userIds).stream()
                .map(UserService::toSummary)
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String rawUsername) {
        return !userRepository.existsByUsernameIgnoreCase(normalizeUsername(rawUsername));
    }

    // Ảnh đại diện (presigned URL) thêm cùng T-116.
    private static UserSummary toSummary(User user) {
        UserAccount account = user.toAccount();
        return new UserSummary(account.id(), account.username(), account.displayName(), null);
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
