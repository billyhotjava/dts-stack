package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.OrganizationNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends JpaRepository<OrganizationNode, Long> {
    List<OrganizationNode> findByParentIsNullOrderByIdAsc();

    Optional<OrganizationNode> findByKeycloakGroupId(String keycloakGroupId);

    Optional<OrganizationNode> findFirstByNameAndParentIsNull(String name);

    Optional<OrganizationNode> findFirstByParentIdAndName(Long parentId, String name);

    List<OrganizationNode> findByRootTrue();

    Optional<OrganizationNode> findFirstByRootTrue();
}
