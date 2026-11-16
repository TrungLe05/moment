package com.dev.moment.auth;

import com.dev.moment.common.mail.EmailSender;
import com.dev.moment.user.UserAccount;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Soạn và gửi email xác thực, đặt lại mật khẩu theo ngôn ngữ của người dùng.
 * Gửi lỗi thì chỉ ghi log: người dùng bấm "gửi lại" được, và phản hồi API không được lộ email có tồn tại hay không.
 */
@Component
class AuthMailer {

    private static final Logger log = LoggerFactory.getLogger(AuthMailer.class);

    private final EmailSender emailSender;
    private final MessageSource messageSource;
    private final AuthProperties properties;

    AuthMailer(EmailSender emailSender, MessageSource messageSource, AuthProperties properties) {
        this.emailSender = emailSender;
        this.messageSource = messageSource;
        this.properties = properties;
    }

    void sendVerification(UserAccount account, String rawToken) {
        send(account, "mail.verify", "/verify-email?token=" + encode(rawToken));
    }

    void sendPasswordReset(UserAccount account, String rawToken) {
        send(account, "mail.reset", "/reset-password?token=" + encode(rawToken));
    }

    private void send(UserAccount account, String keyPrefix, String pathAndQuery) {
        Locale locale = Locale.forLanguageTag(account.locale());
        String link = properties.frontendBaseUrl() + pathAndQuery;
        String subject = messageSource.getMessage(keyPrefix + ".subject", null, locale);
        String body = messageSource.getMessage(keyPrefix + ".body", new Object[] {link}, locale);
        try {
            emailSender.send(account.email(), subject, body);
        } catch (RuntimeException exception) {
            log.warn("Không gửi được email {} cho user {}", keyPrefix, account.id(), exception);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
