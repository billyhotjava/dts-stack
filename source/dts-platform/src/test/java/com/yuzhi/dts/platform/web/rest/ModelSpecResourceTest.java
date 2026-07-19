package com.yuzhi.dts.platform.web.rest;

import static org.mockito.ArgumentMatchers.any;
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
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import com.yuzhi.dts.platform.service.modeling.ModelSpecCreateRequestDecoder;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
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
    private WarehousePlanActorProvider actorProvider;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

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
             "modelType":"FACT","layer":"DWD","name":"customer_detail",
             "implementationMode":"DESIGNER_GENERATED",
             "grain":{"statement":"one row per customer event","keys":["customer_id"]},
             "sourceRefs":[{"kind":"TABLE","ref":"ods.customer","layer":"ODS","role":"PRIMARY","sortOrder":0,
                            "sourceBindingId":"50000000-0000-0000-0000-000000000001","resolvedVersion":"v1"}],
             "idempotencyKey":"create-1"}
            """;
    }

    private static String updateJson() {
        return createJson().replace(",\n             \"idempotencyKey\":\"create-1\"", "");
    }
}
