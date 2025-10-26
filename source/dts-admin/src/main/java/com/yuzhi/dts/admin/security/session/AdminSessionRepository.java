package com.yuzhi.dts.admin.security.session;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AdminSessionRepository extends JpaRepository<AdminSessionEntity, UUID> {

    Optional<AdminSessionEntity> findByAccessTokenHash(String accessTokenHash);

    Optional<AdminSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AdminSessionEntity s where s.normalizedUsername = :username and s.revokedAt is null")
    Optional<AdminSessionEntity> findActiveForUpdate(@Param("username") String normalizedUsername);
}
