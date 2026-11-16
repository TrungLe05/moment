package com.dev.moment.auth;

import com.dev.moment.common.error.ApiException;
import com.dev.moment.common.error.ErrorCode;
import com.dev.moment.user.UserAccount;
import com.dev.moment.user.UserService;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Nghiệp vụ xác thực E1 (US-01 → US-05, US-08). Phần ghi database chạy trong TransactionTemplate,
 * gửi email chạy sau khi commit vì không gọi dịch vụ ngoài bên trong transaction (CLAUDE.md).
 */
@Service
class AuthService {

    private final UserService userService;
    private final EmailTokenService emailTokenService;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenService accessTokenService;
    private final AuthIdentityRepository authIdentityRepository;
    private final LoginAttemptTracker loginAttemptTracker;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AuthMailer authMailer;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    // Băm sẵn để so khi email không tồn tại, giữ thời gian phản hồi như khi sai mật khẩu.
    private final String timingEqualizerHash;

    AuthService(UserService userService, EmailTokenService emailTokenService, RefreshTokenService refreshTokenService,
            AccessTokenService accessTokenService, AuthIdentityRepository authIdentityRepository,
            LoginAttemptTracker loginAttemptTracker, GoogleTokenVerifier googleTokenVerifier, AuthMailer authMailer,
            PasswordEncoder passwordEncoder, AuthProperties properties, TransactionTemplate transactionTemplate,
            Clock clock) {
        this.userService = userService;
        this.emailTokenService = emailTokenService;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenService = accessTokenService;
        this.authIdentityRepository = authIdentityRepository;
        this.loginAttemptTracker = loginAttemptTracker;
        this.googleTokenVerifier = googleTokenVerifier;
        this.authMailer = authMailer;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.timingEqualizerHash = passwordEncoder.encode("timing-equalizer-password");
    }

    /** US-01: tạo tài khoản chưa xác thực và gửi link xác thực 24 giờ. */
    void register(String email, String password, String locale) {
        PasswordPolicy.check("password", password);
        String passwordHash = passwordEncoder.encode(password);
        PendingEmail pending = transactionTemplate.execute(status -> {
            UserAccount account = userService.createEmailUser(email, passwordHash, locale);
            String token = emailTokenService.issue(account.id(), EmailToken.Purpose.VERIFY_EMAIL,
                    properties.verifyEmailTtl());
            return new PendingEmail(account, token);
        });
        authMailer.sendVerification(pending.account(), pending.token());
    }

    /** US-02: link dùng một lần. */
    void verifyEmail(String rawToken) {
        transactionTemplate.executeWithoutResult(status -> {
            UUID userId = emailTokenService.consume(rawToken, EmailToken.Purpose.VERIFY_EMAIL);
            userService.markEmailVerified(userId);
        });
    }

    /** Luôn thành công với bên gọi để không lộ email có tồn tại hay không. */
    void resendVerification(String email) {
        Optional<PendingEmail> pending = transactionTemplate.execute(status -> userService.findByEmail(email)
                .filter(account -> !account.emailVerified())
                .map(account -> new PendingEmail(account, emailTokenService.issue(account.id(),
                        EmailToken.Purpose.VERIFY_EMAIL, properties.verifyEmailTtl()))));
        pending.ifPresent(found -> authMailer.sendVerification(found.account(), found.token()));
    }

