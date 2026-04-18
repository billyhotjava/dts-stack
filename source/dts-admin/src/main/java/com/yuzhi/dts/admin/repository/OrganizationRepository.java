package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.OrganizationNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends JpaRepository<OrganizationNode, Long> {
    List<OrganizationNode> findByParentIsNullOrderByIdAsc();

    boolean existsByParent_Id(Long parentId);

    Optional<OrganizationNode> findByKeycloakGroupId(String keycloakGroupId);

    Optional<OrganizationNode> findFirstByNameAndParentIsNull(String name);

    Optional<OrganizationNode> findFirstByDeptCodeIgnoreCase(String deptCode);

    Optional<OrganizationNode> findFirstByOrgCodeIgnoreCase(String orgCode);

    Optional<OrganizationNode> findFirstByParentIdAndName(Long parentId, String name);

    List<OrganizationNode> findByRootTrue();

    Optional<OrganizationNode> findFirstByRootTrue();

    /**
     * 一次性通过递归 CTE 把 {@code deptCode} 节点到根节点的整条祖先链取回，用于构造
     * Keycloak group path，避免按层级逐级查询造成的 N+1。
     *
     * <p>返回顺序：从根 → 目标节点（按 depth DESC），每行长度 4：
     * <ol>
     *   <li>{@code id} (Long)</li>
     *   <li>{@code dept_code} (String)</li>
     *   <li>{@code parent_id} (Long / null)</li>
     *   <li>{@code name} (String)</li>
     * </ol>
     *
     * <p>深度上限 20，与之前手写循环的 guard 一致。
     */
    @Query(
        value = """
            WITH RECURSIVE chain AS (
              SELECT id, dept_code, parent_id, name, 1 AS depth
              FROM organization_node
              WHERE LOWER(dept_code) = LOWER(:deptCode)
              UNION ALL
              SELECT o.id, o.dept_code, o.parent_id, o.name, c.depth + 1
              FROM organization_node o
              JOIN chain c ON c.parent_id = o.id
              WHERE c.depth < 20
            )
            SELECT id, dept_code, parent_id, name FROM chain ORDER BY depth DESC
            """,
        nativeQuery = true
    )
    List<Object[]> findAncestorChainByDeptCode(@Param("deptCode") String deptCode);
}
