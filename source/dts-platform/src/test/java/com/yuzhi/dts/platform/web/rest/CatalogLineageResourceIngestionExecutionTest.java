package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.catalog.CatalogColumnLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogLineageJobRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.lineage.IngestionLineageWriter;
import com.yuzhi.dts.platform.service.etl.OdsTableMappingSyncService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CatalogLineageResourceIngestionExecutionTest {

    @Mock private CatalogDatasetRepository datasetRepository;
    @Mock private CatalogDatasetLineageRepository lineageRepository;
    @Mock private CatalogColumnLineageRepository columnLineageRepository;
    @Mock private CatalogLineageJobRepository lineageJobRepository;
    @Mock private InfraOdsTableMappingRepository mappingRepository;
    @Mock private InfraDataSourceRepository dataSourceRepository;
    @Mock private IngestionLineageWriter lineageWriter;
    @Mock private OdsTableMappingSyncService mappingSyncService;
    @Mock private AccessChecker accessChecker;
    @Mock private AuditService auditService;

    private CatalogLineageResource resource;

    @BeforeEach
    void setUp() {
        resource = new CatalogLineageResource(
            datasetRepository,
            lineageRepository,
            columnLineageRepository,
            lineageJobRepository,
            mappingRepository,
            dataSourceRepository,
            lineageWriter,
            mappingSyncService,
            accessChecker,
            auditService
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ordinaryCatalogMaintainerCannotSubmitApiLandingTruth() {
        authenticate("catalog-user", AuthoritiesConstants.ADMIN);

        assertThatThrownBy(() -> resource.syncIngestionExecutionLineage(validPayload()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("API landing evidence requires dts-ingestion service authentication");
        verify(mappingSyncService, never()).syncFromIngestionPayload(any(), any());
    }

    @Test
    void ingestionServiceCannotSubmitApiLandingWithoutChecksumsAndTargetEvidence() {
        authenticate("service:dts-ingestion", AuthoritiesConstants.SERVICE_INTERNAL);
        Map<String, Object> invalid = Map.of(
            "task",
            Map.of(
                "id", "10",
                "sourceKind", "API",
                "sourceDataSourceId", UUID.randomUUID().toString(),
                "taskRevision", "2026-07-24T07:00:00Z"
            ),
            "execution",
            Map.of("id", 100L, "executionId", "api-100", "status", "SUCCESS", "targetTables", List.of(Map.of("resourceId", "orders")))
        );

        assertThatThrownBy(() -> resource.syncIngestionExecutionLineage(invalid))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("qualifiedName");
        verify(mappingSyncService, never()).syncFromIngestionPayload(any(), any());
    }

    @Test
    void ingestionServiceSubmitsOnlyCompleteSuccessfulApiLandingEvidence() {
        authenticate("service:dts-ingestion", AuthoritiesConstants.SERVICE_INTERNAL);
        when(mappingSyncService.syncFromIngestionPayload(any(), any()))
            .thenReturn(new OdsTableMappingSyncService.SyncResult(true, 1, "ok"));

        resource.syncIngestionExecutionLineage(validPayload());

        verify(mappingSyncService).syncFromIngestionPayload(any(), any());
    }

    private static void authenticate(String name, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(name, null, List.of(new SimpleGrantedAuthority(authority)))
        );
    }

    private static Map<String, Object> validPayload() {
        UUID connectionId = UUID.fromString("2b0fce68-0c78-41f4-9c63-0f6d1e9292e1");
        return Map.of(
            "task",
            Map.of(
                "id", "10",
                "name", "orders-api",
                "sourceType", "httpreader",
                "sourceKind", "API",
                "sourceDataSourceId", connectionId.toString(),
                "taskRevision", "2026-07-24T07:00:00Z"
            ),
            "execution",
            Map.of(
                "id", 100L,
                "executionSequence", 100L,
                "executionId", "api-100",
                "status", "SUCCESS",
                "endTime", "2026-07-24T08:00:00Z",
                "targetTables",
                List.of(
                    Map.of(
                        "resourceId", "orders",
                        "qualifiedName", "ods.ods_api_orders",
                        "executionId", "api-100",
                        "landingStatus", "SUCCESS",
                        "rowsWritten", 9L,
                        "configChecksum", "sha256:" + "a".repeat(64),
                        "fieldSnapshotChecksum", "sha256:" + "b".repeat(64)
                    )
                )
            )
        );
    }
}
