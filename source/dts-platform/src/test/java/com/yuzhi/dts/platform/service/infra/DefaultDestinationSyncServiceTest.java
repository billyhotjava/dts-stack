package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultDestinationSyncServiceTest {

    @Mock
    private AdminInfraClient adminInfraClient;

    @Mock
    private InfraDataSourceRepository dataSourceRepository;

    @Mock
    private InfraSecretService secretService;

    private DefaultDestinationSyncService service;

    @BeforeEach
    void setUp() {
        service = new DefaultDestinationSyncService(
            adminInfraClient,
            dataSourceRepository,
            secretService,
            new ObjectMapper()
        );
    }

    @Test
    void synchronizeManagedDefaultLakeOnStartup_repairsStaleManagedMirror() {
        UUID platformSourceId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        AdminInfraClient.AdminDataLakeConfig adminLake = adminLake(
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "postgresqlwriter"
        );
        InfraDataSource staleMirror = source(
            platformSourceId,
            "数据仓库 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin1",
            "POSTGRESQL"
        );
        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake));
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(staleMirror));
        when(dataSourceRepository.findById(platformSourceId)).thenReturn(Optional.of(staleMirror));
        when(dataSourceRepository.save(staleMirror)).thenReturn(staleMirror);

        service.synchronizeManagedDefaultLakeOnStartup();

        assertThat(staleMirror.getJdbcUrl()).isEqualTo("jdbc:postgresql://dts-pg:5432/biadmin");
        org.mockito.Mockito.verify(dataSourceRepository).save(staleMirror);
    }

    @Test
    void synchronizeManagedDefaultLakeOnStartup_doesNotAbortStartupWhenAdminUnavailable() {
        UUID platformSourceId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        InfraDataSource staleMirror = source(
            platformSourceId,
            "数据仓库 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin1",
            "POSTGRESQL"
        );
        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.empty());
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(staleMirror));
        when(secretService.readSecrets(staleMirror)).thenReturn(Map.of());

        org.assertj.core.api.Assertions.assertThatCode(service::synchronizeManagedDefaultLakeOnStartup)
            .doesNotThrowAnyException();
        org.mockito.Mockito.verify(dataSourceRepository, org.mockito.Mockito.never())
            .save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void checkDefaultDestinationStatus_resolvesBiadminLocalSourceWhenAdminNameAndJdbcDiffer() {
        UUID platformSourceId = UUID.randomUUID();
        AdminInfraClient.AdminDataLakeConfig adminLake = adminLake(
            "数仓 (biadmin)",
            "jdbc:postgresql://10.10.10.10:5432/biadmin",
            "postgresqlwriter"
        );
        InfraDataSource platformSource = source(
            platformSourceId,
            "数据仓库 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "POSTGRESQL"
        );
        platformSource.setProps("{\"source\":\"admin-default-data-lake\",\"defaultLake\":true}");

        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake));
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(platformSource));

        DefaultDestinationSyncService.DefaultDestinationStatus status = service.checkDefaultDestinationStatus();

        assertThat(status.available()).isTrue();
        assertThat(status.writerType()).isEqualTo("postgresqlwriter");
        assertThat(status.dataSourceId()).isEqualTo(platformSourceId.toString());
    }

    @Test
    void checkDefaultDestinationStatus_reusesExistingPlatformSourceWhenOnlyAliasDiffers() {
        UUID platformSourceId = UUID.randomUUID();
        AdminInfraClient.AdminDataLakeConfig adminLake = adminLake(
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "postgresqlwriter"
        );
        InfraDataSource platformSource = source(
            platformSourceId,
            "现场手工湖仓",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "POSTGRESQL"
        );
        platformSource.setProps("{\"source\":\"admin-default-data-lake\",\"defaultLake\":true}");

        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake));
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(platformSource));

        DefaultDestinationSyncService.DefaultDestinationStatus status = service.checkDefaultDestinationStatus();

        assertThat(status.available()).isTrue();
        assertThat(status.dataSourceId()).isEqualTo(platformSourceId.toString());
        org.mockito.Mockito.verify(dataSourceRepository).save(org.mockito.ArgumentMatchers.same(platformSource));
    }

    @Test
    void ensureDefaultDestination_carriesCanonicalPlatformDataSourceId() {
        UUID platformSourceId = UUID.randomUUID();
        AdminInfraClient.AdminDataLakeConfig adminLake = adminLake(
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "postgresqlwriter"
        );
        InfraDataSource platformSource = source(
            platformSourceId,
            "现场手工湖仓",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "POSTGRESQL"
        );
        platformSource.setProps("{\"source\":\"admin-default-data-lake\",\"defaultLake\":true}");

        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake));
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(platformSource));

        DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot = service.ensureDefaultDestination();

        assertThat(snapshot.dataSourceId()).isEqualTo(platformSourceId.toString());
        assertThat(snapshot.destinationConfig()).containsEntry("jdbcUrl", "jdbc:postgresql://dts-pg:5432/biadmin");
    }

    @Test
    void ensureDestination_usesRequestedPlatformDataSourceInsteadOfAdminDefault() {
        UUID selectedSourceId = UUID.randomUUID();
        InfraDataSource selected = source(
            selectedSourceId,
            "经营分析 biadmin 湖仓",
            "jdbc:postgresql://analytics-pg:5432/ads",
            "POSTGRESQL"
        );
        selected.setProps("{\"schema\":\"ads\"}");

        when(dataSourceRepository.findById(selectedSourceId)).thenReturn(Optional.of(selected));
        when(secretService.readSecrets(selected)).thenReturn(Map.of("password", "analytics-secret"));

        DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot = service.ensureDestination(selectedSourceId.toString());

        assertThat(snapshot.dataSourceId()).isEqualTo(selectedSourceId.toString());
        assertThat(snapshot.destinationName()).isEqualTo("经营分析 biadmin 湖仓");
        assertThat(snapshot.destinationDefinitionId()).isEqualTo("postgresqlwriter");
        assertThat(snapshot.destinationConfig())
            .containsEntry("jdbcUrl", "jdbc:postgresql://analytics-pg:5432/ads")
            .containsEntry("username", "biadmin")
            .containsEntry("password", "analytics-secret");
        org.mockito.Mockito.verifyNoInteractions(adminInfraClient);
    }

    @Test
    void checkDefaultDestinationStatus_doesNotReuseDifferentBiadminDatabase() {
        UUID staleSourceId = UUID.randomUUID();
        UUID canonicalSourceId = UUID.randomUUID();
        AdminInfraClient.AdminDataLakeConfig adminLake = adminLake(
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "postgresqlwriter"
        );
        InfraDataSource staleSource = source(
            staleSourceId,
            "历史数仓 (biadmin1)",
            "jdbc:postgresql://dts-pg:5432/biadmin1",
            "POSTGRESQL"
        );

        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake));
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(staleSource));
        when(dataSourceRepository.save(org.mockito.ArgumentMatchers.any(InfraDataSource.class))).thenAnswer(invocation -> {
            InfraDataSource saved = invocation.getArgument(0);
            saved.setId(canonicalSourceId);
            return saved;
        });

        DefaultDestinationSyncService.DefaultDestinationStatus status = service.checkDefaultDestinationStatus();

        assertThat(status.dataSourceId()).isEqualTo(canonicalSourceId.toString());
        ArgumentCaptor<InfraDataSource> captor = ArgumentCaptor.forClass(InfraDataSource.class);
        org.mockito.Mockito.verify(dataSourceRepository).save(captor.capture());
        assertThat(captor.getValue().getJdbcUrl()).isEqualTo("jdbc:postgresql://dts-pg:5432/biadmin");
    }

    @Test
    void ensureDestination_usesAdminSnapshotForManagedDefaultMirror() throws Exception {
        UUID adminSourceId = UUID.randomUUID();
        UUID platformSourceId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        AdminInfraClient.AdminDataLakeConfig adminLake = adminLake(
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "postgresqlwriter"
        );
        setField(adminLake, "id", adminSourceId);
        InfraDataSource staleMirror = source(
            platformSourceId,
            "数据仓库 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin1",
            "POSTGRESQL"
        );
        staleMirror.setProps("{\"source\":\"admin-data-lake\",\"system\":true,\"ops\":{\"tableCount\":9}}");

        when(dataSourceRepository.findById(platformSourceId)).thenReturn(Optional.of(staleMirror));
        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake));
        when(secretService.readSecrets(staleMirror)).thenReturn(Map.of("password", "existing-secret"));
        when(dataSourceRepository.save(staleMirror)).thenReturn(staleMirror);

        DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot = service.ensureDestination(platformSourceId.toString());

        assertThat(snapshot.dataSourceId()).isEqualTo(platformSourceId.toString());
        assertThat(snapshot.destinationConfig()).containsEntry("jdbcUrl", "jdbc:postgresql://dts-pg:5432/biadmin");
        assertThat(staleMirror.getJdbcUrl()).isEqualTo("jdbc:postgresql://dts-pg:5432/biadmin");
        Map<String, Object> props = new ObjectMapper().readValue(staleMirror.getProps(), Map.class);
        assertThat(props)
            .containsEntry("source", "admin-default-data-lake")
            .containsEntry("defaultLake", true)
            .containsKey("ops");
        org.mockito.Mockito.verify(secretService, org.mockito.Mockito.never())
            .applySecrets(
                org.mockito.ArgumentMatchers.any(InfraDataSource.class),
                org.mockito.ArgumentMatchers.anyMap()
            );
    }

    @Test
    void ensureDestination_updatesAdminPasswordWithoutDroppingOtherSecrets() {
        UUID adminSourceId = UUID.randomUUID();
        UUID platformSourceId = UUID.randomUUID();
        AdminInfraClient.AdminDataLakeConfig adminLake = adminLake(
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "postgresqlwriter"
        );
        setField(adminLake, "id", adminSourceId);
        setField(adminLake, "password", "admin-secret");
        InfraDataSource managedMirror = source(
            platformSourceId,
            "数据仓库 (biadmin)",
            "jdbc:postgresql://old-pg:5432/biadmin",
            "POSTGRESQL"
        );
        managedMirror.setProps("{\"source\":\"admin-data-lake\",\"system\":true}");

        when(dataSourceRepository.findById(platformSourceId)).thenReturn(Optional.of(managedMirror));
        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake));
        when(secretService.readSecrets(managedMirror))
            .thenReturn(Map.of("password", "old-secret", "destinationToken", "keep-me"));
        when(dataSourceRepository.save(managedMirror)).thenReturn(managedMirror);

        service.ensureDestination(platformSourceId.toString());

        org.mockito.Mockito.verify(secretService).applySecrets(
            org.mockito.ArgumentMatchers.same(managedMirror),
            org.mockito.ArgumentMatchers.argThat(
                secrets ->
                    "admin-secret".equals(secrets.get("password"))
                        && "keep-me".equals(secrets.get("destinationToken"))
            )
        );
    }

    @Test
    void ensureDestination_failsClosedWhenManagedMirrorCannotReachAdmin() {
        UUID platformSourceId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        InfraDataSource managedMirror = source(
            platformSourceId,
            "数据仓库 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin1",
            "POSTGRESQL"
        );
        when(dataSourceRepository.findById(platformSourceId)).thenReturn(Optional.of(managedMirror));
        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.ensureDestination(platformSourceId.toString()))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("默认数据湖配置暂不可用");
    }

    @Test
    void checkDefaultDestinationStatus_rejectsInactiveAdminLake() {
        AdminInfraClient.AdminDataLakeConfig adminLake = adminLake(
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "postgresqlwriter"
        );
        setField(adminLake, "status", "INACTIVE");
        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake));

        DefaultDestinationSyncService.DefaultDestinationStatus status = service.checkDefaultDestinationStatus();

        assertThat(status.available()).isFalse();
        assertThat(status.message()).contains("INACTIVE");
        org.mockito.Mockito.verifyNoInteractions(dataSourceRepository);
    }

    @Test
    void checkDefaultDestinationStatus_rejectsFailedAdminHeartbeat() {
        AdminInfraClient.AdminDataLakeConfig adminLake = adminLake(
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "postgresqlwriter"
        );
        setField(adminLake, "heartbeatStatus", "DOWN");
        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake));

        DefaultDestinationSyncService.DefaultDestinationStatus status = service.checkDefaultDestinationStatus();

        assertThat(status.available()).isFalse();
        assertThat(status.message()).contains("DOWN");
        org.mockito.Mockito.verifyNoInteractions(dataSourceRepository);
    }

    @Test
    void checkDefaultDestinationStatus_failsClosedWhenMirrorSaveFails() {
        UUID platformSourceId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        AdminInfraClient.AdminDataLakeConfig adminLake = adminLake(
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "postgresqlwriter"
        );
        InfraDataSource staleMirror = source(
            platformSourceId,
            "数据仓库 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin1",
            "POSTGRESQL"
        );
        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake));
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(staleMirror));
        when(dataSourceRepository.findById(platformSourceId)).thenReturn(Optional.of(staleMirror));
        when(dataSourceRepository.save(staleMirror)).thenThrow(new IllegalStateException("database unavailable"));

        org.assertj.core.api.Assertions.assertThatThrownBy(service::checkDefaultDestinationStatus)
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("默认数据湖镜像同步失败");
    }

    @Test
    void ensureDestination_doesNotMutateMirrorWhenPropsSerializationFails() throws Exception {
        UUID platformSourceId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        AdminInfraClient.AdminDataLakeConfig adminLake = adminLake(
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "postgresqlwriter"
        );
        InfraDataSource staleMirror = source(
            platformSourceId,
            "数据仓库 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin1",
            "POSTGRESQL"
        );
        String originalProps = "{\"source\":\"admin-data-lake\",\"system\":true}";
        staleMirror.setProps(originalProps);
        ObjectMapper failingMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
                throw new com.fasterxml.jackson.core.JsonProcessingException("boom") {};
            }
        };
        DefaultDestinationSyncService failingService = new DefaultDestinationSyncService(
            adminInfraClient,
            dataSourceRepository,
            secretService,
            failingMapper
        );
        when(dataSourceRepository.findById(platformSourceId)).thenReturn(Optional.of(staleMirror));
        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> failingService.ensureDestination(platformSourceId.toString()))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("默认数据湖镜像同步失败");
        assertThat(staleMirror.getJdbcUrl()).isEqualTo("jdbc:postgresql://dts-pg:5432/biadmin1");
        assertThat(staleMirror.getProps()).isEqualTo(originalProps);
        org.mockito.Mockito.verify(dataSourceRepository, org.mockito.Mockito.never())
            .save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void checkDefaultDestinationStatus_prefersMirrorBoundToCurrentAdminLake() {
        UUID adminSourceId = UUID.randomUUID();
        UUID otherAdminSourceId = UUID.randomUUID();
        UUID unboundMirrorId = UUID.fromString("10000000-0000-0000-0000-000000000010");
        UUID boundMirrorId = UUID.fromString("f0000000-0000-0000-0000-000000000020");
        AdminInfraClient.AdminDataLakeConfig adminLake = adminLake(
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "postgresqlwriter"
        );
        setField(adminLake, "id", adminSourceId);
        InfraDataSource unboundMirror = source(
            unboundMirrorId,
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "POSTGRESQL"
        );
        unboundMirror.setProps(
            "{\"source\":\"admin-default-data-lake\",\"defaultLake\":true,\"adminDataLakeId\":\""
                + otherAdminSourceId
                + "\"}"
        );
        InfraDataSource boundMirror = source(
            boundMirrorId,
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "POSTGRESQL"
        );
        boundMirror.setProps(
            "{\"source\":\"admin-default-data-lake\",\"defaultLake\":true,\"adminDataLakeId\":\""
                + adminSourceId
                + "\"}"
        );
        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake));
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE"))
            .thenReturn(List.of(unboundMirror, boundMirror));

        DefaultDestinationSyncService.DefaultDestinationStatus status = service.checkDefaultDestinationStatus();

        assertThat(status.dataSourceId()).isEqualTo(boundMirrorId.toString());
    }

    @Test
    void checkDefaultDestinationStatus_createsPlatformDataSourceWhenAdminDefaultLakeHasNoLocalMatch() {
        UUID adminSourceId = UUID.randomUUID();
        UUID platformSourceId = UUID.randomUUID();
        AdminInfraClient.AdminDataLakeConfig adminLake = adminLake(
            "数仓 (biadmin)",
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "postgresqlwriter"
        );
        setField(adminLake, "id", adminSourceId);
        setField(adminLake, "password", "secret");
        setField(
            adminLake,
            "destinationConfig",
            Map.of(
                "jdbcUrl",
                "jdbc:postgresql://dts-pg:5432/biadmin",
                "username",
                "biadmin",
                "schema",
                "ods",
                "connection",
                List.of(
                    Map.of(
                        "apiKey",
                        "api-key-value",
                        "authorization",
                        "Bearer hidden",
                        "accessKey",
                        "access-key-value",
                        "credential",
                        "credential-value",
                        "privateKey",
                        "private-key-value",
                        "keytabBase64",
                        "keytab-value",
                        "passphrase",
                        "passphrase-value",
                        "pwd",
                        "pwd-value",
                        "safeOption",
                        "keep-me"
                    )
                )
            )
        );

        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake));
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of());
        when(dataSourceRepository.findAll()).thenReturn(List.of());
        when(dataSourceRepository.save(org.mockito.ArgumentMatchers.any(InfraDataSource.class))).thenAnswer(invocation -> {
            InfraDataSource saved = invocation.getArgument(0);
            saved.setId(platformSourceId);
            return saved;
        });

        DefaultDestinationSyncService.DefaultDestinationStatus status = service.checkDefaultDestinationStatus();

        assertThat(status.available()).isTrue();
        assertThat(status.dataSourceId()).isEqualTo(platformSourceId.toString());
        assertThat(status.dataSourceId()).isNotEqualTo(adminSourceId.toString());
        ArgumentCaptor<InfraDataSource> captor = ArgumentCaptor.forClass(InfraDataSource.class);
        org.mockito.Mockito.verify(dataSourceRepository).save(captor.capture());
        InfraDataSource saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("数仓 (biadmin)");
        assertThat(saved.getType()).isEqualTo("POSTGRESQL");
        assertThat(saved.getJdbcUrl()).isEqualTo("jdbc:postgresql://dts-pg:5432/biadmin");
        assertThat(saved.getUsername()).isEqualTo("biadmin");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getOwnerDept()).isNull();
        Map<String, Object> props = readMap(saved.getProps());
        assertThat(props)
            .containsEntry("source", "admin-default-data-lake")
            .containsEntry("defaultLake", true)
            .containsEntry("adminDataLakeId", adminSourceId.toString());
        assertThat(saved.getProps())
            .contains("\"schema\":\"ods\"")
            .doesNotContain(
                "api-key-value",
                "Bearer hidden",
                "access-key-value",
                "credential-value",
                "private-key-value",
                "keytab-value",
                "passphrase-value",
                "pwd-value",
                "safeOption",
                "keep-me"
            );
        org.mockito.Mockito.verify(secretService).applySecrets(
            org.mockito.ArgumentMatchers.same(saved),
            org.mockito.ArgumentMatchers.argThat(secrets -> "secret".equals(secrets.get("password")))
        );
    }

    private Map<String, Object> readMap(String raw) {
        try {
            return new ObjectMapper().readValue(raw, Map.class);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private AdminInfraClient.AdminDataLakeConfig adminLake(String name, String jdbcUrl, String writerType) {
        AdminInfraClient.AdminDataLakeConfig lake = new AdminInfraClient.AdminDataLakeConfig();
        setField(lake, "name", name);
        setField(lake, "type", "JDBC");
        setField(lake, "jdbcUrl", jdbcUrl);
        setField(lake, "username", "biadmin");
        setField(lake, "destinationName", name);
        setField(lake, "destinationDefinitionId", writerType);
        setField(lake, "destinationConfig", Map.of("jdbcUrl", jdbcUrl, "username", "biadmin"));
        setField(lake, "status", "ACTIVE");
        return lake;
    }

    private InfraDataSource source(UUID id, String name, String jdbcUrl, String type) {
        InfraDataSource source = new InfraDataSource();
        source.setId(id);
        source.setName(name);
        source.setJdbcUrl(jdbcUrl);
        source.setType(type);
        source.setUsername("biadmin");
        source.setStatus("ACTIVE");
        return source;
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to set field " + name, ex);
        }
    }
}
