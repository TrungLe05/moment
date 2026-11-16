package com.dev.moment.friend;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {

    Optional<InviteCode> findByCode(String code);

    boolean existsByCode(String code);

    // Hai request tạo mã lần đầu cùng lúc: request đến sau không làm gì thay vì lỗi khóa chính.
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO invite_codes (user_id, code, created_at) VALUES (:userId, :code, :now)
            ON CONFLICT (user_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("userId") UUID userId, @Param("code") String code, @Param("now") Instant now);
}
