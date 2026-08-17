package com.yuzhi.dts.platform.web.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetGrantRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.governance.GovIssueTicketRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CatalogGovernanceResourceTest {

    @Test
    void succeededQualityRunIsCountedAsPassed() {
        UUID datasetId = UUID.randomUUID();
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        dataset.setName("已有质量证据资产");
        GovQualityRun run = new GovQualityRun();
        run.setId(UUID.randomUUID());
        run.setDatasetId(datasetId);
        run.setStatus("SUCCEEDED");
        run.setCreatedDate(Instant.now());

        CatalogDatasetRepository datasetRepository = mock(CatalogDatasetRepository.class);
        CatalogTableSchemaRepository tableRepository = mock(CatalogTableSchemaRepository.class);
        CatalogDatasetGrantRepository grantRepository = mock(CatalogDatasetGrantRepository.class);
        GovQualityRunRepository qualityRunRepository = mock(GovQualityRunRepository.class);
        GovIssueTicketRepository issueRepository = mock(GovIssueTicketRepository.class);
        AuditService auditService = mock(AuditService.class);
        AccessChecker accessChecker = mock(AccessChecker.class);
        CatalogResourceHelper helper = mock(CatalogResourceHelper.class);
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowed(dataset, "D01")).thenReturn(true);
        when(qualityRunRepository.findByDatasetId(any(), any())).thenReturn(List.of(run));
        when(qualityRunRepository.countByDatasetId(datasetId)).thenReturn(1L);
        when(qualityRunRepository.findFirstByDatasetIdOrderByCreatedDateDesc(datasetId)).thenReturn(Optional.of(run));
        when(issueRepository.findTop100ByDatasetIdOrderByCreatedDateDesc(datasetId)).thenReturn(List.of());
        when(helper.normalizeUpper("SUCCEEDED")).thenReturn("SUCCEEDED");

        CatalogGovernanceResource resource = new CatalogGovernanceResource(
            datasetRepository,
            tableRepository,
            grantRepository,
            qualityRunRepository,
            issueRepository,
            auditService,
            accessChecker,
            helper
        );

        Map<String, Object> payload = resource.getDatasetGovernanceHealth(datasetId, "D01").getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> quality = (Map<String, Object>) payload.get("quality");

        assertThat(quality).containsEntry("passRuns", 1L).containsEntry("latestStatus", "SUCCEEDED");
    }

    @Test
    void noQualityRunProducesUnknownHealthInsteadOfHealthyOneHundred() {
        UUID datasetId = UUID.randomUUID();
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        dataset.setName("无质量证据资产");

        CatalogDatasetRepository datasetRepository = mock(CatalogDatasetRepository.class);
        CatalogTableSchemaRepository tableRepository = mock(CatalogTableSchemaRepository.class);
        CatalogDatasetGrantRepository grantRepository = mock(CatalogDatasetGrantRepository.class);
        GovQualityRunRepository qualityRunRepository = mock(GovQualityRunRepository.class);
        GovIssueTicketRepository issueRepository = mock(GovIssueTicketRepository.class);
        AuditService auditService = mock(AuditService.class);
        AccessChecker accessChecker = mock(AccessChecker.class);
        CatalogResourceHelper helper = mock(CatalogResourceHelper.class);
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowed(dataset, "D01")).thenReturn(true);
        when(qualityRunRepository.findByDatasetId(any(), any())).thenReturn(List.of());
        when(qualityRunRepository.countByDatasetId(datasetId)).thenReturn(0L);
        when(qualityRunRepository.findFirstByDatasetIdOrderByCreatedDateDesc(datasetId)).thenReturn(Optional.empty());
        when(issueRepository.findTop100ByDatasetIdOrderByCreatedDateDesc(datasetId)).thenReturn(List.of());

        CatalogGovernanceResource resource = new CatalogGovernanceResource(
            datasetRepository,
            tableRepository,
            grantRepository,
            qualityRunRepository,
            issueRepository,
            auditService,
            accessChecker,
            helper
        );

        Map<String, Object> payload = resource.getDatasetGovernanceHealth(datasetId, "D01").getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> quality = (Map<String, Object>) payload.get("quality");

        assertThat(payload.get("healthScore")).isNull();
        assertThat(payload).containsEntry("healthLevel", "UNKNOWN");
        assertThat(quality).containsEntry("evidenceState", "MISSING");
    }
}
