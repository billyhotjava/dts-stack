package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRoleAssignmentRepository extends JpaRepository<AdminRoleAssignment, Long> {}

