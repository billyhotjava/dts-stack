package com.yuzhi.dts.platform.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryEvidenceType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.BlockerView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceSummaryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkbenchState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkbenchView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkspaceAction;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    value = ModelReleaseCandidateResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
        "dts.platform.modeling.default-tenant-id=server-tenant",
    }
)
@AutoConfigureMockMvc(addFilters = false)
class ModelReleaseCandidateResourceTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String ETAG = "\"release-candidate:" + CANDIDATE_ID + ":4\"";

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

    @Test
    void workspaceUsesServerTenantAndReturnsOneAggregateWithStrongEtag() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        when(service.workspace("server-tenant", "alice", PLAN_ID)).thenReturn(workbench());

        mockMvc
            .perform(
                get("/api/modeling/plans/{planId}/release-candidates/workspace", PLAN_ID)
                    .header("X-Tenant-Id", "request-tenant-must-not-win")
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG))
            .andExpect(jsonPath("$.data.state").value("READY"))
            .andExpect(jsonPath("$.data.candidate.id").value(CANDIDATE_ID.toString()))
            .andExpect(jsonPath("$.data.evidence.length()").value(7))
            .andExpect(jsonPath("$.data.allowedActions[0]").value("UPDATE_SCOPE"))
            .andExpect(jsonPath("$.data.allowedActions[1]").value("START_BUILD"));

        verify(service).workspace("server-tenant", "alice", PLAN_ID);
    }

    @Test
    void candidateLocationIsResolvableWithinTheSamePlanBoundary() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        when(service.get("server-tenant", "alice", PLAN_ID, CANDIDATE_ID)).thenReturn(candidate());

        mockMvc
            .perform(
                get("/api/modeling/plans/{planId}/release-candidates/{candidateId}", PLAN_ID, CANDIDATE_ID)
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG))
            .andExpect(jsonPath("$.data.id").value(CANDIDATE_ID.toString()));
    }

    @Test
    void createUsesHeaderIdempotencyAndReturnsLocationAndEtag() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        when(service.create(eq("server-tenant"), eq("alice"), eq(PLAN_ID), any()))
            .thenReturn(new CommandResult(candidate(), false, List.of()));

        mockMvc
            .perform(
                post("/api/modeling/plans/{planId}/release-candidates", PLAN_ID)
                    .header("Idempotency-Key", "create-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"environment":"prod","entries":[],"reason":"prepare first release"}
                        """
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/modeling/plans/" + PLAN_ID + "/release-candidates/" + CANDIDATE_ID))
            .andExpect(header().string("ETag", ETAG))
            .andExpect(jsonPath("$.data.candidate.id").value(CANDIDATE_ID.toString()))
            .andExpect(jsonPath("$.data.allowedActions.length()").value(0));
    }

    @Test
    void lockRequiresStrongEtagBeforeCallingTheService() throws Exception {
        mockMvc
            .perform(
                post("/api/modeling/plans/{planId}/release-candidates/{candidateId}/lock", PLAN_ID, CANDIDATE_ID)
                    .header("Idempotency-Key", "lock-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"scope confirmed\"}")
            )
            .andExpect(status().isPreconditionRequired())
            .andExpect(jsonPath("$.code").value("MODEL_RELEASE_CANDIDATE_IF_MATCH_REQUIRED"));

        verify(service, never()).lock(any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void driftRefreshPassesStrongEtagAndIdempotencyToTheServerOwnedCommand() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        CandidateView stale = new CandidateView(
            candidate().id(),
            candidate().tenantId(),
            candidate().planId(),
            candidate().environment(),
            DeliveryStatus.STALE,
            5,
            candidate().idempotencyKey(),
            candidate().requestHash(),
            candidate().audit(),
            "alice",
            candidate().lastModifiedAt().plusSeconds(1),
            List.of()
        );
        when(service.refreshDrift(
            "server-tenant",
            "alice",
            PLAN_ID,
            CANDIDATE_ID,
            4,
            "refresh-1",
            "confirm canonical drift"
        ))
            .thenReturn(new CommandResult(stale, false, List.of()));

        mockMvc
            .perform(
                post(
                    "/api/modeling/plans/{planId}/release-candidates/{candidateId}/refresh",
                    PLAN_ID,
                    CANDIDATE_ID
                )
                    .header("If-Match", ETAG)
                    .header("Idempotency-Key", "refresh-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"confirm canonical drift\"}")
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"release-candidate:" + CANDIDATE_ID + ":5\""))
            .andExpect(jsonPath("$.data.candidate.status").value("STALE"));
    }

    @Test
    void scopeUpdatePassesStrongEtagAndReturnsTheNextCandidateVersion() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        CandidateView updated = candidateAtVersion(5);
        when(service.replaceScope(eq("server-tenant"), eq("alice"), eq(PLAN_ID), eq(CANDIDATE_ID), any()))
            .thenReturn(new CommandResult(updated, false, List.of()));

        mockMvc
            .perform(
                put(
                    "/api/modeling/plans/{planId}/release-candidates/{candidateId}/scope",
                    PLAN_ID,
                    CANDIDATE_ID
                )
                    .header("If-Match", ETAG)
                    .header("Idempotency-Key", "scope-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"entries\":[],\"reason\":\"adjust release scope\"}")
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"release-candidate:" + CANDIDATE_ID + ":5\""))
            .andExpect(jsonPath("$.data.replayed").value(false));
    }

    @Test
    void retryReturnsTheOriginalReplayWithoutChangingTheHttpContract() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        when(service.retry(
            "server-tenant",
            "alice",
            PLAN_ID,
            CANDIDATE_ID,
            4,
            "retry-1",
            "retry failed build"
        ))
            .thenReturn(new CommandResult(candidate(), true, List.of()));

        mockMvc
            .perform(
                post(
                    "/api/modeling/plans/{planId}/release-candidates/{candidateId}/retry",
                    PLAN_ID,
                    CANDIDATE_ID
                )
                    .header("If-Match", ETAG)
                    .header("Idempotency-Key", "retry-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"retry failed build\"}")
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG))
            .andExpect(jsonPath("$.data.replayed").value(true));
    }

    @Test
    void cancelRequiresStrongEtagAndDelegatesOnlyTheServerOwnedCancelCommand() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        CandidateView cancelled = new CandidateView(
            candidate().id(),
            candidate().tenantId(),
            candidate().planId(),
            candidate().environment(),
            DeliveryStatus.CANCELLED,
            5,
            candidate().idempotencyKey(),
            candidate().requestHash(),
            candidate().audit(),
            "alice",
            candidate().lastModifiedAt().plusSeconds(1),
            List.of()
        );
        when(service.cancel(
            "server-tenant",
            "alice",
            PLAN_ID,
            CANDIDATE_ID,
            4,
            "cancel-1",
            "abandon failed build"
        ))
            .thenReturn(new CommandResult(cancelled, false, List.of()));

        mockMvc
            .perform(
                post(
                    "/api/modeling/plans/{planId}/release-candidates/{candidateId}/cancel",
                    PLAN_ID,
                    CANDIDATE_ID
                )
                    .header("If-Match", ETAG)
                    .header("Idempotency-Key", "cancel-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"abandon failed build\"}")
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"release-candidate:" + CANDIDATE_ID + ":5\""))
            .andExpect(jsonPath("$.data.candidate.status").value("CANCELLED"));
    }

    @Test
    void missingIdempotencyKeyIsRejectedBeforeCallingTheService() throws Exception {
        mockMvc
            .perform(
                post("/api/modeling/plans/{planId}/release-candidates", PLAN_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"environment\":\"prod\",\"entries\":[],\"reason\":\"prepare\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MODEL_RELEASE_CANDIDATE_IDEMPOTENCY_KEY_REQUIRED"));

        verify(service, never()).create(any(), any(), any(), any());
    }

    @Test
    void versionConflictAndForbiddenUseStableHttpContracts() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        when(service.lock(
            "server-tenant",
            "alice",
            PLAN_ID,
            CANDIDATE_ID,
            4,
            "lock-1",
            "scope confirmed"
        ))
            .thenThrow(
                new ModelReleaseCandidateException(
                    "MODEL_RELEASE_CANDIDATE_VERSION_CONFLICT",
                    "Candidate version changed",
                    ModelReleaseCandidateException.Kind.CONFLICT,
                    Map.of("currentVersion", 5)
                )
            );

        mockMvc
            .perform(
                post("/api/modeling/plans/{planId}/release-candidates/{candidateId}/lock", PLAN_ID, CANDIDATE_ID)
                    .header("If-Match", ETAG)
                    .header("Idempotency-Key", "lock-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"scope confirmed\"}")
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MODEL_RELEASE_CANDIDATE_VERSION_CONFLICT"))
            .andExpect(jsonPath("$.data.currentVersion").value(5));

        when(service.workspace("server-tenant", "alice", PLAN_ID)).thenThrow(
            new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_PLAN_FORBIDDEN",
                "Plan is not available",
                ModelReleaseCandidateException.Kind.FORBIDDEN
            )
        );
        mockMvc
            .perform(get("/api/modeling/plans/{planId}/release-candidates/workspace", PLAN_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("MODEL_RELEASE_CANDIDATE_PLAN_FORBIDDEN"));
    }

    @Test
    void replacementPassesIfMatchVersionAndIdempotencyAndReturnsResolvableLocation() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        when(service.createReplacement(
            eq("server-tenant"),
            eq("alice"),
            eq(PLAN_ID),
            eq(CANDIDATE_ID),
            eq(4),
            any()
        ))
            .thenReturn(new CommandResult(candidate(), false, List.of()));

        mockMvc
            .perform(
                post(
                    "/api/modeling/plans/{planId}/release-candidates/{candidateId}/replacement",
                    PLAN_ID,
                    CANDIDATE_ID
                )
                    .header("If-Match", ETAG)
                    .header("Idempotency-Key", "replacement-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"environment":"prod","entries":[],"reason":"replace stale scope"}
                        """
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(
                header().string(
                    "Location",
                    "/api/modeling/plans/" + PLAN_ID + "/release-candidates/" + CANDIDATE_ID
                )
            )
            .andExpect(header().string("ETag", ETAG));

        verify(service).createReplacement(
            eq("server-tenant"),
            eq("alice"),
            eq(PLAN_ID),
            eq(CANDIDATE_ID),
            eq(4),
            any()
        );
    }

    private static WorkbenchView workbench() {
        return new WorkbenchView(
            PLAN_ID,
            WorkbenchState.READY,
            scopedCandidate(),
            Arrays.stream(DeliveryEvidenceType.values())
                .map(type ->
                    new EvidenceSummaryView(
                        type,
                        EvidenceState.UNAVAILABLE,
                        "MODEL_RELEASE_EVIDENCE_NOT_AVAILABLE",
                        "No current evidence is available"
                    )
                )
                .toList(),
            null,
            List.of(WorkspaceAction.UPDATE_SCOPE, WorkspaceAction.START_BUILD),
            ETAG
        );
    }

    private static CandidateView candidate() {
        Instant now = Instant.parse("2026-07-24T10:00:00Z");
        return new CandidateView(
            CANDIDATE_ID,
            "server-tenant",
            PLAN_ID,
            "prod",
            DeliveryStatus.DRAFT,
            4,
            "create-1",
            "a".repeat(64),
            new DeliveryAuditView("alice", now.minusSeconds(60), null, null, null, null, null, null),
            "alice",
            now,
            List.of()
        );
    }

    private static CandidateView candidateAtVersion(int version) {
        CandidateView candidate = candidate();
        return new CandidateView(
            candidate.id(),
            candidate.tenantId(),
            candidate.planId(),
            candidate.environment(),
            candidate.status(),
            version,
            candidate.idempotencyKey(),
            candidate.requestHash(),
            candidate.audit(),
            candidate.lastModifiedBy(),
            candidate.lastModifiedAt().plusSeconds(version - candidate.version()),
            candidate.entries()
        );
    }

    private static CandidateView scopedCandidate() {
        CandidateView candidate = candidate();
        return new CandidateView(
            candidate.id(),
            candidate.tenantId(),
            candidate.planId(),
            candidate.environment(),
            candidate.status(),
            candidate.version(),
            candidate.idempotencyKey(),
            candidate.requestHash(),
            candidate.audit(),
            candidate.lastModifiedBy(),
            candidate.lastModifiedAt(),
            List.of(
                new EntryView(
                    UUID.fromString("30000000-0000-0000-0000-000000000001"),
                    candidate.tenantId(),
                    candidate.id(),
                    candidate.planId(),
                    UUID.fromString("40000000-0000-0000-0000-000000000001"),
                    2,
                    "b".repeat(64),
                    null,
                    ImplementationMode.DBT_MANAGED,
                    candidate.status(),
                    0,
                    "primary"
                )
            )
        );
    }
}
