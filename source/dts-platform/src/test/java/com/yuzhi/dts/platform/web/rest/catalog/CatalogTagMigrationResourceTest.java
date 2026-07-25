package com.yuzhi.dts.platform.web.rest.catalog;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.session.PortalSessionActivityService;
import com.yuzhi.dts.platform.security.session.PortalSessionCookieService;
import com.yuzhi.dts.platform.service.audit.AuditFlowManager;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogTagGovernanceGuard;
import com.yuzhi.dts.platform.service.catalog.CatalogTagMigrationConflictException;
import com.yuzhi.dts.platform.service.catalog.CatalogTagMigrationService;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagMigrationExecution;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagMigrationReport;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagMigrationRollback;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = CatalogTagMigrationResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration"
    }
)
@AutoConfigureMockMvc
@Import({ CatalogTagGovernanceGuard.class, CatalogTagMigrationResourceTest.SecurityConfig.class })
class CatalogTagMigrationResourceTest {

    private static final String CHECKSUM = "a".repeat(64);
    private static final String BATCH_ID = "catalog-tags-" + "a".repeat(24);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogTagMigrationService service;

    @MockBean
    private AuditService audit;

    @MockBean
    private AuditFlowManager auditFlowManager;

    @MockBean
    private PortalSessionActivityService portalSessionActivityService;

    @MockBean
    private PortalSessionCookieService portalSessionCookieService;

