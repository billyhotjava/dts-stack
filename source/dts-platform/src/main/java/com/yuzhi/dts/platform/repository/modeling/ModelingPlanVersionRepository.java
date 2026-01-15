package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.ModelingPlan;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlanVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelingPlanVersionRepository extends JpaRepository<ModelingPlanVersion, UUID> {
    Optional<ModelingPlanVersion> findByPlanAndVersion(ModelingPlan plan, String version);

    List<ModelingPlanVersion> findByPlanOrderByCreatedDateDesc(ModelingPlan plan);

    long deleteByPlan(ModelingPlan plan);
}
