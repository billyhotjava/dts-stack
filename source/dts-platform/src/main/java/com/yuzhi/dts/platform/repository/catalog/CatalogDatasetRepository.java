package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("SELECT d.hiveTable, d.id FROM CatalogDataset d WHERE d.hiveTable IS NOT NULL")
    List<Object[]> findHiveTableAndIdProjection();

    // ------------------------------------------------------------------ Sprint-15 F1/T03: Asset KPI counts

    /**
     * Counts enabled datasets filtered by optional dept / biz-domain /
     * time-window / classification set. Any {@code null} parameter
     * relaxes the corresponding filter. Callers must pass a non-empty
     * {@code classifications} list when the classification filter is
     * required (JPA {@code in} does not allow empty collections).
     */
    @Query(
        "select count(d) from CatalogDataset d left join d.domain dom " +
        "where d.enabled = true " +
        "  and (cast(:deptCode as text) is null or d.ownerDept = :deptCode) " +
        "  and (cast(:bizDomain as text) is null or dom.code = :bizDomain) " +
        "  and (cast(:createdFrom as java.time.Instant) is null or d.createdDate >= :createdFrom) " +
        "  and (cast(:createdTo as java.time.Instant) is null or d.createdDate < :createdTo)"
    )
    long countAssets(
        @Param("deptCode") String deptCode,
        @Param("bizDomain") String bizDomain,
        @Param("createdFrom") Instant createdFrom,
        @Param("createdTo") Instant createdTo
    );

    @Query(
        "select count(d) from CatalogDataset d left join d.domain dom " +
        "where d.enabled = true " +
        "  and (cast(:deptCode as text) is null or d.ownerDept = :deptCode) " +
        "  and (cast(:bizDomain as text) is null or dom.code = :bizDomain) " +
        "  and upper(d.classification) in :classifications"
    )
    long countAssetsByClassifications(
        @Param("deptCode") String deptCode,
        @Param("bizDomain") String bizDomain,
        @Param("classifications") List<String> classifications
    );

    // ------------------------------------------------------------------ Sprint-15 F1/T05: TOP assets

    /**
     * TOP-N assets ordered by caller-provided classification buckets
     * then by {@code lastModifiedDate} desc. Used
     * for both DEPT and ALL scopes — pass {@code deptCode = null} for
     * the institute-wide variant.
     *
     * <p>P0-11: drops the {@code fetch} keyword from the JPQL join so
     * Pageable can be applied at the SQL level (Hibernate refuses to push
     * limit/offset down when fetch joins are present, falling back to
     * in-memory pagination — HHH90003004). The {@code domain} association
     * is now eagerly loaded via {@link EntityGraph} which Hibernate
     * implements as a separate batch round-trip rather than a join, so
     * pagination keys stay in SQL.
     */
    @EntityGraph(attributePaths = {"domain"})
    @Query(
        "select d from CatalogDataset d left join d.domain dom " +
        "where d.enabled = true " +
        "  and (cast(:deptCode as text) is null or d.ownerDept = :deptCode) " +
        "  and (cast(:bizDomain as text) is null or dom.code = :bizDomain) " +
        "order by case " +
        "           when upper(d.classification) in :confidentialClassifications then 4 " +
        "           when upper(d.classification) in :secretClassifications then 3 " +
        "           when upper(d.classification) in :internalClassifications then 2 " +
        "           when upper(d.classification) in :publicClassifications then 1 " +
        "           else 0 " +
        "         end desc, " +
        "         d.lastModifiedDate desc, " +
        "         d.id asc"
    )
    List<CatalogDataset> findTopByClassification(
        @Param("deptCode") String deptCode,
        @Param("bizDomain") String bizDomain,
        @Param("confidentialClassifications") List<String> confidentialClassifications,
        @Param("secretClassifications") List<String> secretClassifications,
        @Param("internalClassifications") List<String> internalClassifications,
        @Param("publicClassifications") List<String> publicClassifications,
        Pageable pageable
    );

    /**
     * MINE fallback: when there is no catalog-access-log table, surface
     * the TOP-N assets that the user created or last modified.
     */
    @EntityGraph(attributePaths = {"domain"})
    @Query(
        "select d from CatalogDataset d " +
        "where d.enabled = true " +
        "  and (d.createdBy = :userLogin or d.lastModifiedBy = :userLogin) " +
        "order by d.lastModifiedDate desc, d.id asc"
    )
    List<CatalogDataset> findTopForUser(
        @Param("userLogin") String userLogin,
        Pageable pageable
    );
}
