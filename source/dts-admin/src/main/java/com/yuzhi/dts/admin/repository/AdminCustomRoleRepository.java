package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminCustomRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminCustomRoleRepository extends JpaRepository<AdminCustomRole, Long> {
    Optional<AdminCustomRole> findByName(String name);
}

