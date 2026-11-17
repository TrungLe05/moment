package com.dev.moment.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

    // Khóa dòng để một link chỉ dùng được đúng một lần khi bấm hai lần liền.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EmailToken t where t.tokenHash = :tokenHash")
    Optional<EmailToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    // Gửi link mới thì link cũ cùng mục đích hết hiệu lực.
    @Modifying
    @Query("""
            update EmailToken t set t.usedAt = :now
            where t.userId = :userId and t.purpose = :purpose and t.usedAt is null
            """)
    int invalidateUnused(@Param("userId") UUID userId, @Param("purpose") EmailToken.Purpose purpose,
            @Param("now") Instant now);

    // Job định kỳ (DB mục 9): token email hết hạn trước :expiredBefore.
    @Modifying
    @Query("delete from EmailToken t where t.expiresAt < :expiredBefore")
    int deleteExpiredBefore(@Param("expiredBefore") Instant expiredBefore);
}
