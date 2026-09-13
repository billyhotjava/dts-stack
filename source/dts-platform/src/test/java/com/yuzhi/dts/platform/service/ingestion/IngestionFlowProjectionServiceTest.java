package com.yuzhi.dts.platform.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovQualityWorkflowRun;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityWorkflowRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetStatusViewService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetStatusViewService.AssetDeliveryStatus;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.governance.QualityDatasetReadGuard;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionFlowProjectionServiceTest {

    private static final UUID DATASET_ID = UUID.fromString("00000000-0000-0000-0000-000000000013");
    private static final UUID WORKFLOW_ID = UUID.fromString("10000000-0000-0000-0000-000000000013");

    @Mock private CatalogDatasetRepository datasetRepository;
    @Mock private GovRuleBindingRepository bindingRepository;
    @Mock private GovQualityWorkflowRunRepository workflowRepository;
    @Mock private CatalogAssetStatusViewService assetStatusViewService;
    @Mock private QualityDatasetReadGuard datasetReadGuard;

    private IngestionFlowProjectionService service;

    @BeforeEach
    void setUp() {
        service = new IngestionFlowProjectionService(
            datasetRepository,
            bindingRepository,
            workflowRepository,
            assetStatusViewService,
            datasetReadGuard
        );
    }

    @Test
    void currentPassedWorkflowAndEligibleAssetProduceTrustedUsableEvidence() {
        CatalogDataset dataset = dataset();
        GovQualityWorkflowRun workflow = workflow("ingestion:90", "PASSED");
        AssetRef ref = new AssetRef("DATASET", "source:unknown/schema:ods/table:cost_center");
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(dataset));
        when(datasetReadGuard.requireReadable(DATASET_ID, "FIN")).thenReturn(dataset);
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
        when(bindingRepository.findWorkflowBindings(DATASET_ID, "PUBLISHED")).thenReturn(List.of(new GovRuleBinding()));
        when(assetStatusViewService.read(List.of(ref))).thenReturn(Map.of(ref, eligibleStatus()));

        Map<String, Object> projected = service.enrichExecution(execution(), "FIN");

        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) projected.get("qualityEvidence");
        assertThat(evidence)
            .containsEntry("evidenceState", "CURRENT")
            .containsEntry("qualityStatus", "PASSED")
            .containsEntry("consumptionEligibility", "ELIGIBLE")
            .containsEntry("trustedUsable", true);
    }

    @Test
    void workflowFromAnotherExecutionIsStaleAndNeverTrusted() {
        CatalogDataset dataset = dataset();
        GovQualityWorkflowRun workflow = workflow("ingestion:89", "PASSED");
        AssetRef ref = new AssetRef("DATASET", "source:unknown/schema:ods/table:cost_center");
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(dataset));
        when(datasetReadGuard.requireReadable(DATASET_ID, null)).thenReturn(dataset);
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
        when(bindingRepository.findWorkflowBindings(DATASET_ID, "PUBLISHED")).thenReturn(List.of(new GovRuleBinding()));
        when(assetStatusViewService.read(List.of(ref))).thenReturn(Map.of(ref, eligibleStatus()));

        Map<String, Object> projected = service.enrichExecution(execution(), null);

        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) projected.get("qualityEvidence");
        assertThat(evidence)
            .containsEntry("evidenceState", "STALE")
            .containsEntry("trustedUsable", false);
    }

    @Test
    void previousExecutionEvidenceBecomesStaleAsSoonAsANewerLedgerExists() {
        CatalogDataset dataset = dataset();
        GovQualityWorkflowRun workflow = workflow("ingestion:90", "PASSED");
        AssetRef ref = new AssetRef("DATASET", "source:unknown/schema:ods/table:cost_center");
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(dataset));
        when(datasetReadGuard.requireReadable(DATASET_ID, null)).thenReturn(dataset);
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
        when(bindingRepository.findWorkflowBindings(DATASET_ID, "PUBLISHED")).thenReturn(List.of(new GovRuleBinding()));
        when(assetStatusViewService.read(List.of(ref))).thenReturn(Map.of(ref, eligibleStatus()));

        Map<String, Object> projected = service.enrichExecution(execution(), null, "91");

        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) projected.get("qualityEvidence");
        assertThat(evidence).containsEntry("evidenceState", "STALE").containsEntry("trustedUsable", false);
    }

    @Test
    void executionPageProjectsEvidenceAndCachesSharedAssetLookups() {
        CatalogDataset dataset = dataset();
        GovQualityWorkflowRun workflow = workflow("ingestion:90", "PASSED");
        AssetRef ref = new AssetRef("DATASET", "source:unknown/schema:ods/table:cost_center");
        when(datasetRepository.findAllById(Set.of(DATASET_ID))).thenReturn(List.of(dataset));
        when(datasetReadGuard.readableDatasetIds(List.of(dataset), "FIN")).thenReturn(Set.of(DATASET_ID));
        when(workflowRepository.findAllById(Set.of(WORKFLOW_ID))).thenReturn(List.of(workflow));
        when(bindingRepository.countWorkflowBindingsForDatasets(Set.of(DATASET_ID), "PUBLISHED"))
            .thenReturn(List.<Object[]>of(new Object[] { DATASET_ID, 1L }));
        when(assetStatusViewService.read(List.of(ref))).thenReturn(Map.of(ref, eligibleStatus()));
        Map<String, Object> older = new java.util.LinkedHashMap<>(execution());
        older.put("id", 89);

        Map<String, Object> page = service.enrichExecutionPage(
            Map.of("content", List.of(execution(), older), "totalElements", 2),
            "FIN",
            "90"
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        assertThat(content).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> currentEvidence = (Map<String, Object>) content.get(0).get("qualityEvidence");
        @SuppressWarnings("unchecked")
        Map<String, Object> staleEvidence = (Map<String, Object>) content.get(1).get("qualityEvidence");
        assertThat(currentEvidence).containsEntry("evidenceState", "CURRENT");
        assertThat(staleEvidence).containsEntry("evidenceState", "STALE");
        verify(datasetRepository, times(1)).findAllById(Set.of(DATASET_ID));
        verify(bindingRepository, times(1)).countWorkflowBindingsForDatasets(Set.of(DATASET_ID), "PUBLISHED");
        verify(workflowRepository, times(1)).findAllById(Set.of(WORKFLOW_ID));
        verify(datasetRepository, never()).findById(DATASET_ID);
        verify(workflowRepository, never()).findById(WORKFLOW_ID);
    }

    @Test
    void designReferenceValidationRequiresSamePhysicalAssetAndPublishedRules() {
        CatalogDataset dataset = dataset();
        AssetRef ref = new AssetRef("DATASET", "source:unknown/schema:ods/table:cost_center");
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(dataset));
        when(datasetReadGuard.requireReadable(DATASET_ID, "FIN")).thenReturn(dataset);
        when(bindingRepository.findWorkflowBindings(DATASET_ID, "PUBLISHED")).thenReturn(List.of(new GovRuleBinding()));
        when(assetStatusViewService.read(List.of(ref))).thenReturn(Map.of(ref, eligibleStatus()));
        Map<String, Object> design = Map.of(
            "postIngestionQualityEnabled", true,
            "qualityPolicyRef", "dataset:" + DATASET_ID,
            "targetDatasetId", DATASET_ID.toString(),
            "destinationConfig", Map.of("table", List.of("ods.cost_center")),
            "tableMapping", List.of(Map.of("source", "source_cost_center", "target", "cost_center"))
        );

        IngestionFlowProjectionService.AssetProjection asset = service.validateDesignReferences(design, "FIN");

        assertThat(asset.resolutionState()).isEqualTo("RESOLVED");
        assertThat(asset.datasetId()).isEqualTo(DATASET_ID.toString());
        assertThat(asset.qualityBindingCount()).isEqualTo(1);
    }

    @Test
    void designReferenceValidationFailsClosedOnPhysicalMismatch() {
        CatalogDataset dataset = dataset();
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(dataset));
        when(datasetReadGuard.requireReadable(DATASET_ID, null)).thenReturn(dataset);
        Map<String, Object> design = Map.of(
            "postIngestionQualityEnabled", true,
            "qualityPolicyRef", "dataset:" + DATASET_ID,
            "targetDatasetId", DATASET_ID.toString(),
            "tableMapping", List.of(Map.of("source", "source_cost_center", "target", "another_asset"))
        );

        assertThatThrownBy(() -> service.validateDesignReferences(design, null))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("QUALITY_ASSET_MISMATCH");
    }

    @Test
    void designReferenceValidationAllowsNewManagedFileTargetBeforeCatalogObservation() {
        Map<String, Object> design = Map.of(
            "sourceType", "txtfilereader",
            "sourceConfig", Map.of(
                "_fileLanding", Map.of(
                    "landingMode", "create_new",
                    "targetTable", "ods_project_subject_domain_v2"
                )
            ),
            "destinationConfig", Map.of(
                "targetDataSourceId", "a0000000-0000-0000-0000-000000000001"
            )
        );

        IngestionFlowProjectionService.AssetProjection asset = service.validateDesignReferences(design, null);

        assertThat(asset.resolutionState()).isEqualTo("UNRESOLVED");
        assertThat(asset.datasetId()).isNull();
        assertThat(asset.qualityConfigured()).isFalse();
        verify(datasetRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void designReferenceValidationRejectsNewManagedFileTargetWhenPostIngestionQualityIsEnabled() {
        Map<String, Object> design = Map.of(
            "sourceType", "txtfilereader",
            "sourceConfig", Map.of(
                "_fileLanding", Map.of(
                    "landingMode", "create_new",
                    "targetTable", "ods_project_subject_domain_v2"
                )
            ),
            "postIngestionQualityEnabled", true
        );

        assertThatThrownBy(() -> service.validateDesignReferences(design, null))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("TARGET_ASSET_UNRESOLVED");
    }

    @Test
    void designReferenceValidationAllowsCompleteDatabaseLandingBeforeCatalogObservation() {
        Map<String, Object> design = new java.util.LinkedHashMap<>(Map.of(
            "sourceType", "mysqlreader",
            "sourceDataSourceId", "11111111-2222-3333-4444-555555555555",
            "destinationType", "postgresqlwriter",
            "syncMode", "full_refresh",
            "destinationConfig", Map.of("targetDataSourceId", "a0000000-0000-0000-0000-000000000001"),
            "tableMapping", List.of(
                Map.of("source", "prs.customer", "target", "ods_prs_customer"),
                Map.of("source", "prs.contract", "target", "ods_prs_contract")
            )
        ));
        assertThat(service.validateDesignReferences(design, null).resolutionState()).isEqualTo("UNRESOLVED");
        verify(datasetRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        design.put("postIngestionQualityEnabled", true);
        assertThatThrownBy(() -> service.validateDesignReferences(design, null)).hasMessageContaining("TARGET_ASSET_UNRESOLVED");
        design.put("postIngestionQualityEnabled", false);
        design.put("targetDatasetId", "invalid-existing-reference");
        assertThatThrownBy(() -> service.validateDesignReferences(design, null)).hasMessageContaining("TARGET_ASSET_UNRESOLVED");
    }

    @Test
    void designReferenceValidationStillRejectsUnresolvedNonFileTarget() {
        Map<String, Object> design = Map.of(
            "sourceType", "mysqlreader",
            "destinationConfig", Map.of(
                "targetDataSourceId", "a0000000-0000-0000-0000-000000000001"
            )
        );

        assertThatThrownBy(() -> service.validateDesignReferences(design, null))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("TARGET_ASSET_UNRESOLVED");
    }

    private Map<String, Object> execution() {
        return Map.of(
            "id", 90,
            "taskId", 13,
            "targetDatasetId", DATASET_ID.toString(),
            "qualityPolicyRef", "dataset:" + DATASET_ID,
            "qualityWorkflowId", WORKFLOW_ID.toString(),
            "qualityWorkflowStatus", "PASSED"
        );
    }

    private CatalogDataset dataset() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(DATASET_ID);
        dataset.setName("cost_center");
        dataset.setHiveDatabase("ods");
        dataset.setHiveTable("cost_center");
        dataset.setEnabled(true);
        return dataset;
    }

    private GovQualityWorkflowRun workflow(String triggerRef, String status) {
        GovQualityWorkflowRun workflow = new GovQualityWorkflowRun();
        workflow.setId(WORKFLOW_ID);
        workflow.setDatasetId(DATASET_ID);
        workflow.setTriggerRef(triggerRef);
        workflow.setStatus(status);
        return workflow;
    }

    private AssetDeliveryStatus eligibleStatus() {
        return new AssetDeliveryStatus(
            null,
            "ELIGIBLE",
            List.of(),
            null,
            List.of(),
            null,
            "PASSED"
        );
    }
}
