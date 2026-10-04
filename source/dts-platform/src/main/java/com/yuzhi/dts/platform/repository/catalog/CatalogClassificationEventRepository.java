package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogClassificationEventRepository extends JpaRepository<CatalogClassificationEvent, UUID> {
    List<CatalogClassificationEvent> findBySubjectTypeAndSubjectKeyOrderByOccurredAtAsc(
        String subjectType,
        String subjectKey
    );
}
