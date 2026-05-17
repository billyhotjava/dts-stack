package com.yuzhi.dts.platform.service.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit;
import com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection;
import com.yuzhi.dts.platform.repository.permission.AssetPermissionAuditRepository;
import com.yuzhi.dts.platform.repository.permission.AssetPermissionPolicyInjectionRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionDecision;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetPermissionAuditServiceTest {

    @Mock
    private AssetPermissionAuditRepository auditRepository;

    @Mock
    private AssetPermissionPolicyInjectionRepository policyInjectionRepository;

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
        assertThat(audit.getReasonCode()).isEqualTo("CLASSIFICATION_MISMATCH");
        assertThat(audit.getReasonDetail()).contains("classification");
        assertThat(audit.getDetail()).contains("\"reason\":\"classification_denied\"");
        assertThat(audit.getDetail()).contains("\"reasonCode\":\"CLASSIFICATION_MISMATCH\"");
        assertThat(audit.getDetail()).contains("\"grantSource\":\"explicit_grant\"");
    }

    @Test
    void recordPolicyInjectionPersistsPredicatesAndStableHash() {
        AssetPermissionAuditService service = new AssetPermissionAuditService(auditRepository, policyInjectionRepository);

        service.recordPolicyInjection(
            new AssetPermissionAuditService.PolicyInjectionAuditEvent(
                "ptrdemo",
                "DATASET",
                "asset-1",
                "PREVIEW",
                List.of("dept_code = 'D01'"),
                List.of("customer_phone"),
                "platform-row-filter+masking",
                null,
                "PROVIDER",
                "flower-rental"
            )
        );

        ArgumentCaptor<AssetPermissionPolicyInjection> captor = ArgumentCaptor.forClass(AssetPermissionPolicyInjection.class);
        verify(policyInjectionRepository).save(captor.capture());
        AssetPermissionPolicyInjection audit = captor.getValue();
        assertThat(audit.getActor()).isEqualTo("ptrdemo");
        assertThat(audit.getAssetType()).isEqualTo("DATASET");
        assertThat(audit.getAssetId()).isEqualTo("asset-1");
        assertThat(audit.getAction()).isEqualTo("PREVIEW");
        assertThat(audit.getPredicates()).isEqualTo("[\"dept_code = 'D01'\"]");
        assertThat(audit.getMaskedColumns()).isEqualTo("[\"customer_phone\"]");
        assertThat(audit.getPolicySource()).isEqualTo("platform-row-filter+masking");
        assertThat(audit.getPredicateHash()).startsWith("sha256:");
        assertThat(audit.getDirection()).isEqualTo("PROVIDER");
        assertThat(audit.getPackId()).isEqualTo("flower-rental");
    }
}
