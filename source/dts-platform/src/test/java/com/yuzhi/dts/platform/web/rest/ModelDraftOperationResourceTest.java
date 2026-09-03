package com.yuzhi.dts.platform.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelDraftSaveApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecCreateRequestDecoder;
import com.yuzhi.dts.platform.service.modeling.ModelSpecUpdateRequestDecoder;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRejectionAudit;
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
    value = ModelDraftOperationResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
        "dts.platform.modeling.default-tenant-id=server-tenant",
    }
)
@AutoConfigureMockMvc(addFilters = false)
class ModelDraftOperationResourceTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelDraftSaveApplicationService service;

    @MockBean
    private ModelSpecCreateRequestDecoder createDecoder;

    @MockBean
    private ModelSpecUpdateRequestDecoder updateDecoder;

    @MockBean
    private WarehousePlanActorProvider actorProvider;

    @MockBean
    private DbtImplementationDraftRejectionAudit dbtImplementationDraftRejectionAudit;

    @MockBean
    private AuditService auditService;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @Test
    void savesTheWholeInitialDraftThroughOneApplicationCommand() throws Exception {
        CreateModelSpecCommand create = mock(CreateModelSpecCommand.class);
        UpdateModelSpecCommand update = mock(UpdateModelSpecCommand.class);
        ModelSpecView model = mock(ModelSpecView.class);
        ImplementationView implementation = mock(ImplementationView.class);
        when(create.idempotencyKey()).thenReturn("manual-create-1");
        when(model.id()).thenReturn(MODEL_ID);
        when(model.revision()).thenReturn(2);
        when(model.checksum()).thenReturn("a".repeat(64));
        when(createDecoder.decode(any())).thenReturn(new ModelSpecCreateRequestDecoder.DecodeResult(create, List.of()));
        when(updateDecoder.decode(any())).thenReturn(new ModelSpecUpdateRequestDecoder.DecodeResult(update, List.of()));
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        when(service.save(eq("server-tenant"), eq("alice"), eq(create), eq(update), any())).thenReturn(
            new ModelDraftSaveApplicationService.SaveResult(model, implementation, false)
        );

        mockMvc.perform(
            post("/api/modeling/model-specs/draft-operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "create": {},
                      "modelSpec": {},
                      "implementation": {
                        "inputMode": "PHYSICAL_ASSET",
                        "inputs": [{"sourceBindingId":"50000000-0000-0000-0000-000000000001","resolvedVersion":"source-v1"}],
                        "settings": {},
                        "ownership": "DESIGNER_GENERATED",
                        "materialization": "table",
                        "idempotencyKey": "implementation-1"
                      }
                    }
                    """
                )
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.model.id").value(MODEL_ID.toString()))
            .andExpect(jsonPath("$.data.replayed").value(false));

        verify(service).save(eq("server-tenant"), eq("alice"), eq(create), eq(update), any());
    }
}
