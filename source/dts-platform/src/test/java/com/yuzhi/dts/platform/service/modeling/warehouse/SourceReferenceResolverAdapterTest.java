package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.AVAILABLE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.FORBIDDEN;
import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.MISSING;
import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.PROVIDER_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraExternalExchangeFileRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetAvailabilityReadPort;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetAvailabilityReadPort.Availability;
import com.yuzhi.dts.platform.service.catalog.JpaCatalogSourceReferenceReadAdapter;
import com.yuzhi.dts.platform.service.etl.DbtManifestService;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolvedSource;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SourceReferenceResolverAdapterTest {

    private static final UUID TABLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID FILE_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID CONNECTION_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final AccessContext ACCESS = new AccessContext("tenant-a", "user-a", "D01");

    private CatalogTableSchemaRepository tableRepository;
    private CatalogColumnSchemaRepository columnRepository;
    private CatalogDatasetRepository datasetRepository;
    private InfraExternalExchangeFileRepository fileRepository;
    private InfraDataSourceRepository dataSourceRepository;
    private DbtManifestService dbtManifestService;
    private AccessChecker accessChecker;
    private CatalogAssetAvailabilityReadPort availability;
    private SourceReferenceResolverAdapter resolver;

    @BeforeEach
    void setUp() {
        tableRepository = mock(CatalogTableSchemaRepository.class);
        columnRepository = mock(CatalogColumnSchemaRepository.class);
        datasetRepository = mock(CatalogDatasetRepository.class);
        fileRepository = mock(InfraExternalExchangeFileRepository.class);
        dataSourceRepository = mock(InfraDataSourceRepository.class);
        dbtManifestService = mock(DbtManifestService.class);
        accessChecker = mock(AccessChecker.class);
        availability = mock(CatalogAssetAvailabilityReadPort.class);
        when(
            availability.read(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
            )
        ).thenReturn(Availability.legacyAvailable());
        resolver = new SourceReferenceResolverAdapter(
            new JpaCatalogSourceReferenceReadAdapter(tableRepository, columnRepository, datasetRepository, accessChecker),
            fileRepository,
            dataSourceRepository,
            dbtManifestService,
            availability
        );
    }

    @Test
    void resolvesCatalogTableFromAuthorizedSchemaFingerprint() {
        CatalogDataset dataset = dataset("orders");
        CatalogTableSchema table = table(dataset, TABLE_ID, "orders");
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));
        when(columnRepository.findByTable(table)).thenReturn(List.of(column(table, "order_id", "uuid", false)));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowedExact(dataset, "D01")).thenReturn(true);

        ResolvedSource result = resolver.resolve(
            SourceType.CATALOG_TABLE,
            new SourceLocator(TABLE_ID, null, null, null, null, null, null),
            ACCESS
        );

        assertThat(result.status()).isEqualTo(AVAILABLE);
        assertThat(result.displayName()).isEqualTo("orders");
        assertThat(result.resolvedVersion())
            .isEqualTo(sha256("orders\u0000order_id\u0000uuid\u0000false\u0000ACTIVE"));
    }

    @Test
    void resolvesConfirmedCatalogSourceForBackgroundExecutionWithoutARequestSecurityContext() {
        CatalogDataset dataset = dataset("orders");
        dataset.setClassification("CONFIDENTIAL");
        dataset.setLifecycleStatus("PENDING_GOVERNANCE");
        CatalogTableSchema table = table(dataset, TABLE_ID, "orders");
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));
        when(columnRepository.findByTable(table)).thenReturn(List.of(column(table, "order_id", "uuid", false)));

        ResolvedSource result = resolver.resolveForExecution(
            SourceType.CATALOG_TABLE,
            new SourceLocator(TABLE_ID, null, null, null, null, null, null),
            ACCESS
        );

        assertThat(result.status()).isEqualTo(AVAILABLE);
        assertThat(result.resolvedVersion())
            .isEqualTo(sha256("orders\u0000order_id\u0000uuid\u0000false\u0000ACTIVE"));
        verifyNoInteractions(accessChecker);
    }

    @Test
    void backgroundExecutionStillRejectsDisabledOrStaleCatalogSources() {
        CatalogDataset dataset = dataset("orders");
        CatalogTableSchema table = table(dataset, TABLE_ID, "orders");
        SourceLocator locator = new SourceLocator(TABLE_ID, null, null, null, null, null, null);
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));

        dataset.setEnabled(false);
        assertThat(resolver.resolveForExecution(SourceType.CATALOG_TABLE, locator, ACCESS).status())
            .isEqualTo(MISSING);

        dataset.setEnabled(true);
        dataset.setSourceId(CONNECTION_ID);
        dataset.setHarvestStatus("STALE");
        assertThat(resolver.resolveForExecution(SourceType.CATALOG_TABLE, locator, ACCESS).status())
            .isEqualTo(MISSING);

        dataset.setHarvestStatus(null);
        assertThat(resolver.resolveForExecution(SourceType.CATALOG_TABLE, locator, ACCESS).status())
            .isEqualTo(PROVIDER_ERROR);
        verifyNoInteractions(accessChecker);
    }

    @Test
    void mapsHarvestStatusAfterAuthorizationAndKeepsManualAssetsCompatible() {
        CatalogDataset dataset = dataset("orders");
        dataset.setSourceId(CONNECTION_ID);
        CatalogTableSchema table = table(dataset, TABLE_ID, "orders");
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));
        when(columnRepository.findByTable(table)).thenReturn(List.of(column(table, "order_id", "uuid", false)));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowedExact(dataset, "D01")).thenReturn(true);
        SourceLocator locator = new SourceLocator(TABLE_ID, null, null, null, null, null, null);

        dataset.setHarvestStatus("STALE");
        assertThat(resolver.resolve(SourceType.CATALOG_TABLE, locator, ACCESS).status()).isEqualTo(MISSING);

        dataset.setHarvestStatus(null);
        assertThat(resolver.resolve(SourceType.CATALOG_TABLE, locator, ACCESS).status()).isEqualTo(PROVIDER_ERROR);

        dataset.setHarvestStatus("SYNCED");
        assertThat(resolver.resolve(SourceType.CATALOG_TABLE, locator, ACCESS).status()).isEqualTo(AVAILABLE);

        dataset.setSourceId(null);
        dataset.setHarvestStatus(null);
        assertThat(resolver.resolve(SourceType.CATALOG_TABLE, locator, ACCESS).status()).isEqualTo(AVAILABLE);
    }

    @Test
    void resolvesCatalogTableWithANewFingerprintWhenApiLandingSnapshotOrConfigChanges() {
        CatalogDataset dataset = dataset("ods_api_crm_orders");
        CatalogTableSchema table = table(dataset, TABLE_ID, "ods_api_crm_orders");
        CatalogColumnSchema original = column(table, "__raw_record", "jsonb", false);
        table.setTags(apiEvidence("sha256:api-v1", "sha256:fields-v1"));
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowedExact(dataset, "D01")).thenReturn(true);
        when(columnRepository.findByTable(table)).thenReturn(List.of(original));

        String originalVersion = resolver.resolve(
            SourceType.CATALOG_TABLE,
            new SourceLocator(TABLE_ID, null, null, null, null, null, null),
            ACCESS
        ).resolvedVersion();

        table.setTags(apiEvidence("sha256:api-v2", "sha256:fields-v1"));
        String changedVersion = resolver.resolve(
            SourceType.CATALOG_TABLE,
            new SourceLocator(TABLE_ID, null, null, null, null, null, null),
            ACCESS
        ).resolvedVersion();

        table.setTags(apiEvidence("sha256:api-v2", "sha256:fields-v2"));
        String changedSnapshotVersion = resolver.resolve(
            SourceType.CATALOG_TABLE,
            new SourceLocator(TABLE_ID, null, null, null, null, null, null),
            ACCESS
        ).resolvedVersion();

        assertThat(changedVersion).isNotEqualTo(originalVersion);
        assertThat(changedSnapshotVersion).isNotEqualTo(changedVersion);
    }

    @Test
    void rejectsApiCatalogTableWithoutVerifiedExecutionAndBothChecksums() {
        CatalogDataset dataset = dataset("ods_api_crm_orders");
        CatalogTableSchema table = table(dataset, TABLE_ID, "ods_api_crm_orders");
        table.setTags("origin=API;taskId=10;resourceId=orders;executionStatus=success;configChecksum=sha256:api-v1");
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowedExact(dataset, "D01")).thenReturn(true);
        when(columnRepository.findByTable(table)).thenReturn(List.of(column(table, "__raw_record", "jsonb", false)));

        ResolvedSource result = resolver.resolve(
            SourceType.CATALOG_TABLE,
            new SourceLocator(TABLE_ID, null, null, null, null, null, null),
            ACCESS
        );

        assertThat(result.status()).isEqualTo(PROVIDER_ERROR);
    }

    @Test
    void resolvesEnabledExcelFromItsChecksumAndHidesForbiddenMetadata() {
        InfraExternalExchangeFile file = new InfraExternalExchangeFile();
        file.setId(FILE_ID);
        file.setFileName("budget.xlsx");
        file.setChecksum("sha256:abc");
        file.setEnabled(true);
        file.setOwnerDept("D01");
        when(fileRepository.findById(FILE_ID)).thenReturn(Optional.of(file));

        ResolvedSource available = resolver.resolve(
            SourceType.EXCEL_FILE,
            new SourceLocator(null, FILE_ID, null, null, null, null, null),
            ACCESS
        );
        ResolvedSource forbidden = resolver.resolve(
            SourceType.EXCEL_FILE,
            new SourceLocator(null, FILE_ID, null, null, null, null, null),
            new AccessContext("tenant-a", "user-b", "D02")
        );

        assertThat(available).isEqualTo(new ResolvedSource(AVAILABLE, "budget.xlsx", "sha256:abc"));
        assertThat(forbidden.status()).isEqualTo(FORBIDDEN);
        assertThat(forbidden.displayName()).isNull();
        assertThat(forbidden.resolvedVersion()).isNull();
    }

    @Test
    void resolvesConnectionTableOnlyThroughCatalogedSchema() {
        InfraDataSource connection = new InfraDataSource();
        connection.setId(CONNECTION_ID);
        connection.setName("ERP");
        connection.setOwnerDept("D01");
        connection.setStatus("ACTIVE");
        connection.setLastVerifiedAt(Instant.parse("2026-07-22T03:19:40Z"));
        CatalogDataset dataset = dataset("orders");
        dataset.setSourceId(CONNECTION_ID);
        dataset.setHarvestStatus("SYNCED");
        dataset.setHiveDatabase("public");
        dataset.setHiveTable("orders");
        CatalogTableSchema table = table(dataset, TABLE_ID, "orders");
        when(dataSourceRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(connection));
        when(datasetRepository.findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(CONNECTION_ID, "public", "orders"))
            .thenReturn(Optional.of(dataset));
        when(tableRepository.findByDataset(dataset)).thenReturn(List.of(table));
        when(columnRepository.findByTable(table)).thenReturn(List.of(column(table, "order_id", "uuid", false)));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowedExact(dataset, "D01")).thenReturn(true);

        ResolvedSource result = resolver.resolve(
            SourceType.CONNECTION_TABLE,
            new SourceLocator(null, null, null, null, CONNECTION_ID, "public", "orders"),
            ACCESS
        );

        assertThat(result.status()).isEqualTo(AVAILABLE);
        assertThat(result.displayName()).isEqualTo("ERP / public.orders");
        assertThat(result.resolvedVersion()).hasSize(64);

        connection.setLastVerifiedAt(null);
        assertThat(
            resolver.resolve(
                SourceType.CONNECTION_TABLE,
                new SourceLocator(null, null, null, null, CONNECTION_ID, "public", "orders"),
                ACCESS
            ).status()
        ).isEqualTo(PROVIDER_ERROR);
        connection.setLastVerifiedAt(Instant.parse("2026-07-22T03:19:40Z"));

        when(tableRepository.findByDataset(dataset)).thenReturn(List.of());
        assertThat(
            resolver.resolve(
                SourceType.CONNECTION_TABLE,
                new SourceLocator(null, null, null, null, CONNECTION_ID, "public", "orders"),
                ACCESS
            ).status()
        ).isEqualTo(PROVIDER_ERROR);
    }

    @Test
    void resolvesOnlyTheCurrentDbtProjectAndUsesAStableNodeArtifactHash() {
        DbtManifestService.DbtModelSummary model = new DbtManifestService.DbtModelSummary(
            "model.dts.orders",
            "orders",
            "orders",
            "warehouse",
            "dwd",
            "models/dwd/orders.sql"
        );
        when(dbtManifestService.listModels()).thenReturn(DbtManifestService.DbtModelResult.of(List.of(model)));

        ResolvedSource current = resolver.resolve(
            SourceType.DBT_NODE,
            new SourceLocator(null, null, "default", "model.dts.orders", null, null, null),
            ACCESS
        );
        ResolvedSource unsupported = resolver.resolve(
            SourceType.DBT_NODE,
            new SourceLocator(null, null, "another-project", "model.dts.orders", null, null, null),
            ACCESS
        );

        assertThat(current.status()).isEqualTo(AVAILABLE);
        assertThat(current.displayName()).isEqualTo("orders");
        assertThat(current.resolvedVersion()).hasSize(64);
        assertThat(unsupported.status()).isEqualTo(PROVIDER_ERROR);
    }

    @Test
    void failsClosedWhenCatalogAvailabilityIsUnavailableOrCannotBeRead() {
        CatalogDataset dataset = dataset("orders");
        CatalogTableSchema table = table(dataset, TABLE_ID, "orders");
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));
        when(columnRepository.findByTable(table)).thenReturn(List.of(column(table, "order_id", "uuid", false)));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowedExact(dataset, "D01")).thenReturn(true);
        when(availability.read(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(new Availability("UNAVAILABLE", 7L, 11L, "rollback-11"));

        ResolvedSource unavailable = resolver.resolve(
            SourceType.CATALOG_TABLE,
            new SourceLocator(TABLE_ID, null, null, null, null, null, null),
            ACCESS
        );
        assertThat(unavailable.status()).isEqualTo(SourceReferenceResolver.ResolutionStatus.MISSING);
        ResolvedSource executionUnavailable = resolver.resolveForExecution(
            SourceType.CATALOG_TABLE,
            new SourceLocator(TABLE_ID, null, null, null, null, null, null),
            ACCESS
        );
        assertThat(executionUnavailable.status()).isEqualTo(SourceReferenceResolver.ResolutionStatus.MISSING);

        when(availability.read(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new IllegalStateException("availability read failed"));
        ResolvedSource providerError = resolver.resolveForExecution(
            SourceType.CATALOG_TABLE,
            new SourceLocator(TABLE_ID, null, null, null, null, null, null),
            ACCESS
        );
        assertThat(providerError.status()).isEqualTo(PROVIDER_ERROR);
    }

    @Test
    void availabilityEpochParticipatesInResolvedVersion() {
        CatalogDataset dataset = dataset("orders");
        CatalogTableSchema table = table(dataset, TABLE_ID, "orders");
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));
        when(columnRepository.findByTable(table)).thenReturn(List.of(column(table, "order_id", "uuid", false)));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowedExact(dataset, "D01")).thenReturn(true);
        when(availability.read(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(new Availability("AVAILABLE", 1L, 1L, "available-1"))
            .thenReturn(new Availability("AVAILABLE", 2L, 2L, "available-2"));

        String first = resolver.resolve(
            SourceType.CATALOG_TABLE,
            new SourceLocator(TABLE_ID, null, null, null, null, null, null),
            ACCESS
        ).resolvedVersion();
        String second = resolver.resolve(
            SourceType.CATALOG_TABLE,
            new SourceLocator(TABLE_ID, null, null, null, null, null, null),
            ACCESS
        ).resolvedVersion();

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void rejectsDepartmentSuffixCollisionsAcrossCatalogFileAndConnectionSources() {
        AccessContext departmentA = new AccessContext("tenant-a", "user-a", "dept-a");
        CatalogDataset catalogDataset = dataset("orders");
        catalogDataset.setOwnerDept("dept-ba");
        CatalogTableSchema catalogTable = table(catalogDataset, TABLE_ID, "orders");
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(catalogTable));
        when(accessChecker.canRead(catalogDataset)).thenReturn(true);
        when(accessChecker.departmentAllowedExact(catalogDataset, "dept-a")).thenReturn(false);

        InfraExternalExchangeFile file = new InfraExternalExchangeFile();
        file.setId(FILE_ID);
        file.setFileName("budget.xlsx");
        file.setChecksum("sha256:abc");
        file.setEnabled(true);
        file.setOwnerDept("10010");
        when(fileRepository.findById(FILE_ID)).thenReturn(Optional.of(file));

        InfraDataSource connection = new InfraDataSource();
        connection.setId(CONNECTION_ID);
        connection.setName("ERP");
        connection.setOwnerDept("dept-ba");
        connection.setStatus("ACTIVE");
        connection.setLastVerifiedAt(Instant.parse("2026-07-22T03:19:40Z"));
        when(dataSourceRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(connection));

        assertThat(
            resolver.resolve(
                SourceType.CATALOG_TABLE,
                new SourceLocator(TABLE_ID, null, null, null, null, null, null),
                departmentA
            ).status()
        ).isEqualTo(FORBIDDEN);
        assertThat(
            resolver.resolve(
                SourceType.EXCEL_FILE,
                new SourceLocator(null, FILE_ID, null, null, null, null, null),
                new AccessContext("tenant-a", "user-a", "10")
            ).status()
        ).isEqualTo(FORBIDDEN);
        assertThat(
            resolver.resolve(
                SourceType.CONNECTION_TABLE,
                new SourceLocator(null, null, null, null, CONNECTION_ID, "public", "orders"),
                departmentA
            ).status()
        ).isEqualTo(FORBIDDEN);
    }

    private static CatalogDataset dataset(String name) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.randomUUID());
        dataset.setName(name);
        dataset.setEnabled(true);
        dataset.setClassification("DATA_INTERNAL");
        dataset.setOwnerDept("D01");
        return dataset;
    }

    private static CatalogTableSchema table(CatalogDataset dataset, UUID id, String name) {
        CatalogTableSchema table = new CatalogTableSchema();
        table.setId(id);
        table.setDataset(dataset);
        table.setName(name);
        return table;
    }

    private static CatalogColumnSchema column(CatalogTableSchema table, String name, String type, boolean nullable) {
        CatalogColumnSchema column = new CatalogColumnSchema();
        column.setId(UUID.randomUUID());
        column.setTable(table);
        column.setName(name);
        column.setDataType(type);
        column.setNullable(nullable);
        column.setStatus("ACTIVE");
        return column;
    }

    private static String apiEvidence(String configChecksum, String fieldSnapshotChecksum) {
        return String.join(
            ";",
            "origin=API",
            "connectionId=" + CONNECTION_ID,
            "taskId=10",
            "taskRevision=2026-07-24T07:00:00Z",
            "resourceId=orders",
            "executionSequence=100",
            "executionId=api-100",
            "executionStatus=success",
            "landingTruth=VERIFIED",
            "landingStatus=success",
            "configChecksum=" + configChecksum,
            "fieldSnapshotChecksum=" + fieldSnapshotChecksum
        );
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
