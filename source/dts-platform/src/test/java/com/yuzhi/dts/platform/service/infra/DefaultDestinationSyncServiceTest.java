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

        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake));
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(platformSource));

        DefaultDestinationSyncService.DefaultDestinationStatus status = service.checkDefaultDestinationStatus();

        assertThat(status.available()).isTrue();
        assertThat(status.dataSourceId()).isEqualTo(platformSourceId.toString());
        org.mockito.Mockito.verify(dataSourceRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
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
        org.mockito.Mockito.verify(secretService).applySecrets(
            org.mockito.ArgumentMatchers.same(saved),
            org.mockito.ArgumentMatchers.argThat(secrets -> "secret".equals(secrets.get("password")))
        );
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
