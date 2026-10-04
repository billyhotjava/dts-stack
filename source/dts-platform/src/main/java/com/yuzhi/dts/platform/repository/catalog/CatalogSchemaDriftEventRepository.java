package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogSchemaDriftEventRepository extends JpaRepository<CatalogSchemaDriftEvent, UUID> {
    List<CatalogSchemaDriftEvent> findTop200ByDatasetIdOrderByCreatedDateDesc(UUID datasetId);

    List<CatalogSchemaDriftEvent> findTop200ByRunIdOrderByCreatedDateDesc(UUID runId);

    List<CatalogSchemaDriftEvent> findTop500ByOrderByCreatedDateDesc();
}
