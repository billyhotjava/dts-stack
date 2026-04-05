package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogDatasetRepository extends JpaRepository<CatalogDataset, UUID>, JpaSpecificationExecutor<CatalogDataset> {
    List<CatalogDataset> findByDomain(CatalogDomain domain);

    long countByDomain(CatalogDomain domain);

    Optional<CatalogDataset> findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(String hiveDatabase, String hiveTable);

    List<CatalogDataset> findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(String hiveDatabase, String hiveTable);

    Optional<CatalogDataset> findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(UUID sourceId, String hiveDatabase, String hiveTable);

    boolean existsBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCaseAndEnabledTrue(
        UUID sourceId,
        String hiveDatabase,
        String hiveTable
    );

    boolean existsBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCaseAndWarehouseLayerIgnoreCaseAndEnabledTrue(
        UUID sourceId,
        String hiveDatabase,
        String hiveTable,
        String warehouseLayer
    );

    boolean existsByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCaseAndEnabledTrue(String hiveDatabase, String hiveTable);

    boolean existsByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCaseAndWarehouseLayerIgnoreCaseAndEnabledTrue(
        String hiveDatabase,
        String hiveTable,
        String warehouseLayer
    );

    List<CatalogDataset> findByHiveDatabaseIgnoreCaseAndTypeIgnoreCase(String hiveDatabase, String type);

    List<CatalogDataset> findByHiveDatabaseIgnoreCase(String hiveDatabase);

    List<CatalogDataset> findBySourceIdAndHiveDatabaseIgnoreCase(UUID sourceId, String hiveDatabase);

    long countByCreatedBy(String createdBy);

    long countByCreatedDateAfter(Instant since);

    long countByEnabledTrue();

    long countByLifecycleStatusIgnoreCase(String lifecycleStatus);

    long countByEnabledTrueAndSnapshotTimeIsNull();

    long countByOwnerDeptIsNull();
}
