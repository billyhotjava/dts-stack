package com.yuzhi.dts.platform.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Action;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.DependencyRole;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.OrderedEntry;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Preview;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.PreviewCommand;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Strategy;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = ModelMaterializationPlanResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
        "dts.platform.modeling.default-tenant-id=server-tenant",
    }
)
@AutoConfigureMockMvc(addFilters = false)
class ModelMaterializationPlanResourceTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelReleaseCandidateApplicationService service;

    @MockBean
    private WarehousePlanActorProvider actorProvider;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @MockBean
    private com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRejectionAudit dbtImplementationDraftRejectionAudit;

    @MockBean
    private com.yuzhi.dts.platform.service.audit.AuditService auditService;

    @Test
    void previewUsesServerTenantActorAndReturnsServerOwnedOrder() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("xiezm", "INST"));
        when(service.previewMaterializationPlan(any(), any(), any(), any())).thenReturn(preview());

        mockMvc
            .perform(
                post("/api/modeling/plans/{planId}/materialization-plans/preview", PLAN_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "environment":"dev",
                          "requestedModelSpecIds":["20000000-0000-0000-0000-000000000001"],
                          "strategy":"WITH_MISSING_UPSTREAMS"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.planChecksum").value("a".repeat(64)))
            .andExpect(jsonPath("$.data.orderedEntries[0].modelSpecId").value(MODEL_ID.toString()))
            .andExpect(jsonPath("$.data.orderedEntries[0].action").value("BUILD"));

        ArgumentCaptor<PreviewCommand> command = ArgumentCaptor.forClass(PreviewCommand.class);
        verify(service).previewMaterializationPlan(
            org.mockito.ArgumentMatchers.eq("server-tenant"),
            org.mockito.ArgumentMatchers.eq("xiezm"),
            org.mockito.ArgumentMatchers.eq(PLAN_ID),
            command.capture()
        );
        org.assertj.core.api.Assertions.assertThat(command.getValue().requestedModelSpecIds()).containsExactly(MODEL_ID);
    }

    @Test
    void emptyRequestFailsAtTheRestBoundary() throws Exception {
        mockMvc
            .perform(post("/api/modeling/plans/{planId}/materialization-plans/preview", PLAN_ID))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MODEL_MATERIALIZATION_PLAN_REQUEST_INVALID"));
    }

    private static Preview preview() {
        return new Preview(
            PLAN_ID,
            "dev",
            Strategy.WITH_MISSING_UPSTREAMS,
            "a".repeat(64),
            true,
            List.of(MODEL_ID),
            List.of(
                new OrderedEntry(
                    MODEL_ID,
                    "项目汇总",
                    2,
                    "b".repeat(64),
                    3,
                    "c".repeat(64),
                    "d".repeat(64),
                    Layer.DWS,
                    DependencyRole.ROOT,
                    0,
                    Action.BUILD,
                    "REQUESTED_MODEL",
                    null,
                    null
                )
            ),
            List.of()
        );
    }
}
