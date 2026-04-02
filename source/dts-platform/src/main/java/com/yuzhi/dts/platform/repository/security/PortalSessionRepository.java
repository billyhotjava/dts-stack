package com.yuzhi.dts.platform.repository.security;

import com.yuzhi.dts.platform.domain.security.PortalSessionEntity;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PortalSessionRepository extends JpaRepository<PortalSessionEntity, UUID> {
    Optional<PortalSessionEntity> findByAccessToken(String accessToken);

    Optional<PortalSessionEntity> findByRefreshToken(String refreshToken);

    Optional<PortalSessionEntity> findByNormalizedUsernameAndRevokedAtIsNull(String normalizedUsername);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ps from PortalSessionEntity ps where ps.normalizedUsername = :username and ps.revokedAt is null")
    Optional<PortalSessionEntity> findActiveForUpdate(@Param("username") String normalizedUsername);
}
