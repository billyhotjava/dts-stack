package com.yuzhi.dts.platform.web.rest;

import static com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolutionStatus.AVAILABLE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.CONFIRMED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.EditUnit.SOURCES;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceFreshness.CURRENT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryReadiness.READY;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType.CATALOG_TABLE;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WarehousePlanSourceInventoryResourceTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BINDING_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ASSET_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant CHECKED_AT = Instant.parse("2026-07-19T01:00:00Z");

    private WarehousePlanApplicationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(WarehousePlanApplicationService.class);
        WarehousePlanStageProjectionService stageProjectionService = mock(WarehousePlanStageProjectionService.class);
        WarehousePlanActorProvider actorProvider = mock(WarehousePlanActorProvider.class);
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("owner-1", "department-1"));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        WarehousePlanResource resource = new WarehousePlanResource(
            service,
            stageProjectionService,
            actorProvider,
            objectMapper,
            "server-tenant"
        );
        mockMvc = MockMvcBuilders.standaloneSetup(resource)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    void getsAndSavesAggregateSourceInventoryWithServerEtag() throws Exception {
        SourceInventoryView current = inventory(4);
        SourceInventoryView saved = inventory(5);
        AccessContext access = new AccessContext("server-tenant", "owner-1", "department-1");
        SourceInventoryCommand command = new SourceInventoryCommand(
            List.of(
                new SourceBindingCommand(
                    BINDING_ID,
                    CATALOG_TABLE,
                    new SourceLocator(ASSET_ID, null, null, null, null, null, null),
                    CONFIRMED,
                    null
                )
            )
        );
        when(service.getSources("server-tenant", PLAN_ID, access)).thenReturn(current);
        when(service.saveSources("server-tenant", PLAN_ID, 4, command, access)).thenReturn(saved);

        mockMvc
            .perform(get("/api/modeling/warehouse-plans/{id}/baseline/sources", PLAN_ID))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"sources:4\""))
            .andExpect(jsonPath("$.data.version").value(4))
            .andExpect(jsonPath("$.data.readiness").value("READY"))
            .andExpect(jsonPath("$.data.checkedAt").value(CHECKED_AT.toString()))
            .andExpect(jsonPath("$.data.bindings[0].resolvedVersion").value("schema-v1"));

        mockMvc
            .perform(
                put("/api/modeling/warehouse-plans/{id}/baseline/sources", PLAN_ID)
                    .header("If-Match", "\"sources:4\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"bindings":[{"bindingId":"30000000-0000-0000-0000-000000000001",
                          "sourceType":"CATALOG_TABLE",
                          "locator":{"assetId":"40000000-0000-0000-0000-000000000001"},
                          "confirmationStatus":"CONFIRMED","exclusionReason":null}]}
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"sources:5\""))
            .andExpect(jsonPath("$.data.version").value(5));

        verify(service).getSources("server-tenant", PLAN_ID, access);
        verify(service).saveSources("server-tenant", PLAN_ID, 4, command, access);
    }

    @Test
    void rejectsMissingIfMatchAndClientForgedResolutionFactsBeforeCallingService() throws Exception {
        String valid =
            """
            {"bindings":[{"sourceType":"CATALOG_TABLE",
              "locator":{"assetId":"40000000-0000-0000-0000-000000000001"},
              "confirmationStatus":"CONFIRMED"}]}
            """;
        mockMvc
            .perform(
                put("/api/modeling/warehouse-plans/{id}/baseline/sources", PLAN_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(valid)
            )
            .andExpect(status().isPreconditionRequired());

        mockMvc
            .perform(
                put("/api/modeling/warehouse-plans/{id}/baseline/sources", PLAN_ID)
                    .header("If-Match", "\"sources:1\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        valid.replace(
                            "\"confirmationStatus\":\"CONFIRMED\"",
                            "\"confirmationStatus\":\"CONFIRMED\",\"resolvedVersion\":\"forged\",\"freshness\":\"CURRENT\""
                        )
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_PLAN_REQUEST_INVALID"));
        verifyNoMoreInteractions(service);
    }

    @Test
    void returnsCurrentSourcesVersionOnCasConflict() throws Exception {
        AccessContext access = new AccessContext("server-tenant", "owner-1", "department-1");
        SourceInventoryCommand command = new SourceInventoryCommand(
            List.of(
                new SourceBindingCommand(
                    null,
                    CATALOG_TABLE,
                    new SourceLocator(ASSET_ID, null, null, null, null, null, null),
                    CONFIRMED,
                    null
                )
            )
        );
        when(service.saveSources("server-tenant", PLAN_ID, 1, command, access)).thenThrow(
            new WarehousePlanException("WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT", "changed", 6, SOURCES)
        );

        mockMvc
            .perform(
                put("/api/modeling/warehouse-plans/{id}/baseline/sources", PLAN_ID)
                    .header("If-Match", "\"sources:1\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"bindings":[{"sourceType":"CATALOG_TABLE",
                          "locator":{"assetId":"40000000-0000-0000-0000-000000000001"},
                          "confirmationStatus":"CONFIRMED"}]}
                        """
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(header().string("ETag", "\"sources:6\""))
            .andExpect(jsonPath("$.data.editUnit").value("SOURCES"))
            .andExpect(jsonPath("$.data.currentVersion").value(6));
    }

    private static SourceInventoryView inventory(int version) {
        return new SourceInventoryView(
            List.of(
                new SourceBindingView(
                    BINDING_ID,
                    CATALOG_TABLE,
                    new SourceLocator(ASSET_ID, null, null, null, null, null, null),
                    ASSET_ID.toString(),
                    CONFIRMED,
                    null,
                    "Orders",
                    "schema-v1",
                    "schema-v1",
                    AVAILABLE,
                    CURRENT,
                    CHECKED_AT
                )
            ),
            READY,
            List.of(),
            version,
            "\"sources:" + version + "\"",
            CHECKED_AT
        );
    }
}
