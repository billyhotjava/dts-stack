package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.ModelingPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelingPlanRepository extends JpaRepository<ModelingPlan, UUID> {
    Optional<ModelingPlan> findFirstByNameIgnoreCase(String name);
}
