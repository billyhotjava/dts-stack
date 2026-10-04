package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.AttributeSemantic;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ReuseScope;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ScopeType;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.Status;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import com.yuzhi.dts.platform.service.modeling.DimensionModelApplicationService;
import com.yuzhi.dts.platform.service.modeling.DimensionModelApplicationService.OperationResult;
import com.yuzhi.dts.platform.service.modeling.DimensionModelCreateRequestDecoder;
import com.yuzhi.dts.platform.service.modeling.DimensionModelCreateRequestDecoder.BindingMode;
import com.yuzhi.dts.platform.service.modeling.DimensionModelCreateRequestDecoder.PreparedCreate;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionDefinitionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRejectionAudit;
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
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = DimensionModelResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
        "dts.platform.modeling.default-tenant-id=server-tenant",
    }
)
@AutoConfigureMockMvc(addFilters = false)
class DimensionModelResourceTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DEFINITION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID OPERATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.yuzhi.dts.platform.service.modeling.ModelingContextInitializationService contexts;

    @org.junit.jupiter.api.BeforeEach
    void resolveExplicitContext() {
        org.mockito.Mockito.when(contexts.withContext(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
                java.util.function.Function<java.util.List<com.fasterxml.jackson.databind.JsonNode>, Object> save = invocation.getArgument(3);
                return save.apply(invocation.getArgument(2));
            });
    }

    @MockBean
    private DimensionModelApplicationService service;

    @MockBean
    private DimensionModelCreateRequestDecoder decoder;

    @MockBean
    private WarehousePlanActorProvider actorProvider;

    @MockBean
    private AuditService auditService;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @MockBean
    private DbtImplementationDraftRejectionAudit dbtImplementationDraftRejectionAudit;

    @Test
    void createsTheCompleteAtomicCommandWithServerTenantActorAndNoStoreResponse() throws Exception {
        PreparedCreate prepared = org.mockito.Mockito.mock(PreparedCreate.class);
        OperationResult result = result(false, model(2), model(2));
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        when(decoder.decode(any())).thenReturn(prepared);
        when(service.create("server-tenant", "alice", prepared)).thenReturn(result);

        mockMvc
            .perform(
                post("/api/modeling/model-specs/dimension")
                    .header("X-Tenant-Id", "request-tenant-must-not-win")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"operationId\":\"" + OPERATION_ID + "\",\"definitionBinding\":{},\"modelSpec\":{}}")
            )
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/modeling/model-specs/" + MODEL_ID))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.operationId").value(OPERATION_ID.toString()))
            .andExpect(jsonPath("$.data.bindingMode").value("CREATE"))
            .andExpect(jsonPath("$.data.dimensionDefinitionRevision.id").value(DEFINITION_ID.toString()))
            .andExpect(jsonPath("$.data.modelSpecRevision.revision").value(2))
            .andExpect(jsonPath("$.data.currentModelSpec.id").value(MODEL_ID.toString()))
            .andExpect(jsonPath("$.data.replayed").value(false));
    }

    @Test
    void recoversByCanonicalLowercaseOperationIdAndReturnsFixedAndCurrentRevisions() throws Exception {
        ModelSpecView fixed = model(2);
        ModelSpecView current = model(4);
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        when(decoder.parseOperationId(OPERATION_ID.toString())).thenReturn(OPERATION_ID);
        when(service.recover("server-tenant", "alice", OPERATION_ID)).thenReturn(result(true, fixed, current));

        mockMvc
            .perform(get("/api/modeling/model-specs/dimension/operations/{operationId}", OPERATION_ID))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.modelSpecRevision.revision").value(2))
            .andExpect(jsonPath("$.data.currentModelSpec.revision").value(4))
            .andExpect(jsonPath("$.data.replayed").value(true));
    }

    @Test
    void writesStrictSanitizedFailureAuditForRejectedCreateCommands() throws Exception {
        ModelSpecException rejection = new ModelSpecException(
            "DIMENSION_MODEL_OPERATION_ID_INVALID",
            "Operation id is invalid",
            ModelSpecException.Kind.UNPROCESSABLE,
            Map.of("unsafeBusinessValue", "must-not-be-audited")
        );
        when(decoder.decode(any())).thenThrow(rejection);

        mockMvc
            .perform(
                post("/api/modeling/model-specs/dimension")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"operationId\":\"invalid\"}")
            )
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("DIMENSION_MODEL_OPERATION_ID_INVALID"));

        verify(auditService).auditActionStrict(
            "MODELING_DIMENSION_MODEL_CREATE",
            AuditStage.FAIL,
            "dimension-model-request",
            Map.of(
                "errorCode",
                "DIMENSION_MODEL_OPERATION_ID_INVALID",
                "errorKind",
                "UNPROCESSABLE"
            )
        );
    }

    @Test
    void failsClosedWhenFailureAuditCannotBePersisted() throws Exception {
        when(decoder.decode(any())).thenThrow(
            new ModelSpecException(
                "DIMENSION_MODEL_OPERATION_ID_INVALID",
                "Operation id is invalid",
                ModelSpecException.Kind.UNPROCESSABLE
            )
        );
        doThrow(new IllegalStateException("audit unavailable"))
            .when(auditService)
            .auditActionStrict(
                eq("MODELING_DIMENSION_MODEL_CREATE"),
                eq(AuditStage.FAIL),
                eq("dimension-model-request"),
                any()
            );

        mockMvc
            .perform(
                post("/api/modeling/model-specs/dimension")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"operationId\":\"invalid\"}")
            )
            .andExpect(status().is5xxServerError());
    }

    @Test
    void requiresAuthenticationForMenuAuthorizedCreationAndRecovery() throws Exception {
        PreAuthorize create = DimensionModelResource.class
            .getMethod("create", com.fasterxml.jackson.databind.JsonNode.class)
            .getAnnotation(PreAuthorize.class);
        PreAuthorize recover = DimensionModelResource.class
            .getMethod("recover", String.class)
            .getAnnotation(PreAuthorize.class);

        assertThat(create).isNotNull().extracting(PreAuthorize::value).asString().isEqualTo("isAuthenticated()");
        assertThat(recover).isNotNull().extracting(PreAuthorize::value).asString().isEqualTo("isAuthenticated()");
    }

    private static OperationResult result(
        boolean replayed,
        ModelSpecView operationRevision,
        ModelSpecView current
    ) {
        return new OperationResult(
            OPERATION_ID,
            BindingMode.CREATE,
            definition(),
            operationRevision,
            current,
            replayed
        );
    }

    private static View definition() {
        return new View(
            DEFINITION_ID,
            "dim_30000000000000000000000000000001",
            DOMAIN_ID,
            "Customer",
            null,
            "Customer dimension",
            "alice",
            ReuseScope.DOMAIN,
            List.of(),
            Status.CURRENT,
            2,
            "b".repeat(64),
            1,
            Instant.EPOCH,
            Instant.EPOCH,
            ScopeType.DOMAIN,
            null,
            List.of(
                new AttributeSemantic(
                    "CUSTOMER_CODE",
                    "Customer code",
                    "Customer code",
                    true,
                    null,
                    null,
                    1
                )
            )
        );
    }

    private static ModelSpecView model(int revision) {
        return new ModelSpecView(
            2,
            MODEL_ID,
            PLAN_ID,
            DOMAIN_ID,
            ModelType.DIMENSION,
            Layer.DWD,
            "dim_customer",
            "Customer dimension",
            ImplementationMode.DESIGNER_GENERATED,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            new DimensionDefinitionRef(DEFINITION_ID, 2),
            ModelStatus.DRAFT,
            revision,
            "d".repeat(64),
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(revision),
            CompatibilityMode.CANONICAL,
            null,
            null,
            "DEFAULT",
            null,
            null
        );
    }
}
