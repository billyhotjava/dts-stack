package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.AVAILABLE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.FORBIDDEN;
import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.PROVIDER_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
import com.yuzhi.dts.platform.service.etl.DbtManifestService;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolvedSource;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import com.yuzhi.dts.platform.service.security.AccessChecker;
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
        resolver = new SourceReferenceResolverAdapter(
            tableRepository,
            columnRepository,
            datasetRepository,
            fileRepository,
            dataSourceRepository,
            dbtManifestService,
            accessChecker
        );
    }

    @Test
    void resolvesCatalogTableFromAuthorizedSchemaFingerprint() {
        CatalogDataset dataset = dataset("orders");
        CatalogTableSchema table = table(dataset, TABLE_ID, "orders");
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));
        when(columnRepository.findByTable(table)).thenReturn(List.of(column(table, "order_id", "uuid", false)));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowed(dataset, "D01")).thenReturn(true);

        ResolvedSource result = resolver.resolve(
            SourceType.CATALOG_TABLE,
            new SourceLocator(TABLE_ID, null, null, null, null, null, null),
            ACCESS
        );

        assertThat(result.status()).isEqualTo(AVAILABLE);
        assertThat(result.displayName()).isEqualTo("orders");
        assertThat(result.resolvedVersion()).hasSize(64);
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
        CatalogDataset dataset = dataset("orders");
        dataset.setSourceId(CONNECTION_ID);
        dataset.setHiveDatabase("public");
        dataset.setHiveTable("orders");
        CatalogTableSchema table = table(dataset, TABLE_ID, "orders");
        when(dataSourceRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(connection));
        when(datasetRepository.findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(CONNECTION_ID, "public", "orders"))
            .thenReturn(Optional.of(dataset));
        when(tableRepository.findByDataset(dataset)).thenReturn(List.of(table));
        when(columnRepository.findByTable(table)).thenReturn(List.of(column(table, "order_id", "uuid", false)));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowed(dataset, "D01")).thenReturn(true);

        ResolvedSource result = resolver.resolve(
            SourceType.CONNECTION_TABLE,
            new SourceLocator(null, null, null, null, CONNECTION_ID, "public", "orders"),
            ACCESS
        );

        assertThat(result.status()).isEqualTo(AVAILABLE);
        assertThat(result.displayName()).isEqualTo("ERP / public.orders");
        assertThat(result.resolvedVersion()).hasSize(64);

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
}
