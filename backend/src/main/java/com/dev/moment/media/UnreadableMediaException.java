package com.dev.moment.media;

/** File không đọc được theo đúng định dạng đã khai báo, hoặc ffmpeg không chuyển được. Thử lại cũng vô ích. */
public class UnreadableMediaException extends RuntimeException {

    public UnreadableMediaException(String message) {
        super(message);
    }

    public UnreadableMediaException(String message, Throwable cause) {
        super(message, cause);
    }
}
