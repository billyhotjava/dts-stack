package com.yuzhi.dts.admin.web.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import com.yuzhi.dts.admin.service.infra.InfraAdminService;
import com.yuzhi.dts.admin.service.infra.dto.InfraFeatureFlags;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminInfraResource.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminInfraResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InfraAdminService infraAdminService;

    @MockBean
    private AuditV2Service auditV2Service;

    @Test
    void shouldReturnInceptorFlags() throws Exception {
        InfraFeatureFlags flags = new InfraFeatureFlags();
        flags.setMultiSourceEnabled(true);
        flags.setInceptorStatus("ACTIVE");
        when(infraAdminService.computeFeatureFlags()).thenReturn(flags);

        mockMvc.perform(get("/api/admin/infra/inceptor/flags"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.multiSourceEnabled").value(true))
            .andExpect(jsonPath("$.data.inceptorStatus").value("ACTIVE"));
    }
}
