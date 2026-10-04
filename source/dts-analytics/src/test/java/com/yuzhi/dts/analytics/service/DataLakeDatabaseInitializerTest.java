package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DataLakeDatabaseInitializerTest {

    private final PlatformInfraClient platform = mock(PlatformInfraClient.class);
    private final MetadataSyncService metadata = mock(MetadataSyncService.class);
    private final PlatformAnalyticsDatabaseRegistrationService registration = mock(PlatformAnalyticsDatabaseRegistrationService.class);
    private final DataLakeDatabaseInitializer initializer = new DataLakeDatabaseInitializer(platform, metadata, registration);

    @Test
    void unavailablePlatformLeavesInitializationPendingWithoutPartialRegistration() {
        when(platform.listDataSources()).thenThrow(new IllegalStateException("dependency unavailable"));

        assertThat(initializer.initializeDataLake()).isFalse();

        verifyNoInteractions(registration, metadata);
    }

    @Test
    void missingBuiltInSourceLeavesInitializationPending() {
        when(platform.listDataSources()).thenReturn(List.of());

        assertThat(initializer.initializeDataLake()).isFalse();

        verifyNoInteractions(registration, metadata);
    }

    @Test
    void recoveredPlatformRegistersTheStableSourceIdAndCompletes() throws Exception {
        UUID sourceId = stubAvailableSource();
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(12L);
        when(registration.ensureDataLakeDatabase(sourceId)).thenReturn(database);

        assertThat(initializer.initializeDataLake()).isTrue();

        verify(registration).ensureDataLakeDatabase(sourceId);
        verify(metadata).syncDatabaseSchema(12L);
    }

    @Test
    void metadataFailureDoesNotPretendSourceRegistrationFailed() throws Exception {
        UUID sourceId = stubAvailableSource();
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(12L);
        when(registration.ensureDataLakeDatabase(sourceId)).thenReturn(database);
        doThrow(new SQLException("metadata unavailable")).when(metadata).syncDatabaseSchema(12L);

        assertThat(initializer.initializeDataLake()).isTrue();

        verify(registration).ensureDataLakeDatabase(sourceId);
    }

    private UUID stubAvailableSource() {
        UUID sourceId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        when(platform.listDataSources()).thenReturn(List.of(new PlatformInfraClient.DataSourceSummary(
            sourceId.toString(), "数仓", "postgres", "jdbc:postgresql://warehouse/biadmin",
            null, null, null, null, null
        )));
        return sourceId;
    }
}
