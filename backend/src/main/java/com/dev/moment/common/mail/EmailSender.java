package com.dev.moment.common.mail;

/**
 * Gửi email văn bản thuần. Tách interface để test thay bằng bản ghi lại thư thay vì gửi SMTP thật.
 * Không gọi bên trong transaction database (CLAUDE.md, quy ước backend).
 */
public interface EmailSender {

    void send(String to, String subject, String body);
}
