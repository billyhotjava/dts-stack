package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit;
import com.yuzhi.dts.platform.repository.permission.AssetPermissionAuditRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.time.Instant;
import java.util.List;
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

        AssetPermissionAuditQueryResource resource = new AssetPermissionAuditQueryResource(auditRepository);

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

        AssetPermissionAuditQueryResource resource = new AssetPermissionAuditQueryResource(auditRepository);

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

        AssetPermissionAuditQueryResource resource = new AssetPermissionAuditQueryResource(auditRepository);

        var response = resource.deniedCsv("NO_GRANT", null, null, null);

        assertThat(response.getBody()).startsWith("actor,assetType,assetId,permission,operator,reasonCode,reasonDetail,deniedAt\n");
        assertThat(response.getBody()).contains("ptrdemo,DATASET,asset-1,READ,service:dts-metrics,NO_GRANT,\"No active grant covers this asset.\",2026-05-18T00:01:00Z");
    }

    @Test
    void requiresServiceInternalAuthority() {
        PreAuthorize preAuthorize = AssetPermissionAuditQueryResource.class.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains(AuthoritiesConstants.SERVICE_INTERNAL);
    }
}
