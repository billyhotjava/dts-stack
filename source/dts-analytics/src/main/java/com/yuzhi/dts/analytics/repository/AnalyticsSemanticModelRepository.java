package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsSemanticModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsSemanticModelRepository extends JpaRepository<AnalyticsSemanticModel, Long> {
    Optional<AnalyticsSemanticModel> findByModelNameIgnoreCase(String modelName);

    List<AnalyticsSemanticModel> findAllByExposedToModelerTrueOrderBySubjectAreaAscLabelAscModelNameAsc();

    List<AnalyticsSemanticModel> findAllByExposedToModelerOrderBySubjectAreaAscLabelAscModelNameAsc(boolean exposedToModeler);

    List<AnalyticsSemanticModel> findAllBySubjectAreaIgnoreCaseAndExposedToModelerOrderByLabelAscModelNameAsc(
        String subjectArea,
        boolean exposedToModeler
    );
}
