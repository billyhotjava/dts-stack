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

    @Test
    void reconcile_pointsAMissingTargetAtTheManagedDefaultLakeMirror() throws Exception {
        UUID staleTargetId = UUID.randomUUID();
        Path configPath = writeConfigWithTarget(staleTargetId);
        InfraDataSource mirror = source("jdbc:postgresql://dts-pg:5432/biadmin", MANAGED_MIRROR_PROPS);
        InfraDataSource sameDatabaseElsewhere = source("jdbc:postgresql://other-pg:5432/biadmin", "{}");
        when(dataSourceRepository.findById(staleTargetId)).thenReturn(Optional.empty());
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(sameDatabaseElsewhere, mirror));

        serviceFor(configPath).reconcileTargetWithManagedDefaultLake();

        assertThat(Files.readString(configPath)).contains(mirror.getId().toString()).doesNotContain(staleTargetId.toString());
    }

    @Test
    void reconcile_neverFallsBackToADatabaseNameMatch() throws Exception {
        UUID staleTargetId = UUID.randomUUID();
        Path configPath = writeConfigWithTarget(staleTargetId);
        when(dataSourceRepository.findById(staleTargetId)).thenReturn(Optional.empty());
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(
            List.of(
                source("jdbc:postgresql://dts-pg:5432/biadmin", "{\"source\":\"admin-data-lake\",\"system\":true}"),
                source("jdbc:postgresql://other-pg:5432/biadmin", "{}")
            )
        );

        serviceFor(configPath).reconcileTargetWithManagedDefaultLake();

        assertThat(Files.readString(configPath)).contains(staleTargetId.toString());
    }

    @Test
    void reconcile_leavesTheFileWhenManagedMirrorsAreAmbiguous() throws Exception {
        UUID staleTargetId = UUID.randomUUID();
        Path configPath = writeConfigWithTarget(staleTargetId);
        when(dataSourceRepository.findById(staleTargetId)).thenReturn(Optional.empty());
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(
            List.of(
                source("jdbc:postgresql://pg-a:5432/biadmin", MANAGED_MIRROR_PROPS),
                source("jdbc:postgresql://pg-b:5432/lake", MANAGED_MIRROR_PROPS)
            )
        );

        serviceFor(configPath).reconcileTargetWithManagedDefaultLake();

        assertThat(Files.readString(configPath)).contains(staleTargetId.toString());
    }

    @Test
    void reconcile_neverSwitchesAValidTargetEvenWhenTheDefaultLakeDiffers() throws Exception {
        InfraDataSource configured = source("jdbc:postgresql://old-pg:5432/biadmin", "{}");
        Path configPath = writeConfigWithTarget(configured.getId());
        when(dataSourceRepository.findById(configured.getId())).thenReturn(Optional.of(configured));
        String before = Files.readString(configPath);

        serviceFor(configPath).reconcileTargetWithManagedDefaultLake();

        assertThat(Files.readString(configPath)).isEqualTo(before);
    }

    @Test
    void reconcile_runsAfterTheManagedDefaultLakeStartupSynchronization() throws Exception {
        int sync = com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService.class
            .getMethod("synchronizeManagedDefaultLakeOnStartup")
            .getAnnotation(org.springframework.core.annotation.Order.class)
            .value();
        int reconcile = DbtConfigService.class
            .getMethod("reconcileTargetWithManagedDefaultLake")
            .getAnnotation(org.springframework.core.annotation.Order.class)
            .value();

        assertThat(reconcile).isGreaterThan(sync);
    }

    private Path writeConfigWithTarget(UUID targetId) throws Exception {
        Path projectDir = tempDir.resolve("dbt");
        Files.createDirectories(projectDir);
        Path configPath = tempDir.resolve("upload").resolve("dbt-config.json");
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
            """.formatted(projectDir, tempDir.resolve("profiles"), targetId),
            StandardCharsets.UTF_8
        );
        return configPath;
    }

    private DbtConfigService serviceFor(Path configPath) {
        DbtProperties properties = new DbtProperties();
        properties.setEnabled(true);
        properties.setConfigPath(configPath.toString());
        properties.setProjectDir(tempDir.resolve("dbt").toString());
        properties.setProfilesDir(tempDir.resolve("profiles").toString());
        return new DbtConfigService(new ObjectMapper(), dataSourceRepository, secretService, properties);
    }

    private static final String MANAGED_MIRROR_PROPS =
        "{\"source\":\"admin-default-data-lake\",\"defaultLake\":true,\"adminDataLakeId\":\"b6f121f0-52d2-4315-8002-c8dc0c4e6235\"}";

    private static InfraDataSource source(String jdbcUrl, String props) {
        InfraDataSource source = new InfraDataSource();
        source.setId(UUID.randomUUID());
        source.setName("数仓");
        source.setType("POSTGRESQL");
        source.setJdbcUrl(jdbcUrl);
        source.setUsername("biadmin");
        source.setStatus("ACTIVE");
        source.setProps(props);
        return source;
    }
}
