package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelingSqlModelRepository extends JpaRepository<ModelingSqlModel, UUID> {
    List<ModelingSqlModel> findByPlanId(UUID planId);

    Optional<ModelingSqlModel> findFirstByPlanIdAndNameIgnoreCase(UUID planId, String name);

    List<ModelingSqlModel> findBySourceDataSourceId(UUID sourceDataSourceId);

    List<ModelingSqlModel> findByModelPathIn(List<String> modelPaths);

    @org.springframework.data.jpa.repository.Query("SELECT m FROM ModelingSqlModel m WHERE lower(m.tags) LIKE lower(concat('%', :tag, '%'))")
    List<ModelingSqlModel> findByTagsContainingIgnoreCase(@org.springframework.data.repository.query.Param("tag") String tag);

    @org.springframework.data.jpa.repository.Query("SELECT m FROM ModelingSqlModel m WHERE upper(m.layer) = upper(:layer)")
    List<ModelingSqlModel> findByLayerIgnoreCase(@org.springframework.data.repository.query.Param("layer") String layer);
}
