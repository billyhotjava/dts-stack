package com.yuzhi.dts.platform.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy;
import com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest;
import com.yuzhi.dts.platform.repository.iam.IamAssetActionPolicyRepository;
import com.yuzhi.dts.platform.repository.iam.IamAssetActionPolicyRequestRepository;
import com.yuzhi.dts.platform.service.security.AssetActionPolicyService.DecisionCommand;
import com.yuzhi.dts.platform.service.security.AssetActionPolicyService.PolicyChangeRequestCommand;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AssetActionPolicyServiceTest {

    @Mock
    private IamAssetActionPolicyRepository policyRepository;

    @Mock
    private IamAssetActionPolicyRequestRepository requestRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submissionCreatesPendingRequestWithoutChangingEffectivePolicies() {
        authenticate("alice");
        AssetActionPolicyService service = service();
        when(requestRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IamAssetActionPolicyRequest saved = service.requestChange(
            new PolicyChangeRequestCommand(
                "ROLE",
                "ROLE_RELEASE_OPERATOR",
                "发布操作员",
                "DATASET",
                "dataset-1",
                "财务模型",
                Map.of("CREATE", "ALLOW", "UPDATE", "DENY"),
                Instant.parse("2026-07-27T00:00:00Z"),
                null,
                "上线职责授权"
            )
        );

        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getRequestedBy()).isEqualTo("alice");
        assertThat(saved.getChangesJson()).contains("\"CREATE\":\"ALLOW\"").contains("\"UPDATE\":\"DENY\"");
        verify(policyRepository, never()).save(any(IamAssetActionPolicy.class));
    }

    @Test
    void requesterCannotApproveOwnPolicyChange() {
        authenticate("alice");
        AssetActionPolicyService service = service();
        UUID requestId = UUID.randomUUID();
        when(requestRepository.findForUpdate(requestId)).thenReturn(Optional.of(pendingRequest("alice")));

        assertThatThrownBy(() -> service.decide(requestId, new DecisionCommand("APPROVE", "self approval")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("separation_of_duties");
        verify(policyRepository, never()).save(any(IamAssetActionPolicy.class));
    }

    @Test
    void approvalAtomicallyAppliesAllowAndRemovalChanges() {
        authenticate("reviewer");
        AssetActionPolicyService service = service();
        UUID requestId = UUID.randomUUID();
        IamAssetActionPolicyRequest request = pendingRequest("alice");
        request.setChangesJson("{\"CREATE\":\"ALLOW\",\"EXPORT\":\"NONE\"}");
        when(requestRepository.findForUpdate(requestId)).thenReturn(Optional.of(request));
        IamAssetActionPolicy export = new IamAssetActionPolicy();
        when(
            policyRepository.findBySubjectTypeIgnoreCaseAndSubjectIdAndResourceTypeIgnoreCaseAndResourceIdAndActionIgnoreCase(
                "ROLE",
                "ROLE_RELEASE_OPERATOR",
                "DATASET",
                "dataset-1",
                "CREATE"
            )
        ).thenReturn(Optional.empty());
        when(
            policyRepository.findBySubjectTypeIgnoreCaseAndSubjectIdAndResourceTypeIgnoreCaseAndResourceIdAndActionIgnoreCase(
                "ROLE",
                "ROLE_RELEASE_OPERATOR",
                "DATASET",
                "dataset-1",
                "EXPORT"
            )
        ).thenReturn(Optional.of(export));

        IamAssetActionPolicyRequest decided = service.decide(
            requestId,
            new DecisionCommand("APPROVE", "approved")
        );

        ArgumentCaptor<IamAssetActionPolicy> created = ArgumentCaptor.forClass(IamAssetActionPolicy.class);
        verify(policyRepository).save(created.capture());
        assertThat(created.getValue().getAction()).isEqualTo("CREATE");
        assertThat(created.getValue().getEffect()).isEqualTo("ALLOW");
        verify(policyRepository).delete(export);
        assertThat(decided.getStatus()).isEqualTo("APPROVED");
        assertThat(decided.getDecidedBy()).isEqualTo("reviewer");
    }

    @Test
    void matrixRetainsConfiguredEffectAndDistinguishesScheduledFromActivePolicy() {
        AssetActionPolicyService service = service();
        IamAssetActionPolicy scheduled = new IamAssetActionPolicy();
        scheduled.setAction("EXPORT");
        scheduled.setEffect("ALLOW");
        scheduled.setValidFrom(Instant.now().plusSeconds(3600));
        when(
            policyRepository.findBySubjectTypeIgnoreCaseAndSubjectIdAndResourceTypeIgnoreCaseAndResourceIdOrderByActionAsc(
                "ROLE",
                "ROLE_RELEASE_OPERATOR",
                "DATASET",
                "dataset-1"
            )
        ).thenReturn(List.of(scheduled));

        var matrix = service.matrix("ROLE", "ROLE_RELEASE_OPERATOR", "DATASET", "dataset-1");

        var export = matrix.actions().stream().filter(cell -> "EXPORT".equals(cell.action())).findFirst().orElseThrow();
        assertThat(export.effect()).isEqualTo("ALLOW");
        assertThat(export.policyStatus()).isEqualTo("SCHEDULED");
        assertThat(matrix.actions()).filteredOn(cell -> "UNCONFIGURED".equals(cell.policyStatus())).hasSize(7);
    }

    private AssetActionPolicyService service() {
        return new AssetActionPolicyService(policyRepository, requestRepository, objectMapper);
    }

    private static IamAssetActionPolicyRequest pendingRequest(String requester) {
        IamAssetActionPolicyRequest request = new IamAssetActionPolicyRequest();
        request.setSubjectType("ROLE");
        request.setSubjectId("ROLE_RELEASE_OPERATOR");
        request.setSubjectName("发布操作员");
        request.setResourceType("DATASET");
        request.setResourceId("dataset-1");
        request.setResourceName("财务模型");
        request.setChangesJson("{\"CREATE\":\"ALLOW\"}");
        request.setStatus("PENDING");
        request.setRequestedBy(requester);
        request.setValidFrom(Instant.parse("2026-07-27T00:00:00Z"));
        return request;
    }

    private static void authenticate(String username) {
        SecurityContextHolder
            .getContext()
            .setAuthentication(new TestingAuthenticationToken(username, "n/a", "ROLE_INST_DATA_OWNER"));
    }
}
