package com.dev.moment.support;

import com.dev.moment.common.mail.EmailSender;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ghi lại email thay vì gửi SMTP, để test đọc link xác thực và đặt lại mật khẩu. */
public class RecordingEmailSender implements EmailSender {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_%-]+)");

    private final List<SentEmail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new SentEmail(to, subject, body));
    }

    public List<SentEmail> sentTo(String email) {
        return sent.stream().filter(mail -> mail.to().equalsIgnoreCase(email)).toList();
    }

    /** Token trong email mới nhất gửi tới địa chỉ này có đường dẫn chứa {@code path}. */
    public Optional<String> latestToken(String email, String path) {
        List<SentEmail> mails = sentTo(email).stream().filter(mail -> mail.body().contains(path)).toList();
        if (mails.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = TOKEN.matcher(mails.getLast().body());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    public record SentEmail(String to, String subject, String body) {
    }
}
