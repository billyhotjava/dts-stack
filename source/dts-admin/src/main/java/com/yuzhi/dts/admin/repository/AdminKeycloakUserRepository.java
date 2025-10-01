package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminKeycloakUserRepository extends JpaRepository<AdminKeycloakUser, Long> {

    Optional<AdminKeycloakUser> findByKeycloakId(String keycloakId);

    Optional<AdminKeycloakUser> findByUsernameIgnoreCase(String username);

    Page<AdminKeycloakUser> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
}
