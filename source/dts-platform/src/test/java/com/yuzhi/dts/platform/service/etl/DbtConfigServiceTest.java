package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.InfraSecretService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbtConfigServiceTest {

    @Mock
    private InfraDataSourceRepository dataSourceRepository;

    @Mock
    private InfraSecretService secretService;

    @TempDir
    Path tempDir;

    @Test
    void loadConfig_shouldResolveWarehouseTargetAndAllowMissingTargetDirectory() throws Exception {
        Path projectDir = tempDir.resolve("dbt");
        Path profilesDir = tempDir.resolve("profiles");
        Files.createDirectories(projectDir);

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
        DbtConfigService service = new DbtConfigService(new ObjectMapper(), dataSourceRepository, secretService, properties);
        service.ensureConfigFile();

        DbtConfigService.DbtConfigView view = service.loadConfig();

        assertThat(view.enabled()).isTrue();
        assertThat(view.workspaceStatus().ok()).isTrue();
        assertThat(view.config().targetDataSourceId()).isEqualTo(warehouse.getId());
        assertThat(view.profileStatus().generated()).isFalse();
        assertThat(view.profileStatus().message()).contains("tmpfs profile lease");
        assertThat(Files.exists(profilesDir.resolve("profiles.yml"))).isFalse();
        verifyNoInteractions(secretService);
    }

    @Test
    void loadConfig_shouldFallbackWhenConfiguredTargetDataSourceNoLongerExists() throws Exception {
        Path projectDir = tempDir.resolve("dbt");
        Path profilesDir = tempDir.resolve("profiles");
        Files.createDirectories(projectDir);

        Path configPath = tempDir.resolve("upload").resolve("dbt-config.json");
        UUID staleTargetId = UUID.randomUUID();

        DbtProperties properties = new DbtProperties();
        properties.setEnabled(true);
        properties.setConfigPath(configPath.toString());
        properties.setProjectDir(projectDir.toString());
        properties.setProfilesDir(profilesDir.toString());

        InfraDataSource warehouse = new InfraDataSource();
        warehouse.setId(UUID.randomUUID());
        warehouse.setName("数仓 (biadmin)");
        warehouse.setType("postgres");
        warehouse.setJdbcUrl("jdbc:postgresql://localhost:5432/biadmin");
        warehouse.setUsername("biadmin");
        warehouse.setStatus("ACTIVE");

        Files.createDirectories(configPath.getParent());
        Files.writeString(
            configPath,
            """
            {
              "enabled": true,
              "projectDir": "%s",
              "profilesDir": "%s",
              "profileName": "dts",
              "targetName": "dev",
              "targetDataSourceId": "%s",
              "database": "biadmin",
              "schema": "public"
            }
            """.formatted(projectDir, profilesDir, staleTargetId),
            StandardCharsets.UTF_8
        );

        when(dataSourceRepository.findById(staleTargetId)).thenReturn(Optional.empty());
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(warehouse));
        when(dataSourceRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        DbtConfigService service = new DbtConfigService(new ObjectMapper(), dataSourceRepository, secretService, properties);

        DbtConfigService.DbtConfigView view = service.loadConfig();

        assertThat(view.enabled()).isTrue();
        assertThat(view.target()).isNotNull();
        assertThat(view.target().id()).isEqualTo(warehouse.getId());
        assertThat(view.config().targetDataSourceId()).isEqualTo(warehouse.getId());
        assertThat(view.profileStatus().generated()).isFalse();
        assertThat(Files.exists(profilesDir.resolve("profiles.yml"))).isFalse();
        verifyNoInteractions(secretService);
    }
}
