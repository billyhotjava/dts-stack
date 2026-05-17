package com.yuzhi.dts.platform.service.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit;
import com.yuzhi.dts.platform.repository.permission.AssetPermissionAuditRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetPermissionAuditServiceTest {

    @Mock
    private AssetPermissionAuditRepository auditRepository;

    @Test
    void recordDecisionPersistsDenialReasonAndGrantSource() {
        AssetPermissionAuditService service = new AssetPermissionAuditService(auditRepository);
        PermissionDecision decision = new PermissionDecision(
            false,
            "READ",
            "classification_denied",
            "READ",
            "PREVIEW",
            "DATASET",
            "asset-1",
            "source:demo/schema:dwd/table:orders",
            "DENIED",
            "explicit_grant"
        );

        service.recordDecision(decision, "ptrdemo", "service:dts-metrics");

        ArgumentCaptor<AssetPermissionAudit> captor = ArgumentCaptor.forClass(AssetPermissionAudit.class);
        verify(auditRepository).save(captor.capture());
        AssetPermissionAudit audit = captor.getValue();
        assertThat(audit.getAction()).isEqualTo("CHECK_DENY");
        assertThat(audit.getAssetType()).isEqualTo("DATASET");
        assertThat(audit.getAssetId()).isEqualTo("asset-1");
        assertThat(audit.getTargetUser()).isEqualTo("ptrdemo");
        assertThat(audit.getOperator()).isEqualTo("service:dts-metrics");
        assertThat(audit.getDetail()).contains("\"reason\":\"classification_denied\"");
        assertThat(audit.getDetail()).contains("\"grantSource\":\"explicit_grant\"");
    }
}