    /** US-03: thông báo chung khi sai; khóa 15 phút sau 5 lần sai. */
    AuthSession login(String email, String password, String userAgent) {
        Optional<Duration> lock = loginAttemptTracker.remainingLock(email);
        if (lock.isPresent()) {
            throw ApiException.withHeaders(ErrorCode.LOGIN_LOCKED,
                    Map.of("Retry-After", Long.toString(Math.max(1, lock.get().toSeconds()))));
        }
        Optional<UserAccount> found = userService.findByEmail(email);
        String storedHash = found.filter(UserAccount::hasPassword).map(UserAccount::passwordHash)
                .orElse(timingEqualizerHash);
        boolean passwordMatches = passwordEncoder.matches(password, storedHash);
        if (found.isEmpty() || !found.get().hasPassword() || !passwordMatches) {
            loginAttemptTracker.recordFailure(email);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        UserAccount account = found.get();
        if (!account.emailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        loginAttemptTracker.recordSuccess(email);
        return startSession(account, userAgent, null);
    }

    /** US-04, BR-03: tạo tài khoản mới hoặc liên kết với tài khoản email trùng. */
    AuthSession loginWithGoogle(String idToken, String locale, String userAgent) {
        GoogleTokenVerifier.GoogleIdentity identity = googleTokenVerifier.verify(idToken)
                .filter(GoogleTokenVerifier.GoogleIdentity::emailVerified)
                .orElseThrow(() -> new ApiException(ErrorCode.GOOGLE_TOKEN_INVALID));
        GoogleLogin result = transactionTemplate.execute(status -> {
            Optional<AuthIdentity> linked = authIdentityRepository.findByProviderAndSubject(
                    AuthIdentity.GOOGLE, identity.subject());
            if (linked.isPresent()) {
                return new GoogleLogin(linked.get().getUserId(), false);
            }
            Optional<UserAccount> sameEmail = userService.findByEmail(identity.email());
            UUID userId;
            boolean newUser;
            if (sameEmail.isPresent()) {
                userId = sameEmail.get().id();
                newUser = false;
                if (!sameEmail.get().emailVerified()) {
                    userService.claimUnverifiedAccountByGoogle(userId);
                }
            } else {
                userId = userService.createGoogleUser(identity.email(), locale).id();
                newUser = true;
            }
            authIdentityRepository.save(new AuthIdentity(userId, AuthIdentity.GOOGLE, identity.subject(),
                    identity.email(), clock.instant()));
            return new GoogleLogin(userId, newUser);
        });
        UserAccount account = userService.findById(result.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.GOOGLE_TOKEN_INVALID));
        return startSession(account, userAgent, result.newUser());
    }

    /** Xoay vòng refresh token; token đã xoay bị dùng lại → thu hồi cả family và TOKEN_REUSED. */
    RefreshedSession refresh(String rawRefreshToken, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken, userAgent);
        return switch (rotation.outcome()) {
            case INVALID -> throw new ApiException(ErrorCode.UNAUTHENTICATED);
            case REUSED -> throw new ApiException(ErrorCode.TOKEN_REUSED);
            case ROTATED -> new RefreshedSession(
                    accessTokenService.issue(rotation.userId(), rotation.authTime()), rotation.rawToken());
        };
    }

    /** US-08: chỉ thu hồi phiên của thiết bị hiện tại. */
    void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    /** US-05: luôn thành công với bên gọi; link đặt lại hết hạn sau 1 giờ. */
    void forgotPassword(String email) {
        Optional<PendingEmail> pending = transactionTemplate.execute(status -> userService.findByEmail(email)
                .map(account -> new PendingEmail(account, emailTokenService.issue(account.id(),
                        EmailToken.Purpose.RESET_PASSWORD, properties.resetPasswordTtl()))));
        pending.ifPresent(found -> authMailer.sendPasswordReset(found.account(), found.token()));
    }

    /** US-05: đặt mật khẩu mới và thu hồi mọi phiên. Tài khoản demo không được đổi mật khẩu (US-51). */
    void resetPassword(String rawToken, String newPassword) {
        PasswordPolicy.check("newPassword", newPassword);
        String passwordHash = passwordEncoder.encode(newPassword);
        transactionTemplate.executeWithoutResult(status -> {
            UUID userId = emailTokenService.consume(rawToken, EmailToken.Purpose.RESET_PASSWORD);
            UserAccount account = userService.findById(userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
            if (account.demo()) {
                throw new ApiException(ErrorCode.DEMO_ACCOUNT_READONLY);
            }
            userService.changePasswordHash(userId, passwordHash);
            refreshTokenService.revokeAll(userId);
        });
    }

    private AuthSession startSession(UserAccount account, String userAgent, Boolean newUser) {
        String refreshToken = refreshTokenService.startFamily(account.id(), userAgent);
        AccessTokenService.IssuedAccessToken accessToken = accessTokenService.issue(account.id(), clock.instant());
        return new AuthSession(account, accessToken, refreshToken, newUser);
    }

    private record PendingEmail(UserAccount account, String token) {
    }

    private record GoogleLogin(UUID userId, boolean newUser) {
    }

    record AuthSession(UserAccount account, AccessTokenService.IssuedAccessToken accessToken, String refreshToken,
            Boolean newUser) {
    }

    record RefreshedSession(AccessTokenService.IssuedAccessToken accessToken, String refreshToken) {
    }
}
