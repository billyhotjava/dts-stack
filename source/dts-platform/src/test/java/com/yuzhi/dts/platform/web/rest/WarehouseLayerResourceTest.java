package com.yuzhi.dts.platform.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.CreateWarehouseLayerCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.LayerGroup;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.WarehouseLayerView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
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
    value = WarehouseLayerResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
    }
)
@AutoConfigureMockMvc(addFilters = false)
class WarehouseLayerResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WarehouseLayerApplicationService service;

    @MockBean
    private WarehousePlanActorProvider actorProvider;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private com.yuzhi.dts.platform.web.filter.DbtImplementationDraftBodyLimitFilter dbtImplementationDraftBodyLimitFilter;

    @MockBean
    private com.yuzhi.dts.platform.web.filter.DimensionModelBodyLimitFilter dimensionModelBodyLimitFilter;

    @MockBean
    private com.yuzhi.dts.platform.service.audit.AuditService auditService;

    @MockBean
    private com.yuzhi.dts.platform.web.filter.AuditLoggingFilter auditLoggingFilter;

    @Test
    void listsMergedWarehouseLayers() throws Exception {
        when(service.list()).thenReturn(
            List.of(new WarehouseLayerView(
                "DWD", "明细事实 / 维度层", "DWD", "DETAIL", "业务明细", List.of("dwd_"), false, true, false,
                "平台内置分层不可删除", LayerGroup.COMMON, List.of(com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType.DIMENSION)
            ))
        );

        mockMvc
            .perform(get("/api/modeling/warehouse-layers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("DWD"))
            .andExpect(jsonPath("$.data[0].builtin").value(true))
            .andExpect(jsonPath("$.data[0].layerGroup").value("COMMON"));
    }

    @Test
    void createsCustomLayerAndReturnsLocation() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        when(service.create(eq("alice"), any(CreateWarehouseLayerCommand.class)))
            .thenReturn(new WarehouseLayerView(
                "FIN_DETAIL", "财务明细层", "DWD", "DETAIL", "财务域明细", List.of("fin_dwd_"), false, false, true,
                null, LayerGroup.COMMON, List.of(com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType.DIMENSION, com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType.FACT)
            ));

        mockMvc
            .perform(post("/api/modeling/warehouse-layers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"FIN_DETAIL","name":"财务明细层","systemLayerCode":"DWD",
                     "description":"财务域明细","namingPrefix":"fin_dwd_"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/modeling/warehouse-layers/FIN_DETAIL"))
            .andExpect(jsonPath("$.data.code").value("FIN_DETAIL"))
            .andExpect(jsonPath("$.data.builtin").value(false))
            .andExpect(jsonPath("$.data.layerGroup").value("COMMON"));
    }

    @Test
    void rejectsDeletingBuiltInLayerWithStableConflictCode() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        org.mockito.Mockito.doThrow(
            new WarehouseLayerException("WAREHOUSE_LAYER_BUILTIN_PROTECTED", "平台内置分层不可删除: DWD", WarehouseLayerException.Kind.CONFLICT, Map.of("code", "DWD"))
        ).when(service).delete("alice", "DWD");

        mockMvc
            .perform(delete("/api/modeling/warehouse-layers/DWD"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_LAYER_BUILTIN_PROTECTED"));
    }

    @Test
    void rejectsInUseDeleteAndPropagatesReferenceCount() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        org.mockito.Mockito.doThrow(
            new WarehouseLayerException("WAREHOUSE_LAYER_IN_USE", "存在 2 个活动模型引用该分层，不能删除", WarehouseLayerException.Kind.CONFLICT, Map.of("referenceCount", 2))
        ).when(service).delete("alice", "FIN_DETAIL");

        mockMvc
            .perform(delete("/api/modeling/warehouse-layers/FIN_DETAIL"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_LAYER_IN_USE"))
            .andExpect(jsonPath("$.data.referenceCount").value(2));
    }

    @Test
    void deletesCustomLayerAndPropagatesActor() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));

        mockMvc.perform(delete("/api/modeling/warehouse-layers/FIN_DETAIL")).andExpect(status().isNoContent());

        verify(service).delete("alice", "FIN_DETAIL");
    }

    @Test
    void mapsValidationErrorsToStableHttpStatuses() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        org.mockito.Mockito.doThrow(
            new WarehouseLayerException("WAREHOUSE_LAYER_CODE_INVALID", "分层编码格式不合法", WarehouseLayerException.Kind.BAD_REQUEST, Map.of("field", "code"))
        ).when(service).create(eq("alice"), any(CreateWarehouseLayerCommand.class));

        mockMvc
            .perform(post("/api/modeling/warehouse-layers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"9BAD\",\"name\":\"x\",\"systemLayerCode\":\"DWD\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_LAYER_CODE_INVALID"));
    }

    @Test
    void mapsMissingLayerToNotFound() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        org.mockito.Mockito.doThrow(
            new WarehouseLayerException("WAREHOUSE_LAYER_NOT_FOUND", "自定义分层不存在或已删除: GONE", WarehouseLayerException.Kind.NOT_FOUND, Map.of("code", "GONE"))
        ).when(service).delete("alice", "GONE");

        mockMvc
            .perform(delete("/api/modeling/warehouse-layers/GONE"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_LAYER_NOT_FOUND"));
    }
}
