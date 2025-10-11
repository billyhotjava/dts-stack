package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRoleAssignmentRepository extends JpaRepository<AdminRoleAssignment, Long> {
    java.util.List<AdminRoleAssignment> findByUsernameIgnoreCase(String username);
    java.util.List<AdminRoleAssignment> findByUsernameIgnoreCaseAndRoleIgnoreCase(String username, String role);
    long deleteByUsernameIgnoreCaseAndRoleIgnoreCase(String username, String role);
}
