package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbtSourceServiceTest {

    @Mock
    private DbtConfigService configService;

    @Mock
    private InfraOdsTableMappingRepository mappingRepository;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogTableSchemaRepository tableRepository;

    @Mock
    private CatalogColumnSchemaRepository columnRepository;

    @TempDir
    Path tempDir;

    @Test
    void refreshOdsSourcesShouldUseConfiguredWorkspaceInsteadOfStaticDefault() throws Exception {
        Path configuredProjectDir = tempDir.resolve("configured-dbt");
        Files.createDirectories(configuredProjectDir.resolve("models"));

        DbtProperties properties = new DbtProperties();
        properties.setEnabled(true);
        properties.setProjectDir(tempDir.resolve("default-dbt").toString());

        DbtConfigService.DbtWorkspaceConfig config = new DbtConfigService.DbtWorkspaceConfig(
            true,
            configuredProjectDir.toString(),
            tempDir.resolve("profiles").toString(),
            "dts",
            "dev",
            null,
            null,
            null,
            Map.of()
        );
        DbtConfigService.DbtConfigView view = new DbtConfigService.DbtConfigView(
            true,
            config,
            DbtConfigService.DbtProfileStatus.skipped("test"),
            null,
            new DbtConfigService.DbtWorkspaceStatus(true, "ok", Map.of())
        );
        when(configService.loadConfig()).thenReturn(view);

        InfraOdsTableMapping mapping = new InfraOdsTableMapping();
        mapping.setOdsSchema("ods");
        mapping.setOdsTable("project_node");
        mapping.setDescription("项目节点 ODS");
        mapping.setEnabled(true);
        when(mappingRepository.findByEnabledTrueOrderByOdsSchemaAscOdsTableAsc()).thenReturn(List.of(mapping));

        DbtSourceService service = service(properties);

        DbtSourceService.DbtSourceRefreshResult result = service.refreshOdsSources();

        assertThat(result.enabled()).isTrue();
        assertThat(result.tables()).isEqualTo(1);
        assertThat(result.path()).startsWith(configuredProjectDir.toString());
        assertThat(Files.exists(configuredProjectDir.resolve("models").resolve("ods_sources.yml"))).isTrue();
    }

    @Test
    void refreshOdsSourcesShouldWriteEmptySourcesListWhenNoMappingsExist() throws Exception {
        Path configuredProjectDir = tempDir.resolve("configured-dbt-empty");
        Files.createDirectories(configuredProjectDir.resolve("models"));

        DbtProperties properties = new DbtProperties();
        properties.setEnabled(true);

        DbtConfigService.DbtWorkspaceConfig config = new DbtConfigService.DbtWorkspaceConfig(
            true,
            configuredProjectDir.toString(),
            tempDir.resolve("profiles").toString(),
            "dts",
            "dev",
            null,
            null,
            null,
            Map.of()
        );
        DbtConfigService.DbtConfigView view = new DbtConfigService.DbtConfigView(
            true,
            config,
            DbtConfigService.DbtProfileStatus.skipped("test"),
            null,
            new DbtConfigService.DbtWorkspaceStatus(true, "ok", Map.of())
        );
        when(configService.loadConfig()).thenReturn(view);
        when(mappingRepository.findByEnabledTrueOrderByOdsSchemaAscOdsTableAsc()).thenReturn(List.of());

        DbtSourceService service = service(properties);

        DbtSourceService.DbtSourceRefreshResult result = service.refreshOdsSources();

        assertThat(result.enabled()).isTrue();
        assertThat(result.tables()).isEqualTo(0);
        assertThat(Files.readString(configuredProjectDir.resolve("models").resolve("ods_sources.yml"))).isEqualTo("version: 2\nsources: []\n");
    }

    @Test
    void refreshOdsSourcesShouldDeduplicateSameSchemaAndTable() throws Exception {
        Path configuredProjectDir = tempDir.resolve("configured-dbt-dup");
        Files.createDirectories(configuredProjectDir.resolve("models"));

        DbtProperties properties = new DbtProperties();
        properties.setEnabled(true);

        DbtConfigService.DbtWorkspaceConfig config = new DbtConfigService.DbtWorkspaceConfig(
            true,
            configuredProjectDir.toString(),
            tempDir.resolve("profiles").toString(),
            "dts",
            "dev",
            null,
            null,
            null,
            Map.of()
        );
        DbtConfigService.DbtConfigView view = new DbtConfigService.DbtConfigView(
            true,
            config,
            DbtConfigService.DbtProfileStatus.skipped("test"),
            null,
            new DbtConfigService.DbtWorkspaceStatus(true, "ok", Map.of())
        );
        when(configService.loadConfig()).thenReturn(view);

        InfraOdsTableMapping first = new InfraOdsTableMapping();
        first.setOdsSchema("public");
        first.setOdsTable("ods_project_subject_domain");
        first.setDescription("first");
        first.setEnabled(true);

        InfraOdsTableMapping second = new InfraOdsTableMapping();
        second.setOdsSchema("public");
        second.setOdsTable("ods_project_subject_domain");
        second.setDescription("second");
        second.setEnabled(true);

        when(mappingRepository.findByEnabledTrueOrderByOdsSchemaAscOdsTableAsc()).thenReturn(List.of(first, second));

        DbtSourceService service = service(properties);

        DbtSourceService.DbtSourceRefreshResult result = service.refreshOdsSources();

        String yaml = Files.readString(configuredProjectDir.resolve("models").resolve("ods_sources.yml"));
        assertThat(result.tables()).isEqualTo(1);
        assertThat(yaml).contains("name: \"ods_project_subject_domain\"");
        assertThat(yaml).containsOnlyOnce("name: \"ods_project_subject_domain\"");
    }

    @Test
    void refreshOdsSourcesShouldWriteColumnMetadataAndTechnicalFlags() throws Exception {
        Path configuredProjectDir = tempDir.resolve("configured-dbt-columns");
        Files.createDirectories(configuredProjectDir.resolve("models"));

        DbtProperties properties = new DbtProperties();
        properties.setEnabled(true);

        DbtConfigService.DbtWorkspaceConfig config = new DbtConfigService.DbtWorkspaceConfig(
            true,
            configuredProjectDir.toString(),
            tempDir.resolve("profiles").toString(),
            "dts",
            "dev",
            null,
            null,
            null,
            Map.of()
        );
        DbtConfigService.DbtConfigView view = new DbtConfigService.DbtConfigView(
            true,
            config,
            DbtConfigService.DbtProfileStatus.skipped("test"),
            null,
            new DbtConfigService.DbtWorkspaceStatus(true, "ok", Map.of())
        );
        when(configService.loadConfig()).thenReturn(view);

        UUID connectionId = UUID.randomUUID();
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.randomUUID());
        dataset.setHiveDatabase("ods");
        dataset.setHiveTable("ods_project_plan");
        CatalogTableSchema catalogTable = new CatalogTableSchema();
        catalogTable.setDataset(dataset);
        catalogTable.setName("ods_project_plan");

        CatalogColumnSchema projectCode = column("project_code", "varchar", "项目编码", false);
        CatalogColumnSchema batchId = column("_dts_batch_id", "varchar", "DTS批次ID", true);
        projectCode.setTable(catalogTable);
        batchId.setTable(catalogTable);

        InfraOdsTableMapping mapping = new InfraOdsTableMapping();
        mapping.setConnectionId(connectionId);
        mapping.setOdsSchema("ods");
        mapping.setOdsTable("ods_project_plan");
        mapping.setStreamName("project_plan");
        mapping.setStreamNamespace("excel");
        mapping.setEnabled(true);
        when(mappingRepository.findByEnabledTrueOrderByOdsSchemaAscOdsTableAsc()).thenReturn(List.of(mapping));
        when(datasetRepository.findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(connectionId, "ods", "ods_project_plan"))
            .thenReturn(Optional.of(dataset));
        when(tableRepository.findFirstByDatasetAndNameIgnoreCase(dataset, "ods_project_plan")).thenReturn(Optional.of(catalogTable));
        when(columnRepository.findByTable(catalogTable)).thenReturn(List.of(projectCode, batchId));

        DbtSourceService.DbtSourceRefreshResult result = service(properties).refreshOdsSources();

        String yaml = Files.readString(configuredProjectDir.resolve("models").resolve("ods_sources.yml"));
        assertThat(result.tables()).isEqualTo(1);
        assertThat(yaml).contains("columns:");
        assertThat(yaml).contains("name: \"project_code\"");
        assertThat(yaml).contains("data_type: \"varchar\"");
        assertThat(yaml).contains("description: \"项目编码\"");
        assertThat(yaml).contains("name: \"_dts_batch_id\"");
        assertThat(yaml).contains("dts_technical: true");
    }

    private DbtSourceService service(DbtProperties properties) {
        return new DbtSourceService(properties, configService, mappingRepository, datasetRepository, tableRepository, columnRepository);
    }

    private CatalogColumnSchema column(String name, String dataType, String comment, boolean nullable) {
        CatalogColumnSchema column = new CatalogColumnSchema();
        column.setName(name);
        column.setDataType(dataType);
        column.setComment(comment);
        column.setNullable(nullable);
        column.setStatus("ACTIVE");
        return column;
    }
}
