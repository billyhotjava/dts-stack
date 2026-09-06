package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataLakeDatabaseInitializerTest {

    private final AnalyticsDatabaseRepository databases = mock(AnalyticsDatabaseRepository.class);
    private final PlatformInfraClient platform = mock(PlatformInfraClient.class);
    private final MetadataSyncService metadata = mock(MetadataSyncService.class);
    private final DataLakeDatabaseInitializer initializer = new DataLakeDatabaseInitializer(
        databases, platform, metadata, new ObjectMapper()
    );

    @Test
    void existingBuiltInSourceCompletesWithoutCallingPlatformOrCreatingAnotherDatabase() {
        AnalyticsDatabase existing = new AnalyticsDatabase();
        existing.setDetailsJson("{\"source\":\"data-lake\",\"system\":true}");
        when(databases.findAll()).thenReturn(List.of(existing));

        assertThat(initializer.initializeDataLake()).isTrue();

        verifyNoInteractions(platform, metadata);
        verify(databases, never()).save(any());
    }

    @Test
    void unavailablePlatformLeavesInitializationPendingWithoutPartialRegistration() {
        when(databases.findAll()).thenReturn(List.of());
        when(platform.listDataSources()).thenThrow(new IllegalStateException("dependency unavailable"));

        assertThat(initializer.initializeDataLake()).isFalse();

        verify(databases, never()).save(any());
        verifyNoInteractions(metadata);
    }

    @Test
    void missingBuiltInSourceLeavesInitializationPending() {
        when(databases.findAll()).thenReturn(List.of());
        when(platform.listDataSources()).thenReturn(List.of());

        assertThat(initializer.initializeDataLake()).isFalse();

        verify(databases, never()).save(any());
        verifyNoInteractions(metadata);
    }

    @Test
    void recoveredPlatformRegistersTheStableSourceIdAndCompletes() throws Exception {
        stubAvailableSource();

        assertThat(initializer.initializeDataLake()).isTrue();

        var saved = ArgumentCaptor.forClass(AnalyticsDatabase.class);
        verify(databases).save(saved.capture());
        assertThat(new ObjectMapper().readTree(saved.getValue().getDetailsJson()).path("platformDataSourceId").asText())
            .isEqualTo("a0000000-0000-0000-0000-000000000001");
        assertThat(DataLakeDatabaseInitializer.isDataLakeDatabase(saved.getValue())).isTrue();
        verify(metadata).syncDatabaseSchema(12L);
    }

    @Test
    void metadataFailureDoesNotPretendSourceRegistrationFailed() throws Exception {
        stubAvailableSource();
        doThrow(new SQLException("metadata unavailable")).when(metadata).syncDatabaseSchema(12L);

        assertThat(initializer.initializeDataLake()).isTrue();

        verify(databases).save(any());
    }

    private void stubAvailableSource() {
        when(databases.findAll()).thenReturn(List.of());
        when(platform.listDataSources()).thenReturn(List.of(new PlatformInfraClient.DataSourceSummary(
            "a0000000-0000-0000-0000-000000000001", "数仓", "postgres", "jdbc:postgresql://warehouse/biadmin",
            null, null, null, null, null
        )));
        when(databases.save(any())).thenAnswer(invocation -> {
            AnalyticsDatabase saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });
    }
}
