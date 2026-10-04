package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AssetActionPolicyService;
import com.yuzhi.dts.platform.service.security.AssetActionPolicyService.ActionCell;
import com.yuzhi.dts.platform.service.security.AssetActionPolicyService.DecisionCommand;
import com.yuzhi.dts.platform.service.security.AssetActionPolicyService.MatrixView;
import com.yuzhi.dts.platform.service.security.AssetActionPolicyService.PolicyChangeRequestCommand;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssetActionPolicyResourceTest {

    private AssetActionPolicyService service;
    private AuditService audit;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AssetActionPolicyService.class);
        audit = mock(AuditService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AssetActionPolicyResource(service, audit)).build();
    }

    @Test
    void returnsAllEightActionsAndSubmitsPendingRequestWithoutDirectPolicyWrite() throws Exception {
        List<ActionCell> actions = Arrays
            .stream(com.yuzhi.dts.platform.security.policy.AssetAction.values())
            .map(action -> new ActionCell(action.code(), action.displayName(), "NONE", null, null, "UNCONFIGURED"))
            .toList();
        when(service.matrix("ROLE", "ROLE_RELEASE_OPERATOR", "DATASET", "dataset-1"))
            .thenReturn(new MatrixView("ROLE", "ROLE_RELEASE_OPERATOR", "DATASET", "dataset-1", actions, null));

        IamAssetActionPolicyRequest pending = pendingRequest();
        when(service.requestChange(any(PolicyChangeRequestCommand.class))).thenReturn(pending);

        mockMvc.perform(
            get("/api/iam/action-policies/matrix")
                .queryParam("subjectType", "ROLE")
                .queryParam("subjectId", "ROLE_RELEASE_OPERATOR")
                .queryParam("resourceType", "DATASET")
                .queryParam("resourceId", "dataset-1")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.actions.length()").value(8))
            .andExpect(jsonPath("$.data.actions[0].action").value("CREATE"))
            .andExpect(jsonPath("$.data.actions[7].action").value("DESTROY"));

        mockMvc.perform(
            post("/api/iam/action-policies/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "subjectType": "ROLE",
                      "subjectId": "ROLE_RELEASE_OPERATOR",
                      "resourceType": "DATASET",
                      "resourceId": "dataset-1",
                      "desiredEffects": {"CREATE": "ALLOW"},
                      "reason": "发布职责调整"
                    }
                    """
                )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(service).requestChange(any(PolicyChangeRequestCommand.class));
        verify(audit).auditAction(
            eq("IAM_ASSET_ACTION_POLICY_REQUEST"),
            eq(AuditStage.SUCCESS),
            eq(pending.getId().toString()),
            any()
        );
    }

    @Test
    void decisionUsesDedicatedEndpointAndPrivilegedAuthority() throws Exception {
        UUID requestId = UUID.randomUUID();
        IamAssetActionPolicyRequest approved = pendingRequest();
        approved.setId(requestId);
        approved.setStatus("APPROVED");
        approved.setDecidedBy("reviewer");
        when(service.decide(eq(requestId), any(DecisionCommand.class))).thenReturn(approved);

        mockMvc.perform(
            post("/api/iam/action-policies/requests/{id}/decision", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\",\"notes\":\"复核通过\"}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));

        Method requestMethod = AssetActionPolicyResource.class.getDeclaredMethod(
            "requestChange",
            PolicyChangeRequestCommand.class
        );
        Method decisionMethod = AssetActionPolicyResource.class.getDeclaredMethod(
            "decide",
            UUID.class,
            DecisionCommand.class
        );
        assertThat(requestMethod.getAnnotation(PreAuthorize.class).value()).contains("INSTITUTE_PRIVILEGED_ROLES");
        assertThat(decisionMethod.getAnnotation(PreAuthorize.class).value()).contains("INSTITUTE_PRIVILEGED_ROLES");
        verify(audit).auditAction(
            eq("IAM_ASSET_ACTION_POLICY_APPROVE"),
            eq(AuditStage.SUCCESS),
            eq(requestId.toString()),
            any()
        );
    }

    private static IamAssetActionPolicyRequest pendingRequest() {
        IamAssetActionPolicyRequest request = new IamAssetActionPolicyRequest();
        request.setId(UUID.randomUUID());
        request.setStatus("PENDING");
        request.setRequestedBy("alice");
        request.setSubjectType("ROLE");
        request.setSubjectId("ROLE_RELEASE_OPERATOR");
        request.setResourceType("DATASET");
        request.setResourceId("dataset-1");
        return request;
    }
}
