package com.yuzhi.dts.copilot.analytics.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.config.DefaultDatabaseProperties;
import com.yuzhi.dts.copilot.analytics.config.DefaultDatabaseProperties.DatabaseEntry;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabaseRole;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class DefaultDatabaseInitServiceTest {

    @Mock
    private AnalyticsDatabaseRepository databaseRepository;

    @Mock
    private MetadataSyncService metadataSyncService;

    @Mock
    private PlatformInfraClient platformInfraClient;

    @Test
    void runSkipsSystemRuntimePostgresDefaults() throws Exception {
        DefaultDatabaseProperties properties = new DefaultDatabaseProperties(List.of(
                new DatabaseEntry("园林业务库", "SYSTEM_RUNTIME", "postgresql", "localhost", 5432, "garden", "readonly", "", true),
                new DatabaseEntry("Copilot业务库", "SYSTEM_RUNTIME", "postgresql", "copilot-postgres", 5432, "copilot", "copilot", "", true)));
        when(databaseRepository.findAll()).thenReturn(List.of());

        DefaultDatabaseInitService service = new DefaultDatabaseInitService(
                properties,
                databaseRepository,
                metadataSyncService,
                platformInfraClient,
                new ObjectMapper());

        service.run(new DefaultApplicationArguments());

        verify(platformInfraClient, never()).createDataSource(any());
        verify(databaseRepository, never()).save(any());
        verify(metadataSyncService, never()).syncDatabaseSchema(any(Long.class));
    }

    @Test
    void runStillRegistersExternalBusinessDatabaseDefaults() throws Exception {
        DefaultDatabaseProperties properties = new DefaultDatabaseProperties(List.of(
                new DatabaseEntry("新业务测试库1", "BUSINESS_PRIMARY", "mysql", "db.xycyl.com", 3306, "rs_cloud_flower", "yuzhicloud", "secret", true)));
        when(databaseRepository.findAll()).thenReturn(List.of());
        when(platformInfraClient.createDataSource(any())).thenReturn(new PlatformInfraClient.DataSourceSummary(
                "9",
                "新业务测试库1",
                "mysql",
                "jdbc:mysql://db.xycyl.com:3306/rs_cloud_flower",
                null,
                null,
                "ACTIVE",
                null,
                null));
        when(databaseRepository.save(any())).thenAnswer(invocation -> {
            AnalyticsDatabase database = invocation.getArgument(0);
            database.setId(8L);
            return database;
        });
        when(metadataSyncService.syncDatabaseSchema(8L))
                .thenReturn(new MetadataSyncService.SyncSummary(8L, 1, 0, 0, 4, 0, 0));

        DefaultDatabaseInitService service = new DefaultDatabaseInitService(
                properties,
                databaseRepository,
                metadataSyncService,
                platformInfraClient,
                new ObjectMapper());

        service.run(new DefaultApplicationArguments());

        ArgumentCaptor<PlatformInfraClient.CreateDataSourceRequest> requestCaptor =
                ArgumentCaptor.forClass(PlatformInfraClient.CreateDataSourceRequest.class);
        ArgumentCaptor<AnalyticsDatabase> databaseCaptor = ArgumentCaptor.forClass(AnalyticsDatabase.class);
        verify(platformInfraClient).createDataSource(requestCaptor.capture());
        verify(databaseRepository).save(databaseCaptor.capture());
        verify(metadataSyncService).syncDatabaseSchema(8L);
        PlatformInfraClient.CreateDataSourceRequest request = requestCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(request.name()).isEqualTo("新业务测试库1");
        org.assertj.core.api.Assertions.assertThat(request.jdbcUrl())
                .isEqualTo("jdbc:mysql://db.xycyl.com:3306/rs_cloud_flower");
        org.assertj.core.api.Assertions.assertThat(databaseCaptor.getValue().getDatabaseRole())
                .isEqualTo(AnalyticsDatabaseRole.BUSINESS_PRIMARY);
    }
}
