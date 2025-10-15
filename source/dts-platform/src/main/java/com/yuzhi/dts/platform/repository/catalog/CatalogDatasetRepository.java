package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogDatasetRepository extends JpaRepository<CatalogDataset, UUID>, JpaSpecificationExecutor<CatalogDataset> {
    List<CatalogDataset> findByDomain(CatalogDomain domain);

    Optional<CatalogDataset> findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(String hiveDatabase, String hiveTable);

    List<CatalogDataset> findByHiveDatabaseIgnoreCaseAndTypeIgnoreCase(String hiveDatabase, String type);

    List<CatalogDataset> findByHiveDatabaseIgnoreCase(String hiveDatabase);
}
