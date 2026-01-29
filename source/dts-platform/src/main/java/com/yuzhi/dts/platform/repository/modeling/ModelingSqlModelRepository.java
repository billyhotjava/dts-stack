package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelingSqlModelRepository extends JpaRepository<ModelingSqlModel, UUID> {
    List<ModelingSqlModel> findByPlanId(UUID planId);
}
