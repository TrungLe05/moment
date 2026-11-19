package com.dev.moment.user;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserRepository extends JpaRepository<User, UUID> {

    // So sánh không phân biệt hoa thường, khớp unique index users_email_uk trên lower(email).
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    // SELECT ... FOR UPDATE một dòng; bên gọi tự quyết thứ tự khóa (BR-06).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Query("select u from User u where u.id in :ids")
    List<User> findAllByIdIn(@Param("ids") Collection<UUID> ids);

    // US-09: khoảnh khắc, người nhận, bạn bè, reaction, token, mã mời xóa theo khóa ngoại ON DELETE CASCADE.
    @Modifying
    @Query("delete from User u where u.id = :id")
    int deleteUser(@Param("id") UUID id);
}
