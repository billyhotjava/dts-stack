package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

        DbtSourceService service = new DbtSourceService(properties, configService, mappingRepository);

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

        DbtSourceService service = new DbtSourceService(properties, configService, mappingRepository);

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

        DbtSourceService service = new DbtSourceService(properties, configService, mappingRepository);

        DbtSourceService.DbtSourceRefreshResult result = service.refreshOdsSources();

        String yaml = Files.readString(configuredProjectDir.resolve("models").resolve("ods_sources.yml"));
        assertThat(result.tables()).isEqualTo(1);
        assertThat(yaml).contains("name: \"ods_project_subject_domain\"");
        assertThat(yaml).containsOnlyOnce("name: \"ods_project_subject_domain\"");
    }
}
