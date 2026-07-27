package com.yuzhi.dts.platform.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.CompileView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ReleaseView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.LegacyModelLifecycleCandidateAdapter;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationCompatibilityAdapter;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ImplementationMigrationBatch;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ImplementationMigrationResult;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ImplementationValidationView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.util.UUID;
import java.util.List;
import java.util.Map;
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
    private LegacyModelLifecycleCandidateAdapter candidateCompatibility;

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
    void legacyPublishRouteDelegatesToCandidateCompatibilityOwner() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        when(
            candidateCompatibility.publish(
                eq("server-tenant"),
                eq("alice"),
                eq(MODEL_ID),
                any(),
                any(),
                any()
            )
        )
            .thenReturn(mock(ReleaseView.class));

        mockMvc
            .perform(
                post("/api/modeling/model-specs/{id}/lifecycle/publish", MODEL_ID)
                    .header("If-Match", ETAG)
                    .header("If-Match-Implementation", IMPLEMENTATION_ETAG)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"comment\":\"publish candidate\",\"idempotencyKey\":\"publish-7\"}")
            )
            .andExpect(status().isOk());

        verify(candidateCompatibility).publish(
            eq("server-tenant"),
            eq("alice"),
            eq(MODEL_ID),
            any(),
            any(),
            any()
        );
        verify(service, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void legacyPublishPreservesCandidateConflictResponse() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        when(candidateCompatibility.publish(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any(), any()))
            .thenThrow(
                new ModelReleaseCandidateException(
                    "MODEL_RELEASE_CANDIDATE_SCOPE_STALE",
                    "Candidate scope changed",
                    ModelReleaseCandidateException.Kind.CONFLICT
                )
            );

        mockMvc
            .perform(
                post("/api/modeling/model-specs/{id}/lifecycle/publish", MODEL_ID)
                    .header("If-Match", ETAG)
                    .header("If-Match-Implementation", IMPLEMENTATION_ETAG)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"comment\":\"publish candidate\",\"idempotencyKey\":\"publish-7\"}")
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MODEL_RELEASE_CANDIDATE_SCOPE_STALE"));
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
    void migrationDryRunAndApplyUseNarrowStrictCommandsAndServerActor() throws Exception {
        String previewChecksum = "b".repeat(64);
        var decision = new ModelImplementationCompatibilityAdapter.MigrationDecision(
            MODEL_ID,
            ModelImplementationCompatibilityAdapter.MigrationStatus.ELIGIBLE,
            "LEGACY_IMPLEMENTATION_PROJECTED",
            7,
            null,
            null,
            com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode.PHYSICAL_ASSET,
            Map.of("targetPhysicalName", "dwd_finance_project")
        );
        var batch = new ImplementationMigrationBatch(
            previewChecksum,
            1,
            1,
            0,
            0,
            0,
            0,
            List.of(new ImplementationMigrationResult(decision, false, null, null))
        );
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        when(service.previewImplementationMigrations("server-tenant", List.of(MODEL_ID))).thenReturn(batch);
        when(service.applyImplementationMigrations("server-tenant", "alice", List.of(MODEL_ID), previewChecksum))
            .thenReturn(batch);

        mockMvc.perform(
            post("/api/modeling/model-specs/implementation-migrations/dry-run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modelSpecIds\":[\"" + MODEL_ID + "\"]}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.previewChecksum").value(previewChecksum))
            .andExpect(jsonPath("$.data.eligible").value(1));

        mockMvc.perform(
            post("/api/modeling/model-specs/implementation-migrations/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"modelSpecIds\":[\"" + MODEL_ID + "\"],\"previewChecksum\":\"" + previewChecksum + "\"}"
                )
        ).andExpect(status().isOk());

        verify(service).previewImplementationMigrations("server-tenant", List.of(MODEL_ID));
        verify(service).applyImplementationMigrations("server-tenant", "alice", List.of(MODEL_ID), previewChecksum);
    }

    @Test
    void migrationCommandsRejectUnknownFields() throws Exception {
        mockMvc.perform(
            post("/api/modeling/model-specs/implementation-migrations/dry-run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modelSpecIds\":[],\"overwriteCurrent\":true}")
        ).andExpect(status().isBadRequest());

        verify(service, never()).previewImplementationMigrations(any(), any());
    }
}
