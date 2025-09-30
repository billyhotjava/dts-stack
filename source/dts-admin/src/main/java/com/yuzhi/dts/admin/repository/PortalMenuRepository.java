package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.PortalMenu;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortalMenuRepository extends JpaRepository<PortalMenu, Long> {
    List<PortalMenu> findByParentIsNullOrderBySortOrderAscIdAsc();
}

