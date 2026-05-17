package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogRowFilterRuleRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionAuditService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionDecision;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AssetPermissionInternalResourceTest {

    @Mock
    private AssetPermissionService permissionService;

    @Mock
    private AssetPermissionAuditService auditService;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogRowFilterRuleRepository rowFilterRuleRepository;

    @Mock
    private CatalogMaskingRuleRepository maskingRuleRepository;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void policyReturnsMaskingColumnsForDataset() {
        UUID datasetId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        CatalogMaskingRule rule = new CatalogMaskingRule();
        rule.setDataset(dataset);
        rule.setColumn("customer_name");

        when(permissionService.checkAction(any()))
            .thenReturn(
                new PermissionDecision(
                    true,
                    "READ",
                    "explicit_grant",
                    "READ",
                    "PREVIEW",
                    "DATASET",
                    datasetId.toString(),
                    null,
                    "ALLOWED",
                    "explicit_grant"
                )
            );
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(rowFilterRuleRepository.findByDataset(dataset)).thenReturn(List.of());
        when(maskingRuleRepository.findByDataset(dataset)).thenReturn(List.of(rule));

        AssetPermissionInternalResource resource = new AssetPermissionInternalResource(
            permissionService,
            auditService,
            datasetRepository,
            rowFilterRuleRepository,
            maskingRuleRepository,
            meterRegistry
        );

        AssetPermissionInternalResource.PolicyResponse response = resource
            .policy(
                new AssetPermissionInternalResource.CheckRequest(
                    "ptrdemo",
                    List.of("ROLE_PTR"),
                    "D01",
                    "INTERNAL",
                    "INTERNAL",
                    "PREVIEW",
                    new AssetPermissionInternalResource.AssetRefDto("DATASET", datasetId.toString(), null)
                )
            )
            .getBody();

        assertThat(response).isNotNull();
        assertThat(response.maskedColumns()).containsExactly("customer_name");
        assertThat(response.policySource()).isEqualTo("platform-masking");

        ArgumentCaptor<AssetPermissionAuditService.PolicyInjectionAuditEvent> captor =
            ArgumentCaptor.forClass(AssetPermissionAuditService.PolicyInjectionAuditEvent.class);
        verify(auditService).recordPolicyInjection(captor.capture());
        AssetPermissionAuditService.PolicyInjectionAuditEvent audit = captor.getValue();
        assertThat(audit.actor()).isEqualTo("ptrdemo");
        assertThat(audit.assetType()).isEqualTo("DATASET");
        assertThat(audit.assetId()).isEqualTo(datasetId.toString());
        assertThat(audit.action()).isEqualTo("PREVIEW");
        assertThat(audit.maskedColumns()).containsExactly("customer_name");
        assertThat(audit.policySource()).isEqualTo("platform-masking");
        assertThat(audit.direction()).isEqualTo("PROVIDER");
    }

    @Test
    void policyV1ReturnsForbiddenWhenDenied() {
        when(permissionService.checkAction(any()))
            .thenReturn(
                new PermissionDecision(
                    false,
                    null,
                    "missing_grant",
                    "READ",
                    "PREVIEW",
                    "DATASET",
                    "dataset-001",
                    "dataset:key",
                    "DENIED",
                    "none"
                )
            );

        AssetPermissionInternalResource resource = new AssetPermissionInternalResource(
            permissionService,
            auditService,
            datasetRepository,
            rowFilterRuleRepository,
            maskingRuleRepository,
            meterRegistry
        );

        ResponseEntity<AssetPermissionInternalResource.PolicyResponse> response = resource.policyV1(
            new AssetPermissionInternalResource.CheckRequest(
                "ptrdemo",
                List.of("ROLE_PTR"),
                "D01",
                "INTERNAL",
                "INTERNAL",
                "PREVIEW",
                new AssetPermissionInternalResource.AssetRefDto("DATASET", "dataset-001", "dataset:key")
            )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().applyRls()).isFalse();
        assertThat(response.getBody().policySource()).isEqualTo("missing_grant");
        verify(datasetRepository, never()).findAll();
    }

    @Test
    void checkReturnsStructuredDenialReason() {
        when(permissionService.checkAction(any()))
            .thenReturn(
                PermissionDecision.denied(
                    "DATASET",
                    "dataset-001",
                    "dataset:key",
                    "PREVIEW",
                    null,
                    "READ",
                    "classification_denied",
                    "DENIED",
                    "explicit_grant"
                )
            );

        AssetPermissionInternalResource resource = new AssetPermissionInternalResource(
            permissionService,
            auditService,
            datasetRepository,
            rowFilterRuleRepository,
            maskingRuleRepository,
            meterRegistry
        );

        AssetPermissionInternalResource.CheckResponse response = resource
            .check(
                new AssetPermissionInternalResource.CheckRequest(
                    "ptrdemo",
                    List.of("ROLE_PTR"),
                    "D01",
                    "PUBLIC",
                    "INTERNAL",
                    "PREVIEW",
                    new AssetPermissionInternalResource.AssetRefDto("DATASET", "dataset-001", "dataset:key")
                )
            )
            .getBody();

        assertThat(response).isNotNull();
        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("classification_denied");
        assertThat(response.reasonCode()).isEqualTo("CLASSIFICATION_MISMATCH");
        assertThat(response.reasonDetail()).contains("classification");
        assertThat(response.suggestedRemediation()).contains("classification");
        assertThat(response.deniedAt()).isNotNull();
    }

    @Test
    void policyResolvesDatasetByIndexedTableLookupWithoutFullScan() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        dataset.setHiveTable("contract_detail");
        when(permissionService.checkAction(any()))
            .thenReturn(
                new PermissionDecision(
                    true,
                    "READ",
                    "explicit_grant",
                    "READ",
                    "PREVIEW",
                    "DATASET",
                    null,
                    "source:ptr/schema:dwd/table:contract_detail",
                    "ALLOWED",
                    "explicit_grant"
                )
            );
        when(datasetRepository.findFirstByHiveTableIgnoreCase("contract_detail")).thenReturn(Optional.of(dataset));
        when(rowFilterRuleRepository.findByDataset(dataset)).thenReturn(List.of());
        when(maskingRuleRepository.findByDataset(dataset)).thenReturn(List.of());

        AssetPermissionInternalResource resource = new AssetPermissionInternalResource(
            permissionService,
            auditService,
            datasetRepository,
            rowFilterRuleRepository,
            maskingRuleRepository,
            meterRegistry
        );

        AssetPermissionInternalResource.PolicyResponse response = resource
            .policy(
                new AssetPermissionInternalResource.CheckRequest(
                    "ptrdemo",
                    List.of("ROLE_PTR"),
                    "D01",
                    "INTERNAL",
                    "INTERNAL",
                    "PREVIEW",
                    new AssetPermissionInternalResource.AssetRefDto("DATASET", null, "source:ptr/schema:dwd/table:contract_detail")
                )
            )
            .getBody();

        assertThat(response).isNotNull();
        assertThat(response.policySource()).isEqualTo("platform-permission");
        verify(datasetRepository, never()).findAll();
    }

    @Test
    void legacyPolicyRecordsDatasetMissCounterWhenPolicyAssetCannotResolve() {
        when(permissionService.checkAction(any()))
            .thenReturn(
                new PermissionDecision(
                    true,
                    "READ",
                    "explicit_grant",
                    "READ",
                    "PREVIEW",
                    "DATASET",
                    null,
                    "source:ptr/schema:dwd/table:missing_contract_detail",
                    "ALLOWED",
                    "explicit_grant"
                )
            );
        when(datasetRepository.findFirstByHiveTableIgnoreCase("missing_contract_detail")).thenReturn(Optional.empty());
        when(datasetRepository.findFirstByNameIgnoreCase("missing_contract_detail")).thenReturn(Optional.empty());

        AssetPermissionInternalResource resource = new AssetPermissionInternalResource(
            permissionService,
            auditService,
            datasetRepository,
            rowFilterRuleRepository,
            maskingRuleRepository,
            meterRegistry
        );

        AssetPermissionInternalResource.PolicyResponse response = resource
            .policy(
                new AssetPermissionInternalResource.CheckRequest(
                    "ptrdemo",
                    List.of("ROLE_PTR"),
                    "D01",
                    "INTERNAL",
                    "INTERNAL",
                    "PREVIEW",
                    new AssetPermissionInternalResource.AssetRefDto(
                        "DATASET",
                        null,
                        "source:ptr/schema:dwd/table:missing_contract_detail"
                    )
                )
            )
            .getBody();

        assertThat(response).isNotNull();
        assertThat(response.policySource()).isEqualTo("platform-permission");
        assertThat(meterRegistry.counter("dts.platform.asset_permission.policy.dataset_miss").count()).isEqualTo(1);
    }

    @Test
    void policyV1ReturnsUnprocessableEntityWhenDatasetPolicyAssetCannotResolve() {
        when(permissionService.checkAction(any()))
            .thenReturn(
                new PermissionDecision(
                    true,
                    "READ",
                    "explicit_grant",
                    "READ",
                    "PREVIEW",
                    "DATASET",
                    null,
                    "source:ptr/schema:dwd/table:missing_contract_detail",
                    "ALLOWED",
                    "explicit_grant"
                )
            );
        when(datasetRepository.findFirstByHiveTableIgnoreCase("missing_contract_detail")).thenReturn(Optional.empty());
        when(datasetRepository.findFirstByNameIgnoreCase("missing_contract_detail")).thenReturn(Optional.empty());

        AssetPermissionInternalResource resource = new AssetPermissionInternalResource(
            permissionService,
            auditService,
            datasetRepository,
            rowFilterRuleRepository,
            maskingRuleRepository,
            meterRegistry
        );

        ResponseEntity<AssetPermissionInternalResource.PolicyResponse> response = resource.policyV1(
            new AssetPermissionInternalResource.CheckRequest(
                "ptrdemo",
                List.of("ROLE_PTR"),
                "D01",
                "INTERNAL",
                "INTERNAL",
                "PREVIEW",
                new AssetPermissionInternalResource.AssetRefDto(
                    "DATASET",
                    null,
                    "source:ptr/schema:dwd/table:missing_contract_detail"
                )
            )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().applyRls()).isFalse();
        assertThat(response.getBody().policySource()).isEqualTo("dataset_not_resolved");
        assertThat(meterRegistry.counter("dts.platform.asset_permission.policy.dataset_miss").count()).isEqualTo(1);
        verify(rowFilterRuleRepository, never()).findByDataset(any());
        verify(maskingRuleRepository, never()).findByDataset(any());
    }
}
