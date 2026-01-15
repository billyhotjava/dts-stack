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

    Page<AdminKeycloakUser> findByMdmEnabled(int mdmEnabled, Pageable pageable);

    Page<AdminKeycloakUser> findByUsernameContainingIgnoreCaseAndMdmEnabled(String username, int mdmEnabled, Pageable pageable);

    @Query("select u from AdminKeycloakUser u where u.username is not null and lower(u.username) not in :excluded")
    Page<AdminKeycloakUser> findAllExcludingUsernames(@Param("excluded") Collection<String> excluded, Pageable pageable);

    @Query(
        "select u from AdminKeycloakUser u where u.username is not null and lower(u.username) not in :excluded and lower(u.username) like lower(concat('%', :username, '%'))"
    )
    Page<AdminKeycloakUser> findByUsernameContainingIgnoreCaseExcludingUsernames(
        @Param("username") String username,
        @Param("excluded") Collection<String> excluded,
        Pageable pageable
    );

    @Query("select u from AdminKeycloakUser u where u.username is not null and lower(u.username) not in :excluded and u.mdmEnabled = :mdmEnabled")
    Page<AdminKeycloakUser> findByMdmEnabledExcludingUsernames(
        @Param("mdmEnabled") int mdmEnabled,
        @Param("excluded") Collection<String> excluded,
        Pageable pageable
    );

    @Query(
        "select u from AdminKeycloakUser u where u.username is not null and lower(u.username) not in :excluded and lower(u.username) like lower(concat('%', :username, '%')) and u.mdmEnabled = :mdmEnabled"
    )
    Page<AdminKeycloakUser> findByUsernameContainingIgnoreCaseAndMdmEnabledExcludingUsernames(
        @Param("username") String username,
        @Param("mdmEnabled") int mdmEnabled,
        @Param("excluded") Collection<String> excluded,
        Pageable pageable
    );

    @Query("select u from AdminKeycloakUser u where lower(u.username) in :usernames")
    List<AdminKeycloakUser> findByUsernameInIgnoreCase(@Param("usernames") Collection<String> usernames);
}
