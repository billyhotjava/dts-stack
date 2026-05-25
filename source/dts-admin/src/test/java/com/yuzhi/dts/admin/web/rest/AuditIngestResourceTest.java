package com.yuzhi.dts.admin.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.config.AuditIngestProperties;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.service.audit.AuditActionRequest;
import com.yuzhi.dts.admin.service.audit.AuditIngestAuthenticator;
import com.yuzhi.dts.admin.service.audit.AuditIngestAuthenticator.Decision;
import com.yuzhi.dts.admin.service.audit.AuditOperationKind;
import com.yuzhi.dts.admin.service.audit.AuditV2Service;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditIngestResourceTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuditV2Service auditV2Service;

    private AuditIngestAuthenticator authenticator;

    private AdminKeycloakUserRepository userRepository;

    @BeforeEach
    void setUp() {
        auditV2Service = mock(AuditV2Service.class);
        authenticator = mock(AuditIngestAuthenticator.class);
        userRepository = mock(AdminKeycloakUserRepository.class);
        mockMvc = mockMvcWithContainerCidrs(List.of("172.16.0.0/12"));
    }

    private MockMvc mockMvcWithContainerCidrs(List<String> containerCidrs) {
        AuditIngestProperties properties = new AuditIngestProperties();
        properties.setContainerCidrs(containerCidrs);
        return MockMvcBuilders
            .standaloneSetup(new AuditIngestResource(auditV2Service, authenticator, userRepository, properties))
            .build();
    }

    @Test
    void resolvesOnlyExistingHumanActorAndDropsContainerIp() throws Exception {
        AdminKeycloakUser user = new AdminKeycloakUser();
        user.setUsername("opadmin");
        user.setEmail("opadmin@platform.local");
        user.setFullName("运维管理员");

        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-analytics", "valid token"));
        when(userRepository.findByUsernameIgnoreCase("opadmin@platform.local")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("opadmin@platform.local")).thenReturn(Optional.of(user));

        Map<String, Object> body = Map.ofEntries(
            Map.entry("sourceSystem", "analytics"),
            Map.entry("actor", "service:dts-analytics"),
            Map.entry("operator", "opadmin@platform.local"),
            Map.entry("actorName", "service:dts-analytics"),
            Map.entry("module", "SCREEN"),
            Map.entry("buttonCode", "SCREEN_UPDATE"),
            Map.entry("operationType", "WRITE"),
            Map.entry("resourceType", "SCREEN"),
            Map.entry("resourceId", "42"),
            Map.entry("targetTable", "SCREEN"),
            Map.entry("targetIds", List.of("42")),
            Map.entry("clientIp", "172.19.0.15"),
            Map.entry("result", "SUCCESS")
        );

        mockMvc
            .perform(
                post("/api/audit-events")
                    .header("X-Forwarded-For", "223.86.189.127, 172.19.0.15")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(body))
            )
            .andExpect(status().isAccepted());

        ArgumentCaptor<AuditActionRequest> captor = ArgumentCaptor.forClass(AuditActionRequest.class);
        verify(auditV2Service).record(captor.capture());
        AuditActionRequest request = captor.getValue();
        assertThat(request.actorId()).isEqualTo("opadmin");
        assertThat(request.actorName()).isEqualTo("运维管理员");
        assertThat(request.operationKindOverride()).isEqualTo(AuditOperationKind.UPDATE);
        assertThat(request.clientIp()).isEqualTo("223.86.189.127");
    }

    @Test
    void keepsContainerIpWhenNoRealClientIpIsAvailable() throws Exception {
        AdminKeycloakUser user = new AdminKeycloakUser();
        user.setUsername("opadmin");
        user.setEmail("opadmin@platform.local");
        user.setFullName("运维管理员");

        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-analytics", "valid token"));
        when(userRepository.findByUsernameIgnoreCase("opadmin@platform.local")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("opadmin@platform.local")).thenReturn(Optional.of(user));

        Map<String, Object> body = Map.ofEntries(
            Map.entry("sourceSystem", "analytics"),
            Map.entry("actor", "service:dts-analytics"),
            Map.entry("operator", "opadmin@platform.local"),
            Map.entry("actorName", "service:dts-analytics"),
            Map.entry("module", "SCREEN"),
            Map.entry("buttonCode", "SCREEN_VIEW"),
            Map.entry("operationType", "READ"),
            Map.entry("resourceType", "SCREEN"),
            Map.entry("resourceId", "42"),
            Map.entry("targetTable", "SCREEN"),
            Map.entry("targetIds", List.of("42")),
            Map.entry("clientIp", "172.19.0.15"),
            Map.entry("result", "SUCCESS")
        );

        mockMvc
            .perform(
                post("/api/audit-events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(body))
            )
            .andExpect(status().isAccepted());

        ArgumentCaptor<AuditActionRequest> captor = ArgumentCaptor.forClass(AuditActionRequest.class);
        verify(auditV2Service).record(captor.capture());
        assertThat(captor.getValue().clientIp()).isEqualTo("172.19.0.15");
    }

    @Test
    void skipsConfiguredNonStandardContainerCidrInFavourOfBodyClientIp() throws Exception {
        // 现场容器网段是 172.168.0.0/16（非 Docker 默认 172.16/12）。配置后该网段应被识别为容器跳
        // 表头里的代理 IP，转而采用 body 中平台采集到的真实客户端 IP。
        MockMvc siteMockMvc = mockMvcWithContainerCidrs(List.of("172.16.0.0/12", "172.168.0.0/16"));

        AdminKeycloakUser user = new AdminKeycloakUser();
        user.setUsername("opadmin");
        user.setEmail("opadmin@platform.local");
        user.setFullName("运维管理员");

        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-analytics", "valid token"));
        when(userRepository.findByUsernameIgnoreCase("opadmin@platform.local")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("opadmin@platform.local")).thenReturn(Optional.of(user));

        Map<String, Object> body = Map.ofEntries(
            Map.entry("sourceSystem", "analytics"),
            Map.entry("actor", "service:dts-analytics"),
            Map.entry("operator", "opadmin@platform.local"),
            Map.entry("actorName", "service:dts-analytics"),
            Map.entry("module", "SCREEN"),
            Map.entry("buttonCode", "SCREEN_VIEW"),
            Map.entry("operationType", "READ"),
            Map.entry("resourceType", "SCREEN"),
            Map.entry("resourceId", "42"),
            Map.entry("targetTable", "SCREEN"),
            Map.entry("targetIds", List.of("42")),
            Map.entry("clientIp", "203.0.113.50"),
            Map.entry("result", "SUCCESS")
        );

        siteMockMvc
            .perform(
                post("/api/audit-events")
                    .header("X-Forwarded-For", "172.168.0.5")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(body))
            )
            .andExpect(status().isAccepted());

        ArgumentCaptor<AuditActionRequest> captor = ArgumentCaptor.forClass(AuditActionRequest.class);
        verify(auditV2Service).record(captor.capture());
        assertThat(captor.getValue().clientIp()).isEqualTo("203.0.113.50");
    }

    @Test
    void usesOperationCodeAsButtonCodeWhenAnalyticsPayloadOmitsButtonCode() throws Exception {
        AdminKeycloakUser user = new AdminKeycloakUser();
        user.setUsername("xiezm");
        user.setEmail("xiezm@example.test");
        user.setFullName("测试xiezm");

        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-analytics", "valid token"));
        when(userRepository.findByUsernameIgnoreCase("xiezm@example.test")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("xiezm@example.test")).thenReturn(Optional.of(user));

        Map<String, Object> body = Map.ofEntries(
            Map.entry("sourceSystem", "analytics"),
            Map.entry("actor", "xiezm@example.test"),
            Map.entry("module", "analytics.screen"),
            Map.entry("operationCode", "SCREEN_VIEW"),
            Map.entry("operationName", "查看大屏"),
            Map.entry("operationType", "READ"),
            Map.entry("resourceType", "SCREEN"),
            Map.entry("resourceId", "42"),
            Map.entry("targetTable", "SCREEN"),
            Map.entry("targetIds", List.of("42")),
            Map.entry("result", "SUCCESS")
        );

        mockMvc
            .perform(
                post("/api/audit-events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(body))
            )
            .andExpect(status().isAccepted());

        ArgumentCaptor<AuditActionRequest> captor = ArgumentCaptor.forClass(AuditActionRequest.class);
        verify(auditV2Service).record(captor.capture());
        AuditActionRequest request = captor.getValue();
        assertThat(request.sourceSystem()).isEqualTo("analytics");
        assertThat(request.buttonCode()).isEqualTo("SCREEN_VIEW");
        assertThat(request.operationCodeOverride()).isEqualTo("SCREEN_VIEW");
        assertThat(request.operationNameOverride()).isEqualTo("查看大屏");
    }

    @Test
    void skipsAuditEventsWhenNoExistingHumanActorIsPresent() throws Exception {
        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-analytics", "valid token"));

        Map<String, Object> body = Map.ofEntries(
            Map.entry("sourceSystem", "analytics"),
            Map.entry("actor", "service:dts-analytics"),
            Map.entry("operator", "SUCCESS"),
            Map.entry("principal", "POSTGRESQL"),
            Map.entry("module", "SCREEN"),
            Map.entry("buttonCode", "SCREEN_VIEW"),
            Map.entry("operationType", "READ"),
            Map.entry("resourceType", "SCREEN"),
            Map.entry("resourceId", "42"),
            Map.entry("targetTable", "SCREEN"),
            Map.entry("targetIds", List.of("42")),
            Map.entry("clientIp", "172.19.0.15")
        );

        mockMvc
            .perform(
                post("/api/audit-events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(body))
            )
            .andExpect(status().isAccepted());

        verify(auditV2Service, never()).record(any());
    }
}
