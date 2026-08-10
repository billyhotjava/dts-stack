package com.yuzhi.dts.platform.web.rest;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryReadiness.READY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanAuthorizationGuard;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class Sprint89WarehousePlanSourceResourceTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000089");
    private WarehousePlanApplicationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(WarehousePlanApplicationService.class);
        WarehousePlanActorProvider actorProvider = mock(WarehousePlanActorProvider.class);
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("owner-89", "department-89"));
        WarehousePlanAuthorizationGuard guard = mock(WarehousePlanAuthorizationGuard.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        WarehousePlanResource resource = new WarehousePlanResource(
            service,
            mock(WarehousePlanStageProjectionService.class),
            mock(WarehousePlanRelationshipGraphService.class),
            actorProvider,
            guard,
            objectMapper,
            "server-tenant"
        );
        mockMvc = MockMvcBuilders.standaloneSetup(resource)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    void exposesBoundedPaginationAndRejectsOversizedPages() throws Exception {
        AccessContext access = new AccessContext("server-tenant", "owner-89", "department-89");
        SourceInventoryView page = new SourceInventoryView(
            List.of(),
            READY,
            List.of(),
            4,
            "\"sources:4\"",
            Instant.parse("2026-08-10T00:00:00Z"),
            1,
            200,
            201,
            2
        );
        when(service.getSources("server-tenant", PLAN_ID, access, 1, 200, true)).thenReturn(page);

        mockMvc
            .perform(get("/api/modeling/warehouse-plans/{id}/baseline/sources", PLAN_ID).param("page", "1").param("size", "200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(200))
            .andExpect(jsonPath("$.data.totalElements").value(201))
            .andExpect(jsonPath("$.data.totalPages").value(2));
        verify(service).getSources("server-tenant", PLAN_ID, access, 1, 200, true);

        mockMvc
            .perform(get("/api/modeling/warehouse-plans/{id}/baseline/sources", PLAN_ID).param("page", "0").param("size", "201"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_PLAN_SOURCE_PAGE_INVALID"));
    }
}
