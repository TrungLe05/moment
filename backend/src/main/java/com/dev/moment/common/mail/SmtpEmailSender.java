package com.dev.moment.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Gửi email qua SMTP (Mailpit khi dev, D-008). Timeout đặt trong application.yml.
 * Chỉ thử lại lỗi gửi tạm thời, tối đa 3 lần, chờ tăng dần; lỗi cấu hình hay địa chỉ sai thì không thử lại.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 200;

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, @Value("${moment.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        long backoff = INITIAL_BACKOFF_MS;
        for (int attempt = 1; ; attempt++) {
            try {
                mailSender.send(message);
                return;
            } catch (MailAuthenticationException | MailParseException | MailPreparationException exception) {
                throw exception;
            } catch (MailSendException exception) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw exception;
                }
                // Không ghi địa chỉ nhận vào log vì là dữ liệu cá nhân.
                log.warn("Gửi email lỗi tạm thời, thử lại lần {}", attempt + 1);
                sleep(backoff);
                backoff *= 2;
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new MailSendException("Bị ngắt khi chờ gửi lại email", interrupted);
        }
    }
}
