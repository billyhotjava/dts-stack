package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminRoleMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRoleMemberRepository extends JpaRepository<AdminRoleMember, Long> {
    List<AdminRoleMember> findByRoleIgnoreCase(String role);
    Optional<AdminRoleMember> findByRoleIgnoreCaseAndUsernameIgnoreCase(String role, String username);
    long deleteByRoleIgnoreCaseAndUsernameIgnoreCase(String role, String username);
    long countByRoleIgnoreCase(String role);
    long deleteByRoleIgnoreCase(String role);
}
