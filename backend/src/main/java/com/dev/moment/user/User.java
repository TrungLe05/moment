package com.dev.moment.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Bảng users. Chỉ dùng trong gói user; ra ngoài thì chuyển thành {@link UserAccount}. */
@Entity
@Table(name = "users")
class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(length = 20)
    private String username;

    @Column(name = "display_name", length = 40)
    private String displayName;

    @Column(name = "avatar_key", length = 300)
    private String avatarKey;

    @Column(nullable = false, length = 5)
    private String locale;

    @Column(nullable = false, length = 16)
    private String plan = "FREE";

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    User(String email, String passwordHash, String locale, Instant now) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.locale = locale;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UserAccount toAccount() {
        return new UserAccount(id, email, emailVerifiedAt, passwordHash, username, displayName, avatarKey, locale, demo);
    }

    UUID getId() {
        return id;
    }

    String getUsername() {
        return username;
    }

    void markEmailVerified(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
            updatedAt = now;
        }
    }

    void changePasswordHash(String newHash, Instant now) {
        this.passwordHash = newHash;
        this.updatedAt = now;
    }

    void changeUsername(String newUsername, Instant now) {
        this.username = newUsername;
        this.updatedAt = now;
    }

    void changeDisplayName(String newDisplayName, Instant now) {
        this.displayName = newDisplayName;
        this.updatedAt = now;
    }

    void changeLocale(String newLocale, Instant now) {
        this.locale = newLocale;
        this.updatedAt = now;
    }
}
