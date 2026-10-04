package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.PortalMenuVisibility;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PortalMenuVisibilityRepository extends JpaRepository<PortalMenuVisibility, Long> {
    List<PortalMenuVisibility> findByMenuId(Long menuId);

    List<PortalMenuVisibility> findByRoleCode(String roleCode);

    @Modifying
    @Transactional
    void deleteByMenuId(Long menuId);

    @Modifying
    @Transactional
    void deleteByRoleCode(String roleCode);

    @Modifying
    @Transactional
    void deleteByRoleCodeAndMenuIdNotIn(String roleCode, Collection<Long> menuIds);
}
