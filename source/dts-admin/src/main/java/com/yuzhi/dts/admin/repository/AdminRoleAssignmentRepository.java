package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRoleAssignmentRepository extends JpaRepository<AdminRoleAssignment, Long> {
    java.util.List<AdminRoleAssignment> findByUsernameIgnoreCase(String username);
    java.util.List<AdminRoleAssignment> findByRoleIgnoreCase(String role);
    java.util.List<AdminRoleAssignment> findByUsernameIgnoreCaseAndRoleIgnoreCase(String username, String role);
    long deleteByUsernameIgnoreCaseAndRoleIgnoreCase(String username, String role);

    /** F11-T02：稳定键优先读取；范围/动作随同一绑定返回，不得展平。 */
    java.util.List<AdminRoleAssignment> findByKeycloakId(String keycloakId);
}
