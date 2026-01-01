package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.ModelingPlan;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlanReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelingPlanReviewRepository extends JpaRepository<ModelingPlanReview, UUID> {
    List<ModelingPlanReview> findByPlanOrderByCreatedDateDesc(ModelingPlan plan);
}

