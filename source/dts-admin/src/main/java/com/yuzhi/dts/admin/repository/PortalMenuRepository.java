package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.PortalMenu;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortalMenuRepository extends JpaRepository<PortalMenu, Long> {
    List<PortalMenu> findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc();

    List<PortalMenu> findByDeletedTrueOrderBySortOrderAscIdAsc();

    @EntityGraph(attributePaths = { "visibilities", "parent" })
    List<PortalMenu> findAllByOrderBySortOrderAscIdAsc();

    List<PortalMenu> findByParentIdOrderBySortOrderAscIdAsc(Long parentId);

    Optional<PortalMenu> findFirstByNameIgnoreCase(String name);

    Optional<PortalMenu> findFirstByPath(String path);
}
