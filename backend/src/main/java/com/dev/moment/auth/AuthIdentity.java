package com.dev.moment.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Bảng auth_identities: liên kết tài khoản với danh tính Google (claim sub). */
@Entity
@Table(name = "auth_identities")
class AuthIdentity {

    static final String GOOGLE = "GOOGLE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 16)
    private String provider;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuthIdentity() {
    }

    AuthIdentity(UUID userId, String provider, String subject, String email, Instant now) {
        this.userId = userId;
        this.provider = provider;
        this.subject = subject;
        this.email = email;
        this.createdAt = now;
    }

    UUID getUserId() {
        return userId;
    }
}
