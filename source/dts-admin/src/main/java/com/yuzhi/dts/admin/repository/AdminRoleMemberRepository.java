package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminRoleMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRoleMemberRepository extends JpaRepository<AdminRoleMember, Long> {
    List<AdminRoleMember> findByRoleIgnoreCase(String role);
    Optional<AdminRoleMember> findByRoleIgnoreCaseAndUsernameIgnoreCase(String role, String username);
    List<AdminRoleMember> findByUsernameIgnoreCase(String username);

    /** F11-T02：稳定键优先读取；回填完成前可能为空，调用方需保留 username 回退并标记 legacy。 */
    List<AdminRoleMember> findByKeycloakId(String keycloakId);

    @Query("select m from AdminRoleMember m where lower(m.username) in :usernames")
    List<AdminRoleMember> findByUsernameInIgnoreCase(@Param("usernames") Collection<String> usernames);

    long deleteByRoleIgnoreCaseAndUsernameIgnoreCase(String role, String username);
    long countByRoleIgnoreCase(String role);
    long deleteByRoleIgnoreCase(String role);
}
