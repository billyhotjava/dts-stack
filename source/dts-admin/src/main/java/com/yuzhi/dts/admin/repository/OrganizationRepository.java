package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.OrganizationNode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends JpaRepository<OrganizationNode, Long> {
    List<OrganizationNode> findByParentIsNullOrderByIdAsc();
}

