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

    Optional<AdminKeycloakUser> findByEmailIgnoreCase(String email);

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

    @Query(
        value = """
            select *
              from admin_keycloak_user u
             where u.username is not null
               and lower(u.username) not in (:excluded)
               and (cast(:username as text) is null or lower(u.username) like lower(concat('%', cast(:username as text), '%')))
               and (cast(:fullName as text) is null or lower(coalesce(u.full_name, '')) like lower(concat('%', cast(:fullName as text), '%')))
               and (
                   cast(:deptPath as text) is null
                   or exists (
                       select 1
                         from jsonb_array_elements_text(coalesce(u.group_paths, '[]'::jsonb)) as gp(path)
                        where lower(gp.path) = lower(cast(:deptPath as text))
                           or lower(gp.path) like lower(concat(cast(:deptPath as text), '/%'))
                   )
               )
               and (
                   cast(:inRole as boolean) is null
                   or exists (
                       select 1
                         from admin_role_member m
                        where lower(m.username) = lower(u.username)
                          and lower(m.role) in (:roleKeys)
                   ) = cast(:inRole as boolean)
               )
             order by lower(u.username)
            """,
        countQuery = """
            select count(*)
              from admin_keycloak_user u
             where u.username is not null
               and lower(u.username) not in (:excluded)
               and (cast(:username as text) is null or lower(u.username) like lower(concat('%', cast(:username as text), '%')))
               and (cast(:fullName as text) is null or lower(coalesce(u.full_name, '')) like lower(concat('%', cast(:fullName as text), '%')))
               and (
                   cast(:deptPath as text) is null
                   or exists (
                       select 1
                         from jsonb_array_elements_text(coalesce(u.group_paths, '[]'::jsonb)) as gp(path)
                        where lower(gp.path) = lower(cast(:deptPath as text))
                           or lower(gp.path) like lower(concat(cast(:deptPath as text), '/%'))
                   )
               )
               and (
                   cast(:inRole as boolean) is null
                   or exists (
                       select 1
                         from admin_role_member m
                        where lower(m.username) = lower(u.username)
                          and lower(m.role) in (:roleKeys)
                   ) = cast(:inRole as boolean)
               )
            """,
        nativeQuery = true
    )
    Page<AdminKeycloakUser> findRoleAssignmentCandidates(
        @Param("username") String username,
        @Param("fullName") String fullName,
        @Param("deptPath") String deptPath,
        @Param("inRole") Boolean inRole,
        @Param("roleKeys") Collection<String> roleKeys,
        @Param("excluded") Collection<String> excluded,
        Pageable pageable
    );
}
