package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionApplicationService;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ReuseScope;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.Status;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
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
    value = DimensionDefinitionResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
        "dts.platform.modeling.default-tenant-id=server-tenant",
    }
)
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.context.annotation.Import(DimensionDefinitionResourceTest.MenuSecurity.class)
@org.springframework.security.test.context.support.WithMockUser(username = "alice", roles = "EMPLOYEE")
class DimensionDefinitionResourceTest {

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class MenuSecurity {}

    @Autowired
    private DimensionDefinitionResource securedResource;

    @MockBean
    private com.yuzhi.dts.platform.service.audit.AuditService auditService;

    @MockBean
    private com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRejectionAudit dbtImplementationDraftRejectionAudit;

    @Test
    @org.springframework.security.test.context.support.WithAnonymousUser
    void anonymousCannotCreateBusinessDimensions() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> securedResource.create(null))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private static final UUID DEFINITION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String CHECKSUM = "a".repeat(64);
    private static final String ETAG =
        "\"dimension-definition:" + DEFINITION_ID + ":1:" + CHECKSUM + "\"";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DimensionDefinitionApplicationService service;

    @MockBean
    private WarehousePlanActorProvider actorProvider;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @Test
    void exposesCreateListGetUpdateConfirmAndRetireWithServerContextAndStrongEtags() throws Exception {
        View draft = view(Status.DRAFT, 1, CHECKSUM);
        View current = view(Status.CURRENT, 2, "b".repeat(64));
        View retired = view(Status.RETIRED, 3, "c".repeat(64));
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        when(service.create(eq("server-tenant"), eq("alice"), any())).thenReturn(new CreateResult(draft, false));
        when(service.list("server-tenant", null, null, null, 0, 50)).thenReturn(List.of(draft));
        when(service.get("server-tenant", DEFINITION_ID)).thenReturn(draft);
        when(service.update(eq("server-tenant"), eq("alice"), eq(DEFINITION_ID), any(), any())).thenReturn(draft);
        when(service.confirm(eq("server-tenant"), eq("alice"), eq(DEFINITION_ID), any())).thenReturn(current);
        when(service.retire(eq("server-tenant"), eq("alice"), eq(DEFINITION_ID), any())).thenReturn(retired);

        mockMvc
            .perform(
                post("/api/modeling/dimension-definitions")
                    .header("X-Tenant-Id", "request-tenant-must-not-win")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createJson())
            )
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/modeling/dimension-definitions/" + DEFINITION_ID))
            .andExpect(header().string("ETag", ETAG))
            .andExpect(jsonPath("$.data.systemCode").value("dim_30000000000000000000000000000001"));

        mockMvc
            .perform(get("/api/modeling/dimension-definitions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(DEFINITION_ID.toString()));

        mockMvc
            .perform(get("/api/modeling/dimension-definitions/{id}", DEFINITION_ID))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG));

        mockMvc
            .perform(
                put("/api/modeling/dimension-definitions/{id}", DEFINITION_ID)
                    .header("If-Match", ETAG)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateJson())
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG));

        mockMvc
            .perform(post("/api/modeling/dimension-definitions/{id}/confirm", DEFINITION_ID).header("If-Match", ETAG))
            .andExpect(status().isOk())
            .andExpect(header().string(
                "ETag",
                "\"dimension-definition:" + DEFINITION_ID + ":2:" + "b".repeat(64) + "\""
            ));

        mockMvc
            .perform(
                post("/api/modeling/dimension-definitions/{id}/retire", DEFINITION_ID)
                    .header(
                        "If-Match",
                        "\"dimension-definition:" + DEFINITION_ID + ":2:" + "b".repeat(64) + "\""
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("RETIRED"));

        verify(service).create(eq("server-tenant"), eq("alice"), any());
        verify(service).list("server-tenant", null, null, null, 0, 50);
        verify(service).get("server-tenant", DEFINITION_ID);

        mockMvc
            .perform(delete("/api/modeling/dimension-definitions/{id}", DEFINITION_ID).header("If-Match", ETAG))
            .andExpect(status().isOk());
        verify(service).delete(eq("server-tenant"), eq("alice"), eq(DEFINITION_ID), any());
    }

    @Test
    void returnsOkForIdempotentCreateReplay() throws Exception {
        View draft = view(Status.DRAFT, 1, CHECKSUM);
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        when(service.create(eq("server-tenant"), eq("alice"), any())).thenReturn(new CreateResult(draft, true));

        mockMvc
            .perform(
                post("/api/modeling/dimension-definitions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createJson())
            )
            .andExpect(status().isOk())
            .andExpect(header().string("Location", "/api/modeling/dimension-definitions/" + DEFINITION_ID))
            .andExpect(header().string("ETag", ETAG));
    }

    @Test
    void rejectsClientSuppliedSystemCodeImplementationFieldsAndMalformedValues() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));

        mockMvc
            .perform(
                post("/api/modeling/dimension-definitions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createJson().replace("\"domainId\"", "\"systemCode\":\"DIM_MANUAL\",\"domainId\""))
            )
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("DIMENSION_DEFINITION_REQUEST_INVALID"));

        mockMvc
            .perform(
                post("/api/modeling/dimension-definitions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createJson().replace("\"hierarchies\"", "\"sourceRefs\":[],\"hierarchies\""))
            )
            .andExpect(status().isUnprocessableEntity());

        mockMvc
            .perform(
                post("/api/modeling/dimension-definitions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        createJson()
                            .replace(
                                "\"hierarchies\":[]",
                                """
                                "hierarchies":[{
                                  "code":"CUSTOMER_LOCATION",
                                  "name":"Customer location",
                                  "sql":"select forbidden",
                                  "levels":[{"code":"CITY","name":"City","order":1}]
                                }]"""
                            )
                    )
            )
            .andExpect(status().isUnprocessableEntity());

        mockMvc
            .perform(
                post("/api/modeling/dimension-definitions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createJson().replace(
                        "\"20000000-0000-0000-0000-000000000001\"",
                        "\"not-a-uuid\""
                    ))
            )
            .andExpect(status().isUnprocessableEntity());

        mockMvc
            .perform(
                put("/api/modeling/dimension-definitions/{id}", DEFINITION_ID)
                    .header("If-Match", ETAG)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateJson().replace("\"hierarchies\"", "\"materialization\":\"table\",\"hierarchies\""))
            )
            .andExpect(status().isUnprocessableEntity());

        mockMvc
            .perform(
                put("/api/modeling/dimension-definitions/{id}", DEFINITION_ID)
                    .header("If-Match", ETAG)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateJson().replace("\"DOMAIN\"", "0"))
            )
            .andExpect(status().isUnprocessableEntity());

        mockMvc
            .perform(
                post("/api/modeling/dimension-definitions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createJson().replace("\"Customer\"", "123"))
            )
            .andExpect(status().isUnprocessableEntity());

        mockMvc
            .perform(
                post("/api/modeling/dimension-definitions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createJson().replace("\"Customer\"", "true"))
            )
            .andExpect(status().isUnprocessableEntity());

        mockMvc
            .perform(
                post("/api/modeling/dimension-definitions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        createJson()
                            .replace(
                                "\"hierarchies\":[]",
                                """
                                "hierarchies":[{
                                  "code":"CUSTOMER_LOCATION",
                                  "name":"Customer location",
                                  "levels":[{"code":"CITY","name":"City","order":1.5}]
                                }]"""
                            )
                    )
            )
            .andExpect(status().isUnprocessableEntity());

        verify(service, never()).create(any(), any(), any());
        verify(service, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void requiresExactStrongIfMatchForPutAndLifecycleActions() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));

        mockMvc
            .perform(
                put("/api/modeling/dimension-definitions/{id}", DEFINITION_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateJson())
            )
            .andExpect(status().isPreconditionRequired())
            .andExpect(jsonPath("$.code").value("DIMENSION_DEFINITION_IF_MATCH_REQUIRED"));

        mockMvc
            .perform(
                post("/api/modeling/dimension-definitions/{id}/confirm", DEFINITION_ID)
                    .header("If-Match", "W/" + ETAG)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIMENSION_DEFINITION_IF_MATCH_INVALID"));

        UUID another = UUID.fromString("30000000-0000-0000-0000-000000000002");
        mockMvc
            .perform(
                post("/api/modeling/dimension-definitions/{id}/retire", DEFINITION_ID)
                    .header(
                        "If-Match",
                        "\"dimension-definition:" + another + ":1:" + CHECKSUM + "\""
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIMENSION_DEFINITION_IF_MATCH_INVALID"));

        verify(service, never()).update(any(), any(), any(), any(), any());
        verify(service, never()).confirm(any(), any(), any(), any());
        verify(service, never()).retire(any(), any(), any(), any());
    }

    @Test
    void mapsServiceErrorsToTheCanonicalApiEnvelope() throws Exception {
        when(service.get("server-tenant", DEFINITION_ID)).thenThrow(
            new ModelSpecException(
                "DIMENSION_DEFINITION_NOT_FOUND",
                "Dimension definition was not found",
                ModelSpecException.Kind.NOT_FOUND
            )
        );
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        when(service.confirm(eq("server-tenant"), eq("alice"), eq(DEFINITION_ID), any())).thenThrow(
            new ModelSpecException(
                "DIMENSION_DEFINITION_REVISION_CONFLICT",
                "Dimension definition was changed",
                ModelSpecException.Kind.CONFLICT,
                Map.of("currentRevision", 2)
            )
        );
        when(service.retire(eq("server-tenant"), eq("alice"), eq(DEFINITION_ID), any())).thenThrow(
            new ModelSpecException(
                "DIMENSION_DEFINITION_DOMAIN_FORBIDDEN",
                "Business category is not available for dimension maintenance",
                ModelSpecException.Kind.FORBIDDEN
            )
        );

        mockMvc
            .perform(get("/api/modeling/dimension-definitions/{id}", DEFINITION_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("DIMENSION_DEFINITION_NOT_FOUND"));

        mockMvc
            .perform(post("/api/modeling/dimension-definitions/{id}/confirm", DEFINITION_ID).header("If-Match", ETAG))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DIMENSION_DEFINITION_REVISION_CONFLICT"))
            .andExpect(jsonPath("$.data.currentRevision").value(2));

        mockMvc
            .perform(post("/api/modeling/dimension-definitions/{id}/retire", DEFINITION_ID).header("If-Match", ETAG))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("DIMENSION_DEFINITION_DOMAIN_FORBIDDEN"));
    }

    @Test
    void protectsEveryMutationWithTheModelingMaintainerAuthority() {
        assertThat(
            java.util.Arrays
                .stream(DimensionDefinitionResource.class.getDeclaredMethods())
                .filter(method -> List.of("create", "update", "confirm", "retire", "delete").contains(method.getName()))
                .map(method -> method.getAnnotation(PreAuthorize.class))
                .map(PreAuthorize::value)
        )
            .hasSize(5)
            .allSatisfy(expression -> assertThat(expression).isEqualTo("isAuthenticated()"));
    }

    private static String createJson() {
        return """
            {
              "domainId":"20000000-0000-0000-0000-000000000001",
              "name":"Customer",
              "definition":"Reusable customer dimension",
              "ownerId":"business-owner",
              "reuseScope":"DOMAIN",
              "hierarchies":[],
              "idempotencyKey":"create-customer"
            }
            """;
    }

    private static String updateJson() {
        return """
            {
              "name":"Customer",
              "definition":"Reusable customer dimension",
              "ownerId":"business-owner",
              "reuseScope":"DOMAIN",
              "hierarchies":[]
            }
            """;
    }

    private static View view(Status status, int revision, String checksum) {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return new View(
            DEFINITION_ID,
            "dim_30000000000000000000000000000001",
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            "Customer",
            "customer",
            "Reusable customer dimension",
            "business-owner",
            ReuseScope.DOMAIN,
            List.of(),
            status,
            revision,
            checksum,
            2,
            now,
            now
        );
    }
}
