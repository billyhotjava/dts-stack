package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PlatformAnalyticsDatabaseRegistrationServiceTest {

    private final AnalyticsDatabaseRepository databases = mock(AnalyticsDatabaseRepository.class);
    private final PlatformInfraClient platform = mock(PlatformInfraClient.class);
    private final JdbcDetailsResolver jdbcDetailsResolver = mock(JdbcDetailsResolver.class);
    private final MetadataSyncService metadata = mock(MetadataSyncService.class);
    private final PlatformAnalyticsDatabaseBindingWriter bindingWriter = mock(PlatformAnalyticsDatabaseBindingWriter.class);
    private final PlatformAnalyticsDatabaseRegistrationService service = new PlatformAnalyticsDatabaseRegistrationService(
        databases, platform, jdbcDetailsResolver, metadata, bindingWriter, new ObjectMapper(), "default"
    );

    @Test
    void reusesTheExactTenantAndStablePlatformSourceBinding() {
        UUID sourceId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        AnalyticsDatabase existing = new AnalyticsDatabase();
        existing.setId(9L);
        when(databases.findByTenantIdAndPlatformDataSourceId("tenant-a", sourceId)).thenReturn(Optional.of(existing));

        assertThat(service.ensureDatabase("tenant-a", sourceId)).isSameAs(existing);

        verifyNoInteractions(platform, jdbcDetailsResolver);
        verify(bindingWriter, never()).insert(any());
    }

    @Test
    void refusesAnUnassignedLegacyBindingInsteadOfCreatingASecondSource() {
        UUID sourceId = UUID.fromString("10000000-0000-0000-0000-000000000002");
        AnalyticsDatabase legacy = new AnalyticsDatabase();
        legacy.setDetailsJson("{\"platform\":{\"dataSourceId\":\"" + sourceId + "\"}}");
        when(databases.findByTenantIdAndPlatformDataSourceId("tenant-a", sourceId)).thenReturn(Optional.empty());
        when(databases.findAll()).thenReturn(List.of(legacy));

        assertThatThrownBy(() -> service.ensureDatabase("tenant-a", sourceId))
            .isInstanceOf(PlatformAnalyticsDatabaseRegistrationService.AnalysisRegistrationException.class)
            .hasMessage("ANALYSIS_LEGACY_SOURCE_UNRESOLVED");

        verifyNoInteractions(platform, jdbcDetailsResolver);
        verify(bindingWriter, never()).insert(any());
    }

    @Test
    void uniqueConstraintRaceReturnsTheBindingCreatedByTheOtherTransaction() {
        UUID sourceId = UUID.fromString("10000000-0000-0000-0000-000000000003");
        AnalyticsDatabase winner = new AnalyticsDatabase();
        winner.setId(10L);
        winner.setTenantId("tenant-a");
        winner.setPlatformDataSourceId(sourceId);
        when(databases.findByTenantIdAndPlatformDataSourceId("tenant-a", sourceId))
            .thenReturn(Optional.empty(), Optional.of(winner));
        when(databases.findAll()).thenReturn(List.of());
        when(platform.fetchDataSourceDetail(sourceId)).thenReturn(detail(sourceId));
        when(bindingWriter.insert(any(AnalyticsDatabase.class))).thenThrow(new DataIntegrityViolationException("unique binding"));

        assertThat(service.ensureDatabase("tenant-a", sourceId)).isSameAs(winner);

        verify(bindingWriter).insert(any(AnalyticsDatabase.class));
        verify(platform).fetchDataSourceDetail(sourceId);
    }

    @Test
    void adoptsOnlyTheExplicitSystemDataLakeLegacyRow() {
        UUID sourceId = UUID.fromString("10000000-0000-0000-0000-000000000004");
        AnalyticsDatabase legacyDataLake = new AnalyticsDatabase();
        legacyDataLake.setId(11L);
        legacyDataLake.setDetailsJson("{\"source\":\"data-lake\",\"system\":true,\"platformDataSourceId\":\"" + sourceId + "\"}");
        when(databases.findAll()).thenReturn(List.of(legacyDataLake));
        when(bindingWriter.insert(legacyDataLake)).thenReturn(legacyDataLake);
        when(databases.save(legacyDataLake)).thenReturn(legacyDataLake);

        AnalyticsDatabase result = service.ensureDataLakeDatabase(sourceId);

        assertThat(result.getTenantId()).isEqualTo("default");
        assertThat(result.getPlatformDataSourceId()).isEqualTo(sourceId);
        verifyNoInteractions(platform, jdbcDetailsResolver);
    }

    private static PlatformInfraClient.DataSourceDetail detail(UUID sourceId) {
        return new PlatformInfraClient.DataSourceDetail(
            sourceId.toString(), "warehouse", "postgres", "jdbc:postgresql://warehouse/analytics", "analytics",
            null, null, Map.of(), Map.of(), "ACTIVE", null
        );
    }
}