    @Test
    @WithMockUser(username = "owner", authorities = "ROLE_INST_DATA_OWNER")
    void maintainerDryRunReturnsReportAndWritesExactlyOneCanonicalSuccessAudit() throws Exception {
        when(service.dryRun()).thenReturn(report());

        mockMvc
            .perform(post("/api/catalog/tag-migrations/dry-run"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batchId").value(BATCH_ID))
            .andExpect(jsonPath("$.data.checksum").value(CHECKSUM));

        verify(service).dryRun();
        verify(audit)
            .auditAction(
                eq("CATALOG_TAG_MIGRATION_DRY_RUN"),
                eq(AuditStage.SUCCESS),
                eq(BATCH_ID),
                argThat(payload -> hasAllCountFields(payload, CHECKSUM, false))
            );
        verifyNoMoreInteractions(audit);
    }

    @Test
    @WithMockUser(username = "employee", authorities = "ROLE_EMPLOYEE")
    void ordinaryUserIsRejectedByTheRealGovernanceGuardAndDenialIsCanonicallyAudited() throws Exception {
        mockMvc
            .perform(post("/api/catalog/tag-migrations/dry-run"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(service, never()).dryRun();
        verify(audit)
            .auditAction(
                eq("CATALOG_TAG_MIGRATION_DRY_RUN"),
                eq(AuditStage.FAIL),
                eq("dry-run"),
                argThat(payload ->
                    payload instanceof Map<?, ?> map &&
                    "FORBIDDEN".equals(map.get("reasonCode")) &&
                    hasAllCountFields(payload, null, false)
                )
            );
        verifyNoMoreInteractions(audit);
    }

    @Test
    @WithMockUser(username = "owner", authorities = "ROLE_INST_DATA_OWNER")
    void executeAndRollbackRequireStrictChecksumBodiesAndAuditFullCounts() throws Exception {
        when(service.execute(BATCH_ID, CHECKSUM, "owner")).thenReturn(execution());
        when(service.rollback(BATCH_ID, CHECKSUM, "owner")).thenReturn(rollback());

        mockMvc
            .perform(
                post("/api/catalog/tag-migrations/{batchId}/execute", BATCH_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedChecksum\":\"" + CHECKSUM + "\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.created").value(2))
            .andExpect(jsonPath("$.data.replayed").value(false));
        mockMvc
            .perform(
                post("/api/catalog/tag-migrations/{batchId}/rollback", BATCH_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedChecksum\":\"" + CHECKSUM + "\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deleted").value(2))
            .andExpect(jsonPath("$.data.replayed").value(false));

        verify(audit)
            .auditAction(
                eq("CATALOG_TAG_MIGRATION_EXECUTE"),
                eq(AuditStage.SUCCESS),
                eq(BATCH_ID),
                argThat(payload -> hasAllCountFields(payload, CHECKSUM, false))
            );
        verify(audit)
            .auditAction(
                eq("CATALOG_TAG_MIGRATION_ROLLBACK"),
                eq(AuditStage.SUCCESS),
                eq(BATCH_ID),
                argThat(payload ->
                    payload instanceof Map<?, ?> map &&
                    Long.valueOf(2).equals(map.get("deleted")) &&
                    hasAllCountFields(payload, CHECKSUM, false)
                )
            );
        verifyNoMoreInteractions(audit);
    }

    @Test
    @WithMockUser(username = "owner", authorities = "ROLE_INST_DATA_OWNER")
    void malformedChecksumAndUnexpectedBodyFieldReturnBadRequestAndOneFailAuditEach() throws Exception {
        mockMvc
            .perform(
                post("/api/catalog/tag-migrations/{batchId}/execute", BATCH_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedChecksum\":\"ABC\",\"force\":true}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MIGRATION_REQUEST_INVALID"));

        verify(service, never()).execute(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        );
        verify(audit)
            .auditAction(
                eq("CATALOG_TAG_MIGRATION_EXECUTE"),
                eq(AuditStage.FAIL),
                eq(BATCH_ID),
                argThat(payload ->
                    payload instanceof Map<?, ?> map &&
                    "MIGRATION_REQUEST_INVALID".equals(map.get("reasonCode")) &&
                    hasAllCountFields(payload, null, false)
                )
            );
        verifyNoMoreInteractions(audit);
    }

    @Test
    @WithMockUser(username = "owner", authorities = "ROLE_INST_DATA_OWNER")
    void servicePlanConflictReturns409AndOneCanonicalFailAudit() throws Exception {
        when(service.execute(BATCH_ID, CHECKSUM, "owner")).thenThrow(
            new CatalogTagMigrationConflictException("MIGRATION_PLAN_DRIFT: source changed")
        );

        mockMvc
            .perform(
                post("/api/catalog/tag-migrations/{batchId}/execute", BATCH_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedChecksum\":\"" + CHECKSUM + "\"}")
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MIGRATION_GATE_BLOCKED"));

        verify(audit)
            .auditAction(
                eq("CATALOG_TAG_MIGRATION_EXECUTE"),
                eq(AuditStage.FAIL),
                eq(BATCH_ID),
                argThat(payload ->
                    payload instanceof Map<?, ?> map &&
                    "MIGRATION_GATE_BLOCKED".equals(map.get("reasonCode")) &&
                    CHECKSUM.equals(map.get("checksum")) &&
                    hasAllCountFields(payload, CHECKSUM, false)
                )
            );
        verifyNoMoreInteractions(audit);
    }

    @Test
    @WithMockUser(username = "employee", authorities = "ROLE_EMPLOYEE")
    void failAuditOutageDoesNotMaskTheOriginalForbiddenResponse() throws Exception {
        doThrow(new IllegalStateException("audit unavailable"))
            .when(audit)
            .auditAction(
                eq("CATALOG_TAG_MIGRATION_DRY_RUN"),
                eq(AuditStage.FAIL),
                eq("dry-run"),
                org.mockito.ArgumentMatchers.any()
            );

        mockMvc
            .perform(post("/api/catalog/tag-migrations/dry-run"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(service, never()).dryRun();
    }

    @Test
    @WithMockUser(username = "owner", authorities = "ROLE_INST_DATA_OWNER")
    void failAuditOutageDoesNotMaskBadRequestOrConflictResponses() throws Exception {
        doThrow(new IllegalStateException("audit unavailable"))
            .when(audit)
            .auditAction(
                org.mockito.ArgumentMatchers.anyString(),
                eq(AuditStage.FAIL),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
            );

        mockMvc
            .perform(
                post("/api/catalog/tag-migrations/{batchId}/execute", BATCH_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedChecksum\":\"ABC\",\"force\":true}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MIGRATION_REQUEST_INVALID"));

        when(service.execute(BATCH_ID, CHECKSUM, "owner")).thenThrow(
            new CatalogTagMigrationConflictException("MIGRATION_PLAN_DRIFT: source changed")
        );
        mockMvc
            .perform(
                post("/api/catalog/tag-migrations/{batchId}/execute", BATCH_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedChecksum\":\"" + CHECKSUM + "\"}")
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MIGRATION_GATE_BLOCKED"));
    }

    @Test
    @WithMockUser(username = "owner", authorities = "ROLE_INST_DATA_OWNER")
    void successAuditOutageDoesNotChangeExecuteOrRollbackResponses() throws Exception {
        when(service.execute(BATCH_ID, CHECKSUM, "owner")).thenReturn(execution());
        when(service.rollback(BATCH_ID, CHECKSUM, "owner")).thenReturn(rollback());
        doThrow(new IllegalStateException("audit unavailable"))
            .when(audit)
            .auditAction(
                eq("CATALOG_TAG_MIGRATION_EXECUTE"),
                eq(AuditStage.SUCCESS),
                eq(BATCH_ID),
                org.mockito.ArgumentMatchers.any()
            );
        doThrow(new IllegalStateException("audit unavailable"))
            .when(audit)
            .auditAction(
                eq("CATALOG_TAG_MIGRATION_ROLLBACK"),
                eq(AuditStage.SUCCESS),
                eq(BATCH_ID),
                org.mockito.ArgumentMatchers.any()
            );

        mockMvc
            .perform(
                post("/api/catalog/tag-migrations/{batchId}/execute", BATCH_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedChecksum\":\"" + CHECKSUM + "\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("EXECUTED"));

        mockMvc
            .perform(
                post("/api/catalog/tag-migrations/{batchId}/rollback", BATCH_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedChecksum\":\"" + CHECKSUM + "\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ROLLED_BACK"));
    }

    private CatalogTagMigrationReport report() {
        return new CatalogTagMigrationReport(
            BATCH_ID,
            CHECKSUM,
            3,
            3,
            4,
            2,
            List.of(),
            List.of(),
            List.of(),
            2,
            0,
            List.of()
        );
    }

    private CatalogTagMigrationExecution execution() {
        return new CatalogTagMigrationExecution(
            BATCH_ID,
            CHECKSUM,
            "EXECUTED",
            false,
            2,
            1,
            0,
            1,
            2,
            0
        );
    }

    private CatalogTagMigrationRollback rollback() {
        return new CatalogTagMigrationRollback(
            BATCH_ID,
            CHECKSUM,
            "ROLLED_BACK",
            false,
            2,
            1,
            0,
            1,
            2,
            0,
            2
        );
    }

    private boolean hasAllCountFields(Object payload, String checksum, boolean replayed) {
        if (!(payload instanceof Map<?, ?> map)) {
            return false;
        }
        return (
            (checksum == null || checksum.equals(map.get("checksum"))) &&
            map.containsKey("matched") &&
            map.containsKey("unmatched") &&
            map.containsKey("ambiguous") &&
            map.containsKey("protected") &&
            map.containsKey("created") &&
            map.containsKey("skipped") &&
            map.containsKey("deleted") &&
            Boolean.valueOf(replayed).equals(map.get("replayed"))
        );
    }

    @TestConfiguration
    static class SecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
            return http.build();
        }

        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(
                User.withUsername("user").password("{noop}password").authorities("ROLE_USER").build()
            );
        }
    }
}
