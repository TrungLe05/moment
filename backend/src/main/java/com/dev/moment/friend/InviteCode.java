package com.dev.moment.friend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Bảng invite_codes: mỗi người đúng một mã đang hoạt động (BR-04). */
@Entity
@Table(name = "invite_codes")
class InviteCode {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InviteCode() {
    }

    InviteCode(UUID userId, String code, Instant now) {
        this.userId = userId;
        this.code = code;
        this.createdAt = now;
    }

    UUID getUserId() {
        return userId;
    }

    String getCode() {
        return code;
    }

    // Đổi mã ngay trên dòng cũ nên mã cũ mất hiệu lực trong cùng câu lệnh (BR-04).
    void replaceCode(String newCode, Instant now) {
        this.code = newCode;
        this.createdAt = now;
    }
}
