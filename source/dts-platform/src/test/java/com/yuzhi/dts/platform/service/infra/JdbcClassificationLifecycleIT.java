package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogSchemaDriftEventRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraSchemaDiscoverCacheRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAutoLineageService;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationService;
import com.yuzhi.dts.platform.service.catalog.SchemaDriftDetector;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class JdbcClassificationLifecycleIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.4")
            .withDatabaseName("jdbc_classification_it")
            .withUsername("jdbc_classification_test")
            .withPassword("jdbc_classification_test");

    @Mock
    private InfraDataSourceRepository sourceRepository;

    @Mock
    private InfraSecretService secretService;

    @Mock
    private HiveConnectionService hiveConnectionService;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogTableSchemaRepository tableRepository;

    @Mock
    private CatalogColumnSchemaRepository columnRepository;

    @Mock
    private CatalogAutoLineageService autoLineageService;

    @Mock
    private CatalogSchemaDriftEventRepository driftEventRepository;

    @Mock
    private InfraSchemaDiscoverCacheRepository discoverCacheRepository;

    @Mock
    private CatalogClassificationService classificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String tableName = "s72_jdbc_classification";

    @BeforeEach
    void createIsolatedSourceTable() throws Exception {
        try (
            Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            );
            Statement statement = connection.createStatement()
        ) {
            statement.execute(
                "create table public." +
                tableName +
                " (id bigint primary key, identity_no varchar(32) not null)"
            );
            statement.execute(
                "insert into public." + tableName + " values (1, 'ID-SECRET-001')"
            );
        }
    }

    @AfterEach
    void dropIsolatedSourceTable() throws Exception {
        try (
            Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            );
            Statement statement = connection.createStatement()
        ) {
            statement.execute("drop table if exists public." + tableName);
        }
    }

    @Test
    void realJdbcMetadataImportSealsColumnsAndRaisesDatasetToHighestFieldLevel() {
        InfraDataSource source = source();
        when(secretService.readSecrets(source)).thenReturn(Map.of("password", POSTGRES.getPassword()));
        when(
            datasetRepository.findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(
                source.getId(),
                "public",
                tableName
            )
        ).thenReturn(Optional.empty());
        when(datasetRepository.save(any(CatalogDataset.class))).thenAnswer(invocation -> {
            CatalogDataset dataset = invocation.getArgument(0);
            if (dataset.getId() == null) {
                dataset.setId(UUID.randomUUID());
            }
            return dataset;
        });
        when(tableRepository.findFirstByDatasetAndNameIgnoreCase(any(CatalogDataset.class), eq(tableName)))
            .thenReturn(Optional.empty());
        when(tableRepository.save(any(CatalogTableSchema.class))).thenAnswer(invocation -> {
            CatalogTableSchema table = invocation.getArgument(0);
            if (table.getId() == null) {
                table.setId(UUID.randomUUID());
            }
            return table;
        });
        when(columnRepository.findByTable(any(CatalogTableSchema.class))).thenReturn(List.of());
        when(columnRepository.saveAll(any())).thenAnswer(invocation -> {
            List<CatalogColumnSchema> columns = new ArrayList<>(invocation.getArgument(0));
            columns.forEach(column -> column.setId(UUID.randomUUID()));
            return columns;
        });
        when(classificationService.sealOrRaise(any(CatalogClassificationService.SealCommand.class)))
            .thenAnswer(invocation -> snapshot(invocation.getArgument(0)));

        JdbcCatalogSyncService.JdbcSyncResult result = service().synchronize(source);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.tablesDiscovered()).isEqualTo(1);
        assertThat(result.columnsImported()).isEqualTo(2);

        ArgumentCaptor<CatalogClassificationService.SealCommand> commands =
            ArgumentCaptor.forClass(CatalogClassificationService.SealCommand.class);
        org.mockito.Mockito.verify(classificationService, org.mockito.Mockito.atLeast(3))
            .sealOrRaise(commands.capture());
        CatalogClassificationService.SealCommand identityColumn = commands
            .getAllValues()
            .stream()
            .filter(command -> "COLUMN".equals(command.subjectType()))
            .filter(command -> command.subjectKey().endsWith("/column:identity_no"))
            .findFirst()
            .orElseThrow();
        CatalogClassificationService.SealCommand dataset = commands
            .getAllValues()
            .stream()
            .filter(command -> "ASSET".equals(command.subjectType()))
            .findFirst()
            .orElseThrow();
        assertThat(identityColumn.declaredLevel()).isEqualTo("SECRET");
        assertThat(dataset.declaredLevel()).isEqualTo("INTERNAL");
        assertThat(dataset.upstreamLevels().stream().map(String::valueOf).toList()).contains("SECRET");

        ArgumentCaptor<CatalogDataset> savedDatasets = ArgumentCaptor.forClass(CatalogDataset.class);
        org.mockito.Mockito.verify(datasetRepository, org.mockito.Mockito.atLeast(2))
            .save(savedDatasets.capture());
        assertThat(savedDatasets.getAllValues().get(savedDatasets.getAllValues().size() - 1).getClassification())
            .isEqualTo("SECRET");
    }

    private JdbcCatalogSyncService service() {
        return new JdbcCatalogSyncService(
            sourceRepository,
            secretService,
            hiveConnectionService,
            objectMapper,
            datasetRepository,
            tableRepository,
            columnRepository,
            autoLineageService,
            driftEventRepository,
            new SchemaDriftDetector(objectMapper),
            discoverCacheRepository,
            classificationService
        );
    }

    private InfraDataSource source() {
        InfraDataSource source = new InfraDataSource();
        source.setId(UUID.randomUUID());
        source.setName("s72-postgresql");
        source.setType("POSTGRESQL");
        source.setJdbcUrl(POSTGRES.getJdbcUrl());
        source.setUsername(POSTGRES.getUsername());
        source.setStatus("ACTIVE");
        source.setProps(
            """
            {
              "schemas":["public"],
              "tablePattern":"%s",
              "catalogCleanupStale":false,
              "classification":"INTERNAL",
              "columnClassifications":{
                "public.%s.identity_no":"SECRET"
              }
            }
            """.formatted(tableName, tableName)
        );
        return source;
    }

    private CatalogClassificationSnapshot snapshot(
        CatalogClassificationService.SealCommand command
    ) {
        String effective = SecurityLevelCatalog.maxDataCode(
            command.declaredLevel(),
            command.detectedLevel(),
            command.manualFloor(),
            SecurityLevelCatalog.maxDataCode(command.upstreamLevels())
        );
        CatalogClassificationSnapshot snapshot = new CatalogClassificationSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setSubjectType(command.subjectType());
        snapshot.setSubjectKey(command.subjectKey());
        snapshot.setAssetType(command.assetType());
        snapshot.setDeclaredLevel(
            command.declaredLevel() == null
                ? null
                : SecurityLevelCatalog.requireDataLevel(command.declaredLevel()).code()
        );
        snapshot.setEffectiveLevel(effective);
        snapshot.setEvidenceChecksum(command.evidenceChecksum());
        snapshot.setRecordVersion(0L);
        snapshot.setSealedAt(java.time.Instant.parse("2026-07-26T00:00:00Z"));
        snapshot.setPropagationStatus("SEALED");
        return snapshot;
    }
}
