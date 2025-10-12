package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminKeycloakUserRepository extends JpaRepository<AdminKeycloakUser, Long> {

    Optional<AdminKeycloakUser> findByKeycloakId(String keycloakId);

    Optional<AdminKeycloakUser> findByUsernameIgnoreCase(String username);

    Page<AdminKeycloakUser> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    @Query("select u from AdminKeycloakUser u where lower(u.username) in :usernames")
    List<AdminKeycloakUser> findByUsernameInIgnoreCase(@Param("usernames") Collection<String> usernames);
}
