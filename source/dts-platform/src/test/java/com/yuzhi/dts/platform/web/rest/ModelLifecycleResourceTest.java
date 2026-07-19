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
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.CompileView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
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

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelLifecycleService service;

    @MockBean
    private WarehousePlanActorProvider actorProvider;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @Test
    void compileUsesServerTenantActorAndStrongRevisionPrecondition() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        when(service.compile(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), eq("compile-7")))
            .thenReturn(mock(CompileView.class));

        mockMvc.perform(
            post("/api/modeling/model-specs/{id}/lifecycle/compile", MODEL_ID)
                .header("X-Tenant-Id", "request-tenant-must-not-win")
                .header("If-Match", ETAG)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"compile-7\"}")
        ).andExpect(status().isOk());

        verify(service).compile(eq("server-tenant"), eq("alice"), eq(MODEL_ID), any(), eq("compile-7"));
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
}
