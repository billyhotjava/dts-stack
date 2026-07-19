package com.yuzhi.dts.platform.web.rest;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.CONFIRMED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.EditUnit.BUSINESS_SCOPE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.EditUnit.CATEGORY_SCOPE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.ARCHIVED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.DRAFT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType.CATALOG_TABLE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.UpdatePlanHeaderCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.BusinessScope;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryBindingView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryReadiness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryScopeCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryScopeView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanResult;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningBaseline;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyReadiness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ProcessBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBusinessMapping;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceFreshness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryReadiness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.Versioned;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.EvidenceFreshness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.NextAction;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.PrimaryBlocker;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageCode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageProjection;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageView;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.time.Instant;
import java.util.Arrays;
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
    value = WarehousePlanResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
        "dts.platform.modeling.default-tenant-id=server-tenant",
    }
)
@AutoConfigureMockMvc(addFilters = false)
class WarehousePlanResourceTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WarehousePlanApplicationService service;

    @MockBean
    private WarehousePlanStageProjectionService stageProjectionService;

    @MockBean
    private WarehousePlanActorProvider actorProvider;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @Test
    void createsListsAndLoadsUsingOnlyTheServerTenant() throws Exception {
        WarehousePlanHeader created = planHeader(1, DRAFT);
        CreateWarehousePlanResult createResult = new CreateWarehousePlanResult(
            PLAN_ID,
            created,
            1,
            "\"plan-head:1\"",
            List.of(),
            "/modeling/plans/" + PLAN_ID + "/baseline",
            false
        );
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("owner-1", "department-1"));
        when(service.create(eq("server-tenant"), any())).thenReturn(createResult);
        when(service.list("server-tenant", "DRAFT")).thenReturn(List.of(created));
        when(service.get("server-tenant", PLAN_ID)).thenReturn(created);

        mockMvc
            .perform(
                post("/api/modeling/warehouse-plans")
                    .header("X-Tenant-Id", "request-tenant-must-not-win")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name":"Neutral warehouse","objective":"Trusted metrics","scope":"Initial scope",
                          "onboardingMode":"BUSINESS_FIRST","initialSourceRefs":[],"idempotencyKey":"request-1"
                        }
                        """
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(header().string("ETag", "\"plan-head:1\""))
            .andExpect(header().string("Location", "/api/modeling/warehouse-plans/" + PLAN_ID))
            .andExpect(jsonPath("$.data.planId").value(PLAN_ID.toString()))
            .andExpect(jsonPath("$.data.plan.tenantId").value("server-tenant"))
            .andExpect(jsonPath("$.data.nextAction").value("/modeling/plans/" + PLAN_ID + "/baseline"))
            .andExpect(jsonPath("$.data.replayed").value(false));

        mockMvc
            .perform(get("/api/modeling/warehouse-plans").param("lifecycleStatus", "DRAFT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(PLAN_ID.toString()));

        mockMvc
            .perform(get("/api/modeling/warehouse-plans/{id}", PLAN_ID))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"plan-head:1\""));

        verify(service).create(eq("server-tenant"), any());
        verify(service).list("server-tenant", "DRAFT");
        verify(service).get("server-tenant", PLAN_ID);
    }

    @Test
    void rejectsTenantIdInRequestBody() throws Exception {
        mockMvc
            .perform(
                post("/api/modeling/warehouse-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "tenantId":"body-tenant","code":"warehouse-neutral","name":"Neutral warehouse",
                          "objective":"Objective","onboardingMode":"BUSINESS_FIRST","idempotencyKey":"request-tenant"
                        }
                        """
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_PLAN_TENANT_NOT_ACCEPTED"));

        verify(service, never()).create(any(), any());
    }

    @Test
    void rejectsANullInitialSourceWithAStableBadRequest() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("owner-1", "department-1"));
        when(service.create(eq("server-tenant"), any())).thenThrow(
            new WarehousePlanException(
                "WAREHOUSE_PLAN_INITIAL_SOURCE_INVALID",
                "Initial sources must be unique and complete",
                null
            )
        );

        mockMvc
            .perform(
                post("/api/modeling/warehouse-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Invalid asset plan","onboardingMode":"ASSET_FIRST",
                         "initialSourceRefs":[null],"idempotencyKey":"request-null-source"}
                        """
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_PLAN_INITIAL_SOURCE_INVALID"));

        verify(service).create(eq("server-tenant"), any());
    }

    @Test
    void mapsCreateIdempotencyConflictToHttp409() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("owner-1", "department-1"));
        when(service.create(eq("server-tenant"), any())).thenThrow(
            new WarehousePlanException(
                "WAREHOUSE_PLAN_IDEMPOTENCY_CONFLICT",
                "The previous create attempt used different content",
                null
            )
        );

        mockMvc
            .perform(
                post("/api/modeling/warehouse-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Conflicting plan","objective":"Objective","onboardingMode":"BUSINESS_FIRST",
                         "initialSourceRefs":[],"idempotencyKey":"request-conflict"}
                        """
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_PLAN_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void rejectsForgedOwnerAndMissingAuthenticatedActorBeforeCreating() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("owner-1", "department-1"));

        mockMvc
            .perform(
                post("/api/modeling/warehouse-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Forged","objective":"Objective","ownerId":"owner-2",
                         "onboardingMode":"BUSINESS_FIRST","idempotencyKey":"request-forged"}
                        """
                    )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_PLAN_OWNER_FORBIDDEN"));

        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor(null, null));
        mockMvc
            .perform(
                post("/api/modeling/warehouse-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Anonymous","objective":"Objective","onboardingMode":"BUSINESS_FIRST",
                         "idempotencyKey":"request-anonymous"}
                        """
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_PLAN_AUTHENTICATED_ACTOR_REQUIRED"));

        verify(service, never()).create(any(), any());
    }

    @Test
    void requiresPlanHeadIfMatchForUpdateAndArchive() throws Exception {
        when(service.updateHeader(eq("server-tenant"), eq(PLAN_ID), eq(1), any())).thenReturn(planHeader(2, DRAFT));
        when(service.archive("server-tenant", PLAN_ID, 2)).thenReturn(planHeader(3, ARCHIVED));

        mockMvc
            .perform(
                patch("/api/modeling/warehouse-plans/{id}", PLAN_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Renamed\",\"ownerId\":\"owner-2\"}")
            )
            .andExpect(status().isPreconditionRequired())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_PLAN_IF_MATCH_REQUIRED"));

        mockMvc
            .perform(
                patch("/api/modeling/warehouse-plans/{id}", PLAN_ID)
                    .header("If-Match", "\"plan-head:1\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Renamed\",\"ownerId\":\"owner-2\"}")
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"plan-head:2\""));

        mockMvc
            .perform(post("/api/modeling/warehouse-plans/{id}/archive", PLAN_ID).header("If-Match", "\"plan-head:2\""))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"plan-head:3\""))
            .andExpect(jsonPath("$.data.lifecycleStatus").value("ARCHIVED"));
    }

    @Test
    void savesAllBaselineEditUnitsWithIndependentEtagsAndConfirmsTheBaseline() throws Exception {
        BusinessScope scope = scope();
        SourceLocator sourceLocator = new SourceLocator(SOURCE_ID, null, null, null, null, null, null);
        SourceInventoryCommand sourceCommand = new SourceInventoryCommand(
            List.of(new SourceBindingCommand(null, CATALOG_TABLE, sourceLocator, CONFIRMED, null))
        );
        SourceInventoryView sourceInventory = new SourceInventoryView(
            List.of(
                new SourceBindingView(
                    SOURCE_ID,
                    CATALOG_TABLE,
                    sourceLocator,
                    SOURCE_ID.toString(),
                    CONFIRMED,
                    null,
                    "Orders",
                    "schema-v1",
                    "schema-v1",
                    ResolutionStatus.AVAILABLE,
                    SourceFreshness.CURRENT,
                    Instant.parse("2026-07-19T00:00:00Z")
                )
            ),
            SourceInventoryReadiness.READY,
            List.of(),
            2,
            "\"sources:2\"",
            Instant.parse("2026-07-19T00:00:00Z")
        );
        List<SourceBusinessMapping> mappings = mappings();
        PlanningPolicyCommand policy = policyCommand();
        PlanningPolicyView policyView = implementationReadyPolicyView();
        PlanningBaseline ready = new PlanningBaseline(true, List.of());
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("owner-1", "department-1"));
        when(service.saveBusinessScope("server-tenant", PLAN_ID, 1, scope)).thenReturn(new Versioned<>(scope, 2));
        when(
            service.saveSources(
                "server-tenant",
                PLAN_ID,
                1,
                sourceCommand,
                new AccessContext("server-tenant", "owner-1", "department-1")
            )
        ).thenReturn(sourceInventory);
        when(service.saveSourceMappings("server-tenant", PLAN_ID, 1, mappings)).thenReturn(new Versioned<>(mappings, 2));
        when(service.savePlanningPolicy("server-tenant", PLAN_ID, 1, policy)).thenReturn(new Versioned<>(policyView, 2));
        when(
            service.getBaseline(
                "server-tenant",
                PLAN_ID,
                new AccessContext("server-tenant", "owner-1", "department-1")
            )
        ).thenReturn(ready);
        when(service.confirmBaseline("server-tenant", PLAN_ID, 1)).thenReturn(ready);

        mockMvc
            .perform(
                put("/api/modeling/warehouse-plans/{id}/baseline/business-scope", PLAN_ID)
                    .header("If-Match", "\"business-scope:1\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"confirmed":true,
                         "domainBindings":[{"domainId":"20000000-0000-0000-0000-000000000001","confirmationStatus":"CONFIRMED"}],
                         "processBindings":[{"processId":"process-1","domainId":"20000000-0000-0000-0000-000000000001","processShape":"ACCUMULATING_SNAPSHOT","confirmationStatus":"CONFIRMED"}],
                         "metricRequirements":[]}
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"business-scope:2\""));

        mockMvc
            .perform(
                put("/api/modeling/warehouse-plans/{id}/baseline/sources", PLAN_ID)
                    .header("If-Match", "\"sources:1\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"bindings":[{"sourceType":"CATALOG_TABLE",
                          "locator":{"assetId":"30000000-0000-0000-0000-000000000001"},
                          "confirmationStatus":"CONFIRMED"}]}
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"sources:2\""))
            .andExpect(jsonPath("$.data.readiness").value("READY"))
            .andExpect(jsonPath("$.data.bindings[0].resolvedVersion").value("schema-v1"));

        mockMvc
            .perform(
                put("/api/modeling/warehouse-plans/{id}/baseline/source-mappings", PLAN_ID)
                    .header("If-Match", "\"source-mappings:1\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        [{"id":"40000000-0000-0000-0000-000000000001",
                          "sourceBindingId":"30000000-0000-0000-0000-000000000001",
                          "domainId":"20000000-0000-0000-0000-000000000001","processId":"process-1",
                          "confirmationStatus":"CONFIRMED","notes":"confirmed"}]
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"source-mappings:2\""));

        mockMvc
            .perform(
                put("/api/modeling/warehouse-plans/{id}/baseline/policy", PLAN_ID)
                    .header("If-Match", "\"policy:1\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"layerScheme":"CLASSIC_ODS_DWD_DWS_ADS","namingPolicy":"CLASSIC_LOWER_SNAKE",
                         "historyPolicy":"PRESERVE_BUSINESS_HISTORY","defaultTimeZone":"Asia/Shanghai"}
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"policy:2\""))
            .andExpect(jsonPath("$.data.version").value(2))
            .andExpect(jsonPath("$.data.value.readiness").value("IMPLEMENTATION_READY"));

        mockMvc
            .perform(get("/api/modeling/warehouse-plans/{id}/baseline", PLAN_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ready").value(true));

        mockMvc
            .perform(
                post("/api/modeling/warehouse-plans/{id}/baseline/confirm", PLAN_ID).header("If-Match", "\"plan-head:1\"")
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"plan-head:2\""));
    }

    @Test
    void returnsStableConflictAndNotFoundResponsesWithCurrentEtag() throws Exception {
        when(service.updateHeader(eq("server-tenant"), eq(PLAN_ID), eq(1), any())).thenThrow(
            new WarehousePlanException("WAREHOUSE_PLAN_VERSION_CONFLICT", "changed", 4)
        );
        when(service.get("server-tenant", UUID.fromString("90000000-0000-0000-0000-000000000009"))).thenThrow(
            new WarehousePlanException("WAREHOUSE_PLAN_NOT_FOUND", "missing", null)
        );

        mockMvc
            .perform(
                patch("/api/modeling/warehouse-plans/{id}", PLAN_ID)
                    .header("If-Match", "\"plan-head:1\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Stale\",\"ownerId\":\"owner-2\"}")
            )
            .andExpect(status().isConflict())
            .andExpect(header().string("ETag", "\"plan-head:4\""))
            .andExpect(jsonPath("$.code").value("WAREHOUSE_PLAN_VERSION_CONFLICT"));

        mockMvc
            .perform(get("/api/modeling/warehouse-plans/{id}", "90000000-0000-0000-0000-000000000009"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_PLAN_NOT_FOUND"));
    }

    @Test
    void reportsTheConflictingEditUnitEtag() throws Exception {
        when(service.saveBusinessScope(eq("server-tenant"), eq(PLAN_ID), eq(1), any())).thenThrow(
            new WarehousePlanException("WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT", "changed", 3, BUSINESS_SCOPE)
        );

        mockMvc
            .perform(
                put("/api/modeling/warehouse-plans/{id}/baseline/business-scope", PLAN_ID)
                    .header("If-Match", "\"business-scope:1\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"confirmed\":false,\"domainBindings\":[],\"processBindings\":[],\"metricRequirements\":[]}")
            )
            .andExpect(status().isConflict())
            .andExpect(header().string("ETag", "\"business-scope:3\""))
            .andExpect(jsonPath("$.code").value("WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT"));
    }

    @Test
    void getsAndSavesCanonicalCategoriesWithBodyVersionAndCategoryEtag() throws Exception {
        CategoryScopeView view = readyCategoryScope();
        CategoryScopeCommand command = new CategoryScopeCommand(List.of(new DomainBinding(DOMAIN_ID, CONFIRMED)));
        when(service.getCategoryScope("server-tenant", PLAN_ID)).thenReturn(new Versioned<>(view, 4));
        when(service.saveCategoryScope("server-tenant", PLAN_ID, 4, command)).thenReturn(new Versioned<>(view, 5));

        mockMvc
            .perform(get("/api/modeling/warehouse-plans/{id}/baseline/categories", PLAN_ID))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"category-scope:4\""))
            .andExpect(jsonPath("$.data.version").value(4))
            .andExpect(jsonPath("$.data.value.readiness").value("READY"))
            .andExpect(jsonPath("$.data.value.domainBindings[0].resolutionStatus").value("AVAILABLE"));

        mockMvc
            .perform(
                put("/api/modeling/warehouse-plans/{id}/baseline/categories", PLAN_ID)
                    .header("If-Match", "\"category-scope:4\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"domainBindings":[{"domainId":"20000000-0000-0000-0000-000000000001",
                          "confirmationStatus":"CONFIRMED"}]}
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"category-scope:5\""))
            .andExpect(jsonPath("$.data.version").value(5))
            .andExpect(jsonPath("$.data.value.domainBindings[0].name").value("Projects"));
    }

    @Test
    void getsCanonicalPolicyAndReturnsItsVersionInTheBody() throws Exception {
        PlanningPolicyView view = implementationReadyPolicyView();
        when(service.getPlanningPolicy("server-tenant", PLAN_ID)).thenReturn(new Versioned<>(view, 3));

        mockMvc
            .perform(get("/api/modeling/warehouse-plans/{id}/baseline/policy", PLAN_ID))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"policy:3\""))
            .andExpect(jsonPath("$.data.version").value(3))
            .andExpect(jsonPath("$.data.value.layerScheme").value("CLASSIC_ODS_DWD_DWS_ADS"))
            .andExpect(jsonPath("$.data.value.readiness").value("IMPLEMENTATION_READY"));
    }

    @Test
    void categoryWriteRequiresIfMatchAndReturnsStructuredConflictWithoutLosingTheDraftVersion() throws Exception {
        CategoryScopeCommand command = new CategoryScopeCommand(List.of(new DomainBinding(DOMAIN_ID, CONFIRMED)));
        when(service.saveCategoryScope("server-tenant", PLAN_ID, 1, command)).thenThrow(
            new WarehousePlanException("WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT", "changed", 6, CATEGORY_SCOPE)
        );

        String payload =
            "{\"domainBindings\":[{\"domainId\":\"20000000-0000-0000-0000-000000000001\",\"confirmationStatus\":\"CONFIRMED\"}]}";
        mockMvc
            .perform(
                put("/api/modeling/warehouse-plans/{id}/baseline/categories", PLAN_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
            .andExpect(status().isPreconditionRequired())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_PLAN_IF_MATCH_REQUIRED"));

        mockMvc
            .perform(
                put("/api/modeling/warehouse-plans/{id}/baseline/categories", PLAN_ID)
                    .header("If-Match", "\"category-scope:1\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
            .andExpect(status().isConflict())
            .andExpect(header().string("ETag", "\"category-scope:6\""))
            .andExpect(jsonPath("$.data.editUnit").value("CATEGORY_SCOPE"))
            .andExpect(jsonPath("$.data.currentVersion").value(6));
    }

    @Test
    void categoryForbiddenIsAStable403AndCanonicalPayloadRejectsLegacyFields() throws Exception {
        CategoryScopeCommand command = new CategoryScopeCommand(List.of(new DomainBinding(DOMAIN_ID, CONFIRMED)));
        when(service.saveCategoryScope("server-tenant", PLAN_ID, 1, command)).thenThrow(
            new WarehousePlanException("WAREHOUSE_PLAN_CATEGORY_FORBIDDEN", "forbidden", null, CATEGORY_SCOPE)
        );

        mockMvc
            .perform(
                put("/api/modeling/warehouse-plans/{id}/baseline/categories", PLAN_ID)
                    .header("If-Match", "\"category-scope:1\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"domainBindings\":[{\"domainId\":\"20000000-0000-0000-0000-000000000001\",\"confirmationStatus\":\"CONFIRMED\"}]}"
                    )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_PLAN_CATEGORY_FORBIDDEN"));

        mockMvc
            .perform(
                put("/api/modeling/warehouse-plans/{id}/baseline/categories", PLAN_ID)
                    .header("If-Match", "\"category-scope:1\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"domainBindings\":[],\"processBindings\":[]}")
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void returnsTheCanonicalNineStageProjectionForTheServerTenant() throws Exception {
        List<StageView> stages = Arrays.stream(StageCode.values())
            .map(stage ->
                new StageView(
                    stage,
                    StageStatus.NOT_STARTED,
                    EvidenceFreshness.UNAVAILABLE,
                    0,
                    stage.name() + "_NOT_STARTED",
                    "No completion evidence is available for this stage",
                    "Continue",
                    "/modeling/plans/" + PLAN_ID
                )
            )
            .toList();
        StageProjection projection = new StageProjection(
            PLAN_ID,
            StageCode.DATA_CONNECTION,
            new PrimaryBlocker(StageCode.DATA_CONNECTION, "DATA_CONNECTION_NOT_STARTED", "Connection evidence missing"),
            new NextAction("Check data connections", "/data-development/integration/data-sources"),
            stages,
            Instant.parse("2026-07-18T00:00:00Z")
        );
        AccessContext actorContext = new AccessContext("server-tenant", "owner-1", "department-1");
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("owner-1", "department-1"));
        when(stageProjectionService.project("server-tenant", PLAN_ID, actorContext)).thenReturn(projection);

        mockMvc
            .perform(get("/api/modeling/warehouse-plans/{id}/stage-projection", PLAN_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentStage").value("DATA_CONNECTION"))
            .andExpect(jsonPath("$.data.stages.length()").value(9));

        verify(stageProjectionService).project("server-tenant", PLAN_ID, actorContext);
    }

    private static WarehousePlanHeader planHeader(
        int version,
        com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus status
    ) {
        return new WarehousePlanHeader(
            PLAN_ID,
            "server-tenant",
            "warehouse-neutral",
            "Neutral warehouse",
            "Trusted metrics",
            "Initial scope",
            "owner-1",
            "department-1",
            BUSINESS_FIRST,
            status,
            version
        );
    }

    private static BusinessScope scope() {
        return new BusinessScope(
            true,
            List.of(new DomainBinding(DOMAIN_ID, CONFIRMED)),
            List.of(new ProcessBinding("process-1", DOMAIN_ID, "ACCUMULATING_SNAPSHOT", CONFIRMED)),
            List.of()
        );
    }

    private static List<SourceBinding> sources() {
        return List.of(new SourceBinding(SOURCE_ID, CATALOG_TABLE, "asset-1", "schema-v1", CONFIRMED, null));
    }

    private static List<SourceBusinessMapping> mappings() {
        return List.of(
            new SourceBusinessMapping(
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                SOURCE_ID,
                DOMAIN_ID,
                "process-1",
                CONFIRMED,
                "confirmed"
            )
        );
    }

    private static PlanningPolicyCommand policyCommand() {
        return new PlanningPolicyCommand(
            "CLASSIC_ODS_DWD_DWS_ADS",
            "CLASSIC_LOWER_SNAKE",
            "PRESERVE_BUSINESS_HISTORY",
            "Asia/Shanghai"
        );
    }

    private static PlanningPolicyView implementationReadyPolicyView() {
        return new PlanningPolicyView(
            WarehousePlanContract.LayerScheme.CLASSIC_ODS_DWD_DWS_ADS,
            WarehousePlanContract.NamingPolicy.CLASSIC_LOWER_SNAKE,
            WarehousePlanContract.HistoryPolicy.PRESERVE_BUSINESS_HISTORY,
            "Asia/Shanghai",
            PlanningPolicyReadiness.IMPLEMENTATION_READY,
            List.of()
        );
    }

    private static CategoryScopeView readyCategoryScope() {
        Instant validatedAt = Instant.parse("2026-07-19T00:00:00Z");
        return new CategoryScopeView(
            List.of(
                new CategoryBindingView(
                    DOMAIN_ID,
                    CONFIRMED,
                    CatalogDomainResolutionPort.ResolutionStatus.AVAILABLE,
                    "Projects",
                    "PROJECT",
                    validatedAt
                )
            ),
            CategoryReadiness.READY,
            List.of(),
            validatedAt
        );
    }
}
