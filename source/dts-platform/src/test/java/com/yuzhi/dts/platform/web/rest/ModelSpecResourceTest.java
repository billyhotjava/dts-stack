package com.yuzhi.dts.platform.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import com.yuzhi.dts.platform.service.modeling.ModelSpecCreateRequestDecoder;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.GateStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.GateView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.Stage;
import com.yuzhi.dts.platform.service.modeling.ModelSpecUpdateRequestDecoder;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = ModelSpecResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
        "dts.platform.modeling.default-tenant-id=server-tenant",
    }
)
@AutoConfigureMockMvc(addFilters = false)
@Import({ ModelSpecCreateRequestDecoder.class, ModelSpecUpdateRequestDecoder.class })
class ModelSpecResourceTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_BINDING_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final String CHECKSUM = "a".repeat(64);
    private static final String ETAG = "\"model-spec:" + MODEL_ID + ":1:" + CHECKSUM + "\"";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelSpecApplicationService service;

    @MockBean
    private ModelSpecStageGateService stageGateService;

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
    void postsGetsAndPutsUsingServerTenantAndStrongEtags() throws Exception {
        ModelSpecView view = view();
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        when(service.create(eq("server-tenant"), eq("alice"), any())).thenReturn(new CreateResult(view, false));
        when(service.get("server-tenant", MODEL_ID)).thenReturn(view);
        when(service.update(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any())).thenReturn(view);

        mockMvc
            .perform(
                post("/api/modeling/model-specs")
                    .header("X-Tenant-Id", "request-tenant-must-not-win")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createJson())
            )
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/modeling/model-specs/" + MODEL_ID))
            .andExpect(header().string("ETag", ETAG))
            .andExpect(jsonPath("$.data.id").value(MODEL_ID.toString()))
            .andExpect(jsonPath("$.data.compatibilityMode").value("CANONICAL"));

        mockMvc
            .perform(get("/api/modeling/model-specs/{id}", MODEL_ID))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG));

        mockMvc
            .perform(
                put("/api/modeling/model-specs/{id}", MODEL_ID)
                    .header("If-Match", ETAG)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateJson())
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG));

        verify(service).create(eq("server-tenant"), eq("alice"), any());
        verify(service).get("server-tenant", MODEL_ID);
        verify(service).update(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any());
    }

    @Test
    void rejectsUnknownCreateFieldsAndMissingPutPreconditionAtTheBoundary() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));

        mockMvc
            .perform(
                post("/api/modeling/model-specs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createJson().replace("\"idempotencyKey\"", "\"objectId\":\"legacy\",\"idempotencyKey\""))
            )
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("MODEL_SPEC_REQUEST_INVALID"))
            .andExpect(jsonPath("$.data[0].code").value("MODEL_SPEC_FIELD_NOT_ALLOWED"));

        mockMvc
            .perform(
                put("/api/modeling/model-specs/{id}", MODEL_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateJson())
            )
            .andExpect(status().isPreconditionRequired())
            .andExpect(jsonPath("$.code").value("MODEL_SPEC_IF_MATCH_REQUIRED"));

        verify(service, never()).create(any(), any(), any());
        verify(service, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void savesAnExistingDraftDefinitionWithTheCanonicalEtagAndNoImplementationCommand() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        when(service.updateDefinition(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any())).thenReturn(view());

        mockMvc
            .perform(
                put("/api/modeling/model-specs/{id}/definition", MODEL_ID)
                    .header("If-Match", ETAG)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateJson())
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG))
            .andExpect(jsonPath("$.data.id").value(MODEL_ID.toString()));

        verify(service).updateDefinition(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any());
        verify(service, never()).update(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any());
    }

    @Test
    void deletesDraftsOnlyWithAStrongCurrentEtag() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));

        mockMvc
            .perform(delete("/api/modeling/model-specs/{id}", MODEL_ID))
            .andExpect(status().isPreconditionRequired())
            .andExpect(jsonPath("$.code").value("MODEL_SPEC_IF_MATCH_REQUIRED"));

        mockMvc
            .perform(delete("/api/modeling/model-specs/{id}", MODEL_ID).header("If-Match", ETAG))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.message").value("OK"));

        verify(service).deleteDraft(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any());
    }

    @Test
    void archivesActiveModelsWithAStrongCurrentEtag() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        when(service.archive(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any())).thenReturn(view());

        mockMvc
            .perform(post("/api/modeling/model-specs/{id}/archive", MODEL_ID))
            .andExpect(status().isPreconditionRequired())
            .andExpect(jsonPath("$.code").value("MODEL_SPEC_IF_MATCH_REQUIRED"));

        mockMvc
            .perform(post("/api/modeling/model-specs/{id}/archive", MODEL_ID).header("If-Match", ETAG))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG))
            .andExpect(jsonPath("$.status").value(200));

        verify(service).archive(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any());
    }

    @Test
    void appliesRevisionPinnedStandardBindingsWithAStrongCurrentEtag() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        when(service.applyStandardElementBindings(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any()))
            .thenReturn(view());

        mockMvc
            .perform(
                post("/api/modeling/model-specs/{id}/standard-element-bindings", MODEL_ID)
                    .header("If-Match", ETAG)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"bindings":[{"fieldName":"customer_id",
                         "standardElementId":"40000000-0000-0000-0000-000000000001",
                         "standardElementVersion":2}]}
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG));

        verify(service).applyStandardElementBindings(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any());
    }

    @Test
    void mapsStaleCasTo409WithCurrentVersionDetails() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        when(service.update(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any())).thenThrow(
            new ModelSpecException(
                "MODEL_SPEC_REVISION_CONFLICT",
                "ModelSpec was changed by another operation",
                ModelSpecException.Kind.CONFLICT,
                Map.of("currentRevision", 2, "currentChecksum", "b".repeat(64), "currentEtag", "new-etag")
            )
        );

        mockMvc
            .perform(
                put("/api/modeling/model-specs/{id}", MODEL_ID)
                    .header("If-Match", ETAG)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateJson())
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MODEL_SPEC_REVISION_CONFLICT"))
            .andExpect(jsonPath("$.data.currentRevision").value(2));
    }

    @Test
    void returnsServerOwnedStageGateDecisions() throws Exception {
        when(stageGateService.evaluateAll("server-tenant", MODEL_ID)).thenReturn(
            List.of(new GateView(MODEL_ID, 1, CHECKSUM, Stage.IMPLEMENTATION_READY, GateStatus.READY, List.of()))
        );

        mockMvc
            .perform(get("/api/modeling/model-specs/{id}/stage-gates", MODEL_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].stage").value("IMPLEMENTATION_READY"))
            .andExpect(jsonPath("$.data[0].status").value("READY"))
            .andExpect(jsonPath("$.data[0].revision").value(1));

        verify(stageGateService).evaluateAll("server-tenant", MODEL_ID);
    }

    @Test
    void returnsRevisionPinnedDependencyGraph() throws Exception {
        UUID upstreamId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        when(service.dependencyGraph("server-tenant", MODEL_ID)).thenReturn(
            new ModelSpecApplicationService.DependencyGraph(
                MODEL_ID,
                List.of(
                    new ModelSpecApplicationService.DependencyNode(
                        MODEL_ID,
                        PLAN_ID,
                        2,
                        2,
                        "customer_summary",
                        ModelType.SUMMARY,
                        Layer.DWS,
                        ModelStatus.DRAFT,
                        false
                    ),
                    new ModelSpecApplicationService.DependencyNode(
                        upstreamId,
                        PLAN_ID,
                        3,
                        4,
                        "customer_detail",
                        ModelType.FACT,
                        Layer.DWD,
                        ModelStatus.PUBLISHED,
                        false
                    )
                ),
                List.of(
                    new ModelSpecApplicationService.DependencyEdge(
                        MODEL_ID,
                        upstreamId,
                        3,
                        4,
                        ModelSpecApplicationService.DependencyState.STALE
                    )
                )
            )
        );

        mockMvc
            .perform(get("/api/modeling/model-specs/{id}/dependencies", MODEL_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rootModelSpecId").value(MODEL_ID.toString()))
            .andExpect(jsonPath("$.data.edges[0].pinnedRevision").value(3))
            .andExpect(jsonPath("$.data.edges[0].currentRevision").value(4))
            .andExpect(jsonPath("$.data.edges[0].state").value("STALE"));

        verify(service).dependencyGraph("server-tenant", MODEL_ID);
    }

    @Test
    void returnsTheExactReadablePinnedRevisionWithItsOwnEtag() throws Exception {
        ModelRevisionRef reference = new ModelRevisionRef(MODEL_ID, 1);
        ModelSpecView pinned = view();
        when(service.revision("server-tenant", reference)).thenReturn(pinned);

        mockMvc
            .perform(get("/api/modeling/model-specs/{id}/revisions/{revision}", MODEL_ID, 1))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG))
            .andExpect(jsonPath("$.data.id").value(MODEL_ID.toString()))
            .andExpect(jsonPath("$.data.revision").value(1))
            .andExpect(jsonPath("$.data.checksum").value(CHECKSUM));

        verify(service).revision("server-tenant", reference);
    }

    @Test
    void previewsAndAppliesExplicitDraftReclassificationWithTheStrongModelEtag() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        ModelSpecApplicationService.ReclassificationPreview preview = new ModelSpecApplicationService.ReclassificationPreview(
            true,
            ModelType.FACT,
            ModelType.DIMENSION,
            Layer.DWD,
            List.of("name", "description", "fields"),
            List.of("dimensionDefinitionRef", "grain", "fields.KEY"),
            List.of("grain", "factShape", "timeSemantics"),
            List.of(),
            1,
            CHECKSUM
        );
        when(service.previewReclassification(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any()))
            .thenReturn(preview);
        when(service.reclassify(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any())).thenReturn(view());

        mockMvc
            .perform(
                post("/api/modeling/model-specs/{id}/reclassify-preview", MODEL_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"targetType":"DIMENSION",
                         "dimensionDefinitionRef":{"dimensionDefinitionId":"60000000-0000-0000-0000-000000000001","revision":1}}
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.eligible").value(true))
            .andExpect(jsonPath("$.data.clearFields[0]").value("grain"))
            .andExpect(jsonPath("$.data.requiredFields[0]").value("dimensionDefinitionRef"));

        mockMvc
            .perform(
                post("/api/modeling/model-specs/{id}/reclassify", MODEL_ID)
                    .header("If-Match", ETAG)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"targetType":"DIMENSION",
                         "dimensionDefinitionRef":{"dimensionDefinitionId":"60000000-0000-0000-0000-000000000001","revision":1},
                         "acceptedClearFields":["grain","factShape","timeSemantics"],
                         "idempotencyKey":"finance-r4-to-dimension"}
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG));

        verify(service).previewReclassification(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any());
        verify(service).reclassify(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), any());
    }

    @Test
    void mapsHiddenAndMissingPinnedRevisionsToTheSameNotFoundContract() throws Exception {
        ModelSpecException hiddenOrMissing = new ModelSpecException(
            "MODEL_SPEC_NOT_FOUND",
            "ModelSpec not found",
            ModelSpecException.Kind.NOT_FOUND
        );
        when(service.revision("server-tenant", new ModelRevisionRef(MODEL_ID, 2))).thenThrow(hiddenOrMissing);
        when(service.revision("server-tenant", new ModelRevisionRef(MODEL_ID, 3))).thenThrow(hiddenOrMissing);

        for (int revision : List.of(2, 3)) {
            mockMvc
                .perform(get("/api/modeling/model-specs/{id}/revisions/{revision}", MODEL_ID, revision))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODEL_SPEC_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("ModelSpec not found"));
        }

        verify(service).revision("server-tenant", new ModelRevisionRef(MODEL_ID, 2));
        verify(service).revision("server-tenant", new ModelRevisionRef(MODEL_ID, 3));
    }

    @Test
    void rejectsInvalidPinnedRevisionPathValuesWithoutResolvingARevision() throws Exception {
        ModelRevisionRef invalid = new ModelRevisionRef(MODEL_ID, 0);
        when(service.revision("server-tenant", invalid)).thenThrow(
            new ModelSpecException(
                "MODEL_SPEC_REVISION_REF_INVALID",
                "ModelSpec revision reference is invalid",
                ModelSpecException.Kind.BAD_REQUEST
            )
        );

        mockMvc
            .perform(get("/api/modeling/model-specs/{id}/revisions/{revision}", MODEL_ID, 0))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MODEL_SPEC_REVISION_REF_INVALID"));
        mockMvc
            .perform(get("/api/modeling/model-specs/{id}/revisions/{revision}", MODEL_ID, "not-a-number"))
            .andExpect(status().isBadRequest());

        verify(service).revision("server-tenant", invalid);
        verify(service, never()).revision("server-tenant", new ModelRevisionRef(MODEL_ID, -1));
    }

    private static ModelSpecView view() {
        return new ModelSpecView(
            2,
            MODEL_ID,
            PLAN_ID,
            DOMAIN_ID,
            ModelType.FACT,
            Layer.DWD,
            "customer_detail",
            null,
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per customer event", List.of("customer_id")),
            null,
            null,
            List.of(new ModelField("customer_id", "varchar", false, null, FieldRole.KEY, null)),
            List.of(
                new SourceRef(
                    SourceKind.TABLE,
                    "ods.customer",
                    Layer.ODS,
                    SourceRole.PRIMARY,
                    null,
                    null,
                    null,
                    0,
                    SOURCE_BINDING_ID,
                    "v1"
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            ModelStatus.DRAFT,
            1,
            CHECKSUM,
            Instant.EPOCH,
            Instant.EPOCH,
            CompatibilityMode.CANONICAL,
            null
        );
    }

    private static String createJson() {
        return """
            {"planId":"10000000-0000-0000-0000-000000000001",
             "domainId":"20000000-0000-0000-0000-000000000001",
             "modelType":"FACT","name":"customer_detail",
             "idempotencyKey":"create-1"}
            """;
    }

    private static String updateJson() {
        return """
            {"planId":"10000000-0000-0000-0000-000000000001",
             "domainId":"20000000-0000-0000-0000-000000000001",
             "modelType":"FACT","layer":"DWD","name":"customer_detail",
             "implementationMode":"DESIGNER_GENERATED",
             "grain":{"statement":"one row per customer event","keys":["customer_id"]},
             "sourceRefs":[{"kind":"TABLE","ref":"ods.customer","layer":"ODS","role":"PRIMARY","sortOrder":0,
                            "sourceBindingId":"50000000-0000-0000-0000-000000000001","resolvedVersion":"v1"}]}
            """;
    }
}
