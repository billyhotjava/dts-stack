package com.yuzhi.dts.platform.web.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService;
import com.yuzhi.dts.platform.service.modeling.migration.LegacyObjectMigrationService;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = SemanticModelingResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = { "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration" }
)
@AutoConfigureMockMvc(addFilters = false)
class SemanticModelingRetirementResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SemanticModelingService semanticModelingService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private PlatformEventOutboxService platformEventOutboxService;

    @MockBean
    private LegacyObjectMigrationService legacyObjectMigrationService;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @BeforeEach
    void configureLegacyRetirementContract() {
        when(legacyObjectMigrationService.defaultTenantId()).thenReturn("default");
    }

    @Test
    void rejectsSemanticBusinessObjectWritesWithoutRecordingThePayload() throws Exception {
        when(legacyObjectMigrationService.writeFrozen()).thenReturn(true);

        mockMvc
            .perform(post("/api/semantic/business-objects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"sensitive object\"}"))
            .andExpect(status().isGone())
            .andExpect(header().string("Deprecation", "true"))
            .andExpect(header().string("Link", "</api/modeling/model-specs>; rel=\"successor-version\""))
            .andExpect(jsonPath("$.code").value("BUSINESS_OBJECT_RETIRED"));
    }

    @Test
    void rejectsRetiredSubjectDomainWritesAtTheSameBoundary() throws Exception {
        when(legacyObjectMigrationService.writeFrozen()).thenReturn(true);

        mockMvc
            .perform(post("/api/semantic/subject-domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("BUSINESS_OBJECT_RETIRED"));
    }
}
