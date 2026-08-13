package com.yuzhi.dts.platform.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.CompileView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationOwnershipTransitionService;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRejectionAudit;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ImplementationValidationView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = ModelLifecycleResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
        "dts.platform.modeling.default-tenant-id=server-tenant",
    }
)
@AutoConfigureMockMvc(addFilters = false)
class ModelLifecycleResourceTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String CHECKSUM = "a".repeat(64);
    private static final String ETAG = "\"model-spec:" + MODEL_ID + ":7:" + CHECKSUM + "\"";
    private static final String IMPLEMENTATION_ETAG = "\"model-implementation:" + MODEL_ID + ":1:" + CHECKSUM + "\"";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelLifecycleService service;

    @MockBean
    private ModelImplementationOwnershipTransitionService ownershipTransitions;

    @MockBean
    private DbtImplementationDraftRejectionAudit dbtImplementationDraftRejectionAudit;

    @MockBean
    private AuditService auditService;

    @MockBean
    private WarehousePlanActorProvider actorProvider;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @Test
    void compileUsesServerTenantActorAndStrongRevisionPrecondition() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        when(service.compile(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any(), eq("compile-7")))
            .thenReturn(mock(CompileView.class));

        mockMvc.perform(
            post("/api/modeling/model-specs/{id}/lifecycle/compile", MODEL_ID)
                .header("X-Tenant-Id", "request-tenant-must-not-win")
                .header("If-Match", ETAG)
                .header("If-Match-Implementation", IMPLEMENTATION_ETAG)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"compile-7\"}")
        ).andExpect(status().isOk());

        verify(service).compile(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any(), eq("compile-7"));
    }

    @Test
    void legacyReleaseCompatibilityRoutesAreRemoved() throws Exception {
        UUID releaseId = UUID.randomUUID();
        List<String> routes = List.of(
            "/api/modeling/model-specs/" + MODEL_ID + "/lifecycle/reviews",
            "/api/modeling/model-specs/" + MODEL_ID + "/lifecycle/reviews/approve",
            "/api/modeling/model-specs/" + MODEL_ID + "/lifecycle/publish",
            "/api/modeling/model-specs/" + MODEL_ID + "/lifecycle/releases/" + releaseId + "/retry",
            "/api/modeling/model-specs/" + MODEL_ID + "/lifecycle/rollback"
        );

        for (String route : routes) {
            mockMvc.perform(
                post(route)
                    .header("If-Match", ETAG)
                    .header("If-Match-Implementation", IMPLEMENTATION_ETAG)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            ).andExpect(status().isNotFound());
        }
    }

    @Test
    void mutationWithoutStrongEtagIsRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(
            post("/api/modeling/model-specs/{id}/lifecycle/compile", MODEL_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"compile-7\"}")
        )
            .andExpect(status().isPreconditionRequired())
            .andExpect(jsonPath("$.code").value("MODEL_SPEC_IF_MATCH_REQUIRED"));
    }

    @Test
    void ownershipTransitionRejectsClientSuppliedDbtIdentity() throws Exception {
        mockMvc.perform(
            post("/api/modeling/model-specs/{id}/implementation/ownership-transitions", MODEL_ID)
                .header("If-Match", ETAG)
                .header("If-Match-Implementation", IMPLEMENTATION_ETAG)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetOwnership\":\"DBT_MANAGED\",\"previewChecksum\":\"" + CHECKSUM + "\",\"idempotencyKey\":\"transition-7\",\"projectKey\":\"client\"}")
        ).andExpect(status().isBadRequest());

        verify(ownershipTransitions, never()).transition(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void validateDecodesOnlyTheDeclaredInputKind() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        when(service.validateImplementation(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any()))
            .thenReturn(new ImplementationValidationView(true, null));

        mockMvc.perform(
            post("/api/modeling/model-specs/{id}/implementation/inputs/validate", MODEL_ID)
                .header("If-Match", ETAG)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"projectKey":"warehouse","dbtUniqueId":"model.warehouse.customer","inputMode":"PHYSICAL_ASSET",
                     "inputs":[{"sourceBindingId":"10000000-0000-0000-0000-000000000010","resolvedVersion":"v1"}],
                     "ownership":"DESIGNER_GENERATED","materialization":"table","idempotencyKey":"validate-1"}
                    """
                )
        ).andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(true));

        verify(service).validateImplementation(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any());
    }

    @Test
    void rejectsMissingImplementationInputBeforeServiceCall() throws Exception {
        mockMvc.perform(
            post("/api/modeling/model-specs/{id}/implementation/inputs/validate", MODEL_ID)
                .header("If-Match", ETAG)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inputMode\":\"PHYSICAL_ASSET\",\"inputs\":[]}")
        )
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("MODEL_IMPLEMENTATION_INPUT_REQUIRED"));

        verify(service, never()).validateImplementation(any(), any(), any(), any(), any());
    }

    @Test
    void implementationMigrationCompatibilityRoutesAreRemoved() throws Exception {
        for (String action : List.of("dry-run", "apply", "rollback")) {
            mockMvc.perform(
                post("/api/modeling/model-specs/implementation-migrations/" + action)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            ).andExpect(status().isNotFound());
        }
    }
}
