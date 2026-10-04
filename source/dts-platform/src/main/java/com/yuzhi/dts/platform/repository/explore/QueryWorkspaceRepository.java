package com.yuzhi.dts.platform.repository.explore;

import com.yuzhi.dts.platform.domain.explore.QueryWorkspace;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QueryWorkspaceRepository extends JpaRepository<QueryWorkspace, UUID> {}

