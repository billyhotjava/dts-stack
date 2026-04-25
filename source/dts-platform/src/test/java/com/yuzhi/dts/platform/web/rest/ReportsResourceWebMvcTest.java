package com.yuzhi.dts.platform.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.audit.AuditFlowManager;
import com.yuzhi.dts.platform.security.session.PortalSessionActivityService;
import com.yuzhi.dts.platform.service.visualization.BiReportLinkService;
import com.yuzhi.dts.platform.service.visualization.dto.BiReportLinkDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportsResource.class)
@AutoConfigureMockMvc
@Import(ReportsResourceWebMvcTest.MethodSecurityConfig.class)
class ReportsResourceWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BiReportLinkService reports;

    @MockBean
    private AuditService auditService;

    
    @MockBean
    private AuditFlowManager auditFlowManager;
    
    
    @MockBean
    private PortalSessionActivityService portalSessionActivityService;

    @Test
    @WithMockUser(authorities = {"ROLE_EMPLOYEE", "ROLE_INTERNAL"})
    void employeeShouldReadPublishedOnly() throws Exception {
        when(
            reports.listPublished(
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                any(),
                nullable(String.class)
            )
        ).thenReturn(
            List.of(
                new BiReportLinkDto(
                    UUID.randomUUID(),
                    "internal",
                    "内部看板",
                    "HETU",
                    "dashboard",
                    List.of(),
                    List.of(),
                    "INTERNAL",
                    "https://example.local/internal",
                    true,
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "system",
                    Instant.now(),
                    null
                )
            )
        );

        mockMvc
            .perform(get("/api/reports/published"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("internal"));

        Map<String, Object> payload = Map.of(
            "code", "blocked_create",
            "title", "员工创建",
            "url", "https://example.local/create",
            "classification", "INTERNAL"
        );

        mockMvc
            .perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isForbidden());

        verify(reports, never()).create(any(), anyString());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_EMPLOYEE", "ROLE_INTERNAL"})
    void employeeShouldNotUpdateOrDelete() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
            "code", "blocked_update",
            "title", "员工修改",
            "url", "https://example.local/update",
            "classification", "INTERNAL"
        );

        mockMvc
            .perform(put("/api/reports/{id}", id).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/reports/{id}", id)).andExpect(status().isForbidden());

        verify(reports, never()).update(any(), any(), anyString());
        verify(reports, never()).delete(any());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_DEPT_DATA_OWNER", "ROLE_INTERNAL"})
    void dataOwnerShouldCreate() throws Exception {
        when(reports.create(any(), nullable(String.class)))
            .thenReturn(
                new BiReportLinkDto(
                    UUID.randomUUID(),
                    "owner_create",
                    "维护者创建",
                    "HETU",
                    "dashboard",
                    List.of(),
                    List.of(),
                    "INTERNAL",
                    "https://example.local/owner",
                    true,
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "owner",
                    Instant.now(),
                    null
                )
            );

        Map<String, Object> payload = Map.of(
            "code", "owner_create",
            "title", "维护者创建",
            "url", "https://example.local/owner",
            "classification", "INTERNAL"
        );

        mockMvc
            .perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value("owner_create"));

        verify(reports).create(any(), nullable(String.class));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(org.springframework.security.config.Customizer.withDefaults());
            return http.build();
        }

        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(User.withUsername("user").password("{noop}password").authorities("ROLE_USER").build());
        }
    }
}
