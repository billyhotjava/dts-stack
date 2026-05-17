package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit;
import com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection;
import com.yuzhi.dts.platform.repository.permission.AssetPermissionAuditRepository;
import com.yuzhi.dts.platform.repository.permission.AssetPermissionPolicyInjectionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.permission.AssetPermissionAuditService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class AssetPermissionAuditQueryResourceTest {

    @Mock
    private AssetPermissionAuditRepository auditRepository;

    @Mock
    private AssetPermissionPolicyInjectionRepository policyInjectionRepository;

    @Mock
    private AssetPermissionAuditService auditService;

    @Test
    void deniedReturnsStructuredReasonRows() {
        Instant since = Instant.parse("2026-05-18T00:00:00Z");
        AssetPermissionAudit audit = new AssetPermissionAudit();
        audit.setAction("CHECK_DENY");
        audit.setAssetType("DATASET");
        audit.setAssetId("asset-1");
        audit.setTargetUser("ptrdemo");
        audit.setPermission("READ");
        audit.setOperator("service:dts-metrics");
        audit.setReasonCode("CLASSIFICATION_MISMATCH");
        audit.setReasonDetail("User classification PUBLIC is lower than asset classification INTERNAL.");
        audit.setCreatedDate(since.plusSeconds(60));
        when(auditRepository.findDeniedAudits(eq("CLASSIFICATION_MISMATCH"), eq("asset-1"), eq(since), eq(PageRequest.of(0, 50))))
            .thenReturn(new PageImpl<>(List.of(audit)));

        AssetPermissionAuditQueryResource resource = resource();

        var response = resource.denied("CLASSIFICATION_MISMATCH", "asset-1", since, 50);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(1);
        AssetPermissionAuditQueryResource.DeniedAuditResponse row = response.getBody().get(0);
        assertThat(row.assetType()).isEqualTo("DATASET");
        assertThat(row.assetId()).isEqualTo("asset-1");
        assertThat(row.reasonCode()).isEqualTo("CLASSIFICATION_MISMATCH");
        assertThat(row.reasonDetail()).contains("PUBLIC");
        verify(auditRepository).findDeniedAudits("CLASSIFICATION_MISMATCH", "asset-1", since, PageRequest.of(0, 50));
    }

    @Test
    void deniedCapsLimitAtTwoHundred() {
        when(auditRepository.findDeniedAudits(eq(null), eq(null), eq(null), eq(PageRequest.of(0, 200))))
            .thenReturn(new PageImpl<>(List.of()));

        AssetPermissionAuditQueryResource resource = resource();

        resource.denied(null, null, null, 500);

        verify(auditRepository).findDeniedAudits(null, null, null, PageRequest.of(0, 200));
    }

    @Test
    void deniedCsvUsesStableComplianceSchema() {
        Instant createdAt = Instant.parse("2026-05-18T00:01:00Z");
        AssetPermissionAudit audit = new AssetPermissionAudit();
        audit.setAction("CHECK_DENY");
        audit.setAssetType("DATASET");
        audit.setAssetId("asset-1");
        audit.setTargetUser("ptrdemo");
        audit.setPermission("READ");
        audit.setOperator("service:dts-metrics");
        audit.setReasonCode("NO_GRANT");
        audit.setReasonDetail("No active grant covers this asset.");
        audit.setCreatedDate(createdAt);
        when(auditRepository.findDeniedAudits(eq("NO_GRANT"), eq(null), eq(null), eq(PageRequest.of(0, 100))))
            .thenReturn(new PageImpl<>(List.of(audit)));

        AssetPermissionAuditQueryResource resource = resource();

        var response = resource.deniedCsv("NO_GRANT", null, null, null);

        assertThat(response.getBody()).startsWith("actor,assetType,assetId,permission,operator,reasonCode,reasonDetail,deniedAt\n");
        assertThat(response.getBody()).contains("ptrdemo,DATASET,asset-1,READ,service:dts-metrics,NO_GRANT,\"No active grant covers this asset.\",2026-05-18T00:01:00Z");
    }

    @Test
    void policyInjectionReturnsProviderAndConsumerRows() {
        Instant occurredAt = Instant.parse("2026-05-18T00:02:00Z");
        AssetPermissionPolicyInjection row = new AssetPermissionPolicyInjection();
        row.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        row.setActor("ptrdemo");
        row.setAssetType("DATASET");
        row.setAssetId("asset-1");
        row.setAction("PREVIEW");
        row.setPredicates("[\"dept_code = 'D01'\"]");
        row.setMaskedColumns("[\"customer_phone\"]");
        row.setPolicySource("platform-row-filter+masking");
        row.setPredicateHash("sha256:abc123");
        row.setDirection("CONSUMER");
        row.setPackId("flower-rental");
        row.setOccurredAt(occurredAt);
        when(policyInjectionRepository.findByFilters(eq("asset-1"), eq("flower-rental"), eq(occurredAt.minusSeconds(60)), eq(PageRequest.of(0, 25))))
            .thenReturn(new PageImpl<>(List.of(row)));

        var response = resource().policyInjection("asset-1", "flower-rental", occurredAt.minusSeconds(60), 25);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(1);
        AssetPermissionAuditQueryResource.PolicyInjectionAuditResponse body = response.getBody().get(0);
        assertThat(body.assetId()).isEqualTo("asset-1");
        assertThat(body.predicates()).isEqualTo("[\"dept_code = 'D01'\"]");
        assertThat(body.maskedColumns()).isEqualTo("[\"customer_phone\"]");
        assertThat(body.predicateHash()).isEqualTo("sha256:abc123");
        assertThat(body.direction()).isEqualTo("CONSUMER");
        assertThat(body.packId()).isEqualTo("flower-rental");
    }

    @Test
    void recordPolicyInjectionDelegatesToAuditService() {
        AssetPermissionAuditQueryResource.RecordPolicyInjectionRequest request =
            new AssetPermissionAuditQueryResource.RecordPolicyInjectionRequest(
                "ptrdemo",
                "DATASET",
                "asset-1",
                "PREVIEW",
                List.of("dept_code = 'D01'"),
                List.of("customer_phone"),
                "platform-row-filter+masking",
                "sha256:abc123",
                "CONSUMER",
                "flower-rental"
            );

        var response = resource().recordPolicyInjection(request);

        assertThat(response.getBody()).containsEntry("recorded", true);
        verify(auditService).recordPolicyInjection(
            new AssetPermissionAuditService.PolicyInjectionAuditEvent(
                "ptrdemo",
                "DATASET",
                "asset-1",
                "PREVIEW",
                List.of("dept_code = 'D01'"),
                List.of("customer_phone"),
                "platform-row-filter+masking",
                "sha256:abc123",
                "CONSUMER",
                "flower-rental"
            )
        );
    }

    @Test
    void requiresServiceInternalAuthority() {
        PreAuthorize preAuthorize = AssetPermissionAuditQueryResource.class.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains(AuthoritiesConstants.SERVICE_INTERNAL);
    }

    private AssetPermissionAuditQueryResource resource() {
        return new AssetPermissionAuditQueryResource(auditRepository, policyInjectionRepository, auditService);
    }
}
