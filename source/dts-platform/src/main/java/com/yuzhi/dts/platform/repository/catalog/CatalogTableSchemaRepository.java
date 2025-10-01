package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogTableSchemaRepository extends JpaRepository<CatalogTableSchema, UUID> {
    List<CatalogTableSchema> findByDataset(CatalogDataset dataset);

    List<CatalogTableSchema> findByDatasetIn(Collection<CatalogDataset> datasets);
}
