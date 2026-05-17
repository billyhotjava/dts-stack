package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogRowFilterRuleRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionAuditService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionDecision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
            maskingRuleRepository
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
    }
}
