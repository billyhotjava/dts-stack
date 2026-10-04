package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.InfraSecretService;
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
class DbtWorkspaceBootstrapTest {

    @Mock
    private InfraDataSourceRepository dataSourceRepository;

    @Mock
    private InfraSecretService secretService;

    @TempDir
    Path tempDir;

    @Test
    void loadConfig_shouldBootstrapMinimalDbtWorkspace() throws Exception {
        Path projectDir = tempDir.resolve("dbt");
        Path profilesDir = tempDir.resolve("profiles");

        DbtConfigService service = newService(projectDir, profilesDir);

        service.ensureConfigFile();
        DbtConfigService.DbtConfigView view = service.loadConfig();

        assertThat(view.workspaceStatus().ok()).isTrue();
        assertThat(projectDir.resolve("dbt_project.yml")).exists();
        assertThat(projectDir.resolve("README.md")).exists();
        assertThat(projectDir.resolve(".gitignore")).exists();
        assertThat(projectDir.resolve("models/ods")).isDirectory();
        assertThat(projectDir.resolve("models/dwd")).isDirectory();
        assertThat(projectDir.resolve("models/dws")).isDirectory();
        assertThat(projectDir.resolve("models/ads")).isDirectory();
        assertThat(projectDir.resolve("models/ods_sources.yml")).exists();
        assertThat(projectDir.resolve("macros/get_custom_schema.sql")).exists();
        assertThat(projectDir.resolve("macros/nullif_placeholder.sql")).exists();
        assertThat(projectDir.resolve("macros/parse_numeric_safe.sql")).exists();
        assertThat(projectDir.resolve("macros/parse_date_safe.sql")).exists();
        assertThat(Files.readString(projectDir.resolve("macros/parse_date_safe.sql"))).contains("\\d{1,2}").contains("make_date(");
        assertThat(projectDir.resolve("macros/truncate_relation.sql")).exists();
        assertThat(Files.readString(projectDir.resolve("macros/dts_unique_combination.sql")))
            .contains("{% test dts_unique_combination(model, combination_of_columns) %}")
            .contains("group by")
            .contains("having count(*) > 1")
            .doesNotContain("concat(", "||");
        assertThat(projectDir.resolve("seeds")).isDirectory();
        assertThat(projectDir.resolve("tests")).isDirectory();
        assertThat(projectDir.resolve("analyses")).isDirectory();
        assertThat(projectDir.resolve("snapshots")).isDirectory();
        assertThat(projectDir.resolve("target")).isDirectory();
        assertThat(projectDir.resolve("logs")).isDirectory();
    }

    @Test
    void loadConfig_shouldNotOverwriteExistingDbtProjectFile() throws Exception {
        Path projectDir = tempDir.resolve("dbt");
        Path profilesDir = tempDir.resolve("profiles");
        Files.createDirectories(projectDir);
        Path dbtProject = projectDir.resolve("dbt_project.yml");
        String original = """
            name: custom_workspace
            version: '9.9.9'
            profile: custom
            """;
        Files.writeString(dbtProject, original);

        DbtConfigService service = newService(projectDir, profilesDir);

        service.ensureConfigFile();
        service.loadConfig();

        assertThat(Files.readString(dbtProject)).isEqualTo(original);
    }

    private DbtConfigService newService(Path projectDir, Path profilesDir) {
        DbtProperties properties = new DbtProperties();
        properties.setEnabled(true);
        properties.setConfigPath(tempDir.resolve("upload").resolve("dbt-config.json").toString());
        properties.setProjectDir(projectDir.toString());
        properties.setProfilesDir(profilesDir.toString());

        InfraDataSource warehouse = new InfraDataSource();
        warehouse.setId(UUID.randomUUID());
        warehouse.setName("数仓 (biadmin)");
        warehouse.setType("postgres");
        warehouse.setJdbcUrl("jdbc:postgresql://localhost:5432/biadmin");
        warehouse.setUsername("biadmin");
        warehouse.setStatus("ACTIVE");

        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(warehouse));
        when(dataSourceRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(secretService.readSecrets(warehouse)).thenReturn(Map.of("password", ""));

        return new DbtConfigService(new ObjectMapper(), dataSourceRepository, secretService, properties);
    }
}
