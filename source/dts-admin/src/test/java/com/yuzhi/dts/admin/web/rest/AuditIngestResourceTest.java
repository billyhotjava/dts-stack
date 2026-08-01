package com.yuzhi.dts.admin.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.config.AuditIngestProperties;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.domain.audit.AuditEntry;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.repository.audit.AuditEntryRepository;
import com.yuzhi.dts.admin.security.AdminInboundServiceAuthenticator;
import com.yuzhi.dts.admin.security.AdminInboundServiceAuthenticator.Decision;
import com.yuzhi.dts.admin.service.audit.AuditActionRequest;
import com.yuzhi.dts.admin.service.audit.AuditIngestFingerprint;
import com.yuzhi.dts.admin.service.audit.AuditIngestIdempotencyService;
import com.yuzhi.dts.admin.service.audit.AuditIngestPersistenceService;
import com.yuzhi.dts.admin.service.audit.AuditOperationKind;
import com.yuzhi.dts.admin.service.audit.AuditV2Service;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditIngestResourceTest {

    private static final String EVENT_ID = "audit-event-81-0001";

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuditV2Service auditV2Service;

    private AdminInboundServiceAuthenticator authenticator;

    private AdminKeycloakUserRepository userRepository;

    private AuditEntryRepository auditEntryRepository;

    private AuditIngestIdempotencyService idempotencyService;

    private final Map<String, AuditEntry> ingestedEntries = new HashMap<>();

    @BeforeEach
    void setUp() {
        auditV2Service = mock(AuditV2Service.class);
        authenticator = mock(AdminInboundServiceAuthenticator.class);
        userRepository = mock(AdminKeycloakUserRepository.class);
        auditEntryRepository = mock(AuditEntryRepository.class);
        ingestedEntries.clear();
        when(auditV2Service.record(any())).thenAnswer(invocation -> new AuditEntry());
        when(auditEntryRepository.findByIngestProducerAndIngestEventId(any(), any()))
            .thenAnswer(invocation -> Optional.ofNullable(ingestedEntries.get(identityKey(invocation.getArgument(0), invocation.getArgument(1)))));
        when(auditEntryRepository.saveAndFlush(any(AuditEntry.class)))
            .thenAnswer(invocation -> {
                AuditEntry entry = invocation.getArgument(0);
                ingestedEntries.put(identityKey(entry.getIngestProducer(), entry.getIngestEventId()), entry);
                return entry;
            });
        AuditIngestPersistenceService persistence = new AuditIngestPersistenceService(auditV2Service, auditEntryRepository);
        idempotencyService = new AuditIngestIdempotencyService(
            auditEntryRepository,
            persistence,
            new AuditIngestFingerprint(objectMapper)
        );
        mockMvc = mockMvcWithContainerCidrs(List.of("172.16.0.0/12"));
    }

    private MockMvc mockMvcWithContainerCidrs(List<String> containerCidrs) {
        AuditIngestProperties properties = new AuditIngestProperties();
        properties.setContainerCidrs(containerCidrs);
        return MockMvcBuilders
            .standaloneSetup(new AuditIngestResource(idempotencyService, authenticator, userRepository, properties))
            .build();
    }

    private String identityKey(String producer, String eventId) {
        return producer + '|' + eventId;
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
            Map.entry("eventId", "audit-actor-resolution-1"),
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
            .andExpect(status().isCreated());

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
            Map.entry("eventId", "audit-container-ip-1"),
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
            .andExpect(status().isCreated());

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
            Map.entry("eventId", "audit-site-container-ip-1"),
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
            .andExpect(status().isCreated());

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
            Map.entry("eventId", "audit-operation-code-1"),
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
            .andExpect(status().isCreated());

        ArgumentCaptor<AuditActionRequest> captor = ArgumentCaptor.forClass(AuditActionRequest.class);
        verify(auditV2Service).record(captor.capture());
        AuditActionRequest request = captor.getValue();
        assertThat(request.sourceSystem()).isEqualTo("analytics");
        assertThat(request.buttonCode()).isEqualTo("SCREEN_VIEW");
        assertThat(request.operationCodeOverride()).isEqualTo("SCREEN_VIEW");
        assertThat(request.operationNameOverride()).isEqualTo("查看大屏");
    }

    @Test
    void rejectsAuditEventsWhenNoExistingHumanActorIsPresent() throws Exception {
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
            .andExpect(status().isUnprocessableEntity());

        verify(auditV2Service, never()).record(any());
    }

    @Test
    void acceptsWhitelistedPlatformMachineActorAfterPairwiseAuthentication() throws Exception {
        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-platform", "valid token"));
        Map<String, Object> body = new LinkedHashMap<>(
            idempotentBody(EVENT_ID, "forged-producer", "提交模型物化任务", false)
        );
        body.put("actor", "_system:airflow");
        body.put("buttonCode", "MODEL_MATERIALIZATION_DISPATCH_SUBMITTED");
        body.put("operationType", "EXECUTE");

        MvcResult response = postAuditEvent(body, "dts-platform");

        assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        ArgumentCaptor<AuditActionRequest> captor = ArgumentCaptor.forClass(AuditActionRequest.class);
        verify(auditV2Service).record(captor.capture());
        assertThat(captor.getValue().actorId()).isEqualTo("_system:airflow");
        assertThat(captor.getValue().actorName()).isEqualTo("_system:airflow");
        assertThat(captor.getValue().allowSystemActor()).isTrue();
    }

    @Test
    void rejectsPlatformMachineActorFromAnotherAuthenticatedProducer() throws Exception {
        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-analytics", "valid token"));
        Map<String, Object> body = new LinkedHashMap<>(
            idempotentBody(EVENT_ID, "forged-producer", "提交模型物化任务", false)
        );
        body.put("actor", "_system:airflow");

        MvcResult response = postAuditEvent(body, "dts-analytics");

        assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        verify(auditV2Service, never()).record(any());
    }

    @Test
    void rejectsUnrecognizedMachineActorFromPlatform() throws Exception {
        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-platform", "valid token"));
        Map<String, Object> body = new LinkedHashMap<>(
            idempotentBody(EVENT_ID, "forged-producer", "提交模型物化任务", false)
        );
        body.put("actor", "_system:untrusted");

        MvcResult response = postAuditEvent(body, "dts-platform");

        assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        verify(auditV2Service, never()).record(any());
    }

    @Test
    void returnsServiceUnavailableWhenAuditPersistenceIsUnavailable() throws Exception {
        stubHumanActor("alice");
        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-platform", "valid token"));
        when(auditV2Service.record(any())).thenThrow(new DataAccessResourceFailureException("database unavailable"));

        mockMvc
            .perform(
                post("/api/audit-events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(idempotentBody(EVENT_ID, "ignored", "创建模型", false)))
            )
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void firstEventIdSubmissionReturnsRecorded() throws Exception {
        stubHumanActor("alice");
        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-platform", "valid token"));

        MvcResult response = postAuditEvent(
            idempotentBody(EVENT_ID, "forged-producer", "创建模型", false),
            "dts-platform"
        );

        assertAll(
            () -> assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value()),
            () -> assertThat(responseField(response, "status")).isEqualTo("RECORDED"),
            () -> assertThat(responseField(response, "eventId")).isEqualTo(EVENT_ID)
        );
    }

    @Test
    void canonicalReplayIgnoresJsonOrderAndBodyProducer() throws Exception {
        stubHumanActor("alice");
        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-platform", "valid token"));

        MvcResult first = postAuditEvent(
            idempotentBody(EVENT_ID, "forged-producer-a", "创建模型", false),
            "dts-platform"
        );
        MvcResult duplicate = postAuditEvent(
            idempotentBody(EVENT_ID, "forged-producer-b", "创建模型", true),
            "dts-platform"
        );

        assertAll(
            () -> assertThat(first.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value()),
            () -> assertThat(responseField(first, "status")).isEqualTo("RECORDED"),
            () -> assertThat(responseField(first, "eventId")).isEqualTo(EVENT_ID),
            () -> assertThat(duplicate.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
            () -> assertThat(responseField(duplicate, "status")).isEqualTo("DUPLICATE"),
            () -> assertThat(responseField(duplicate, "eventId")).isEqualTo(EVENT_ID)
        );
    }

    @Test
    void sameProducerAndEventIdWithDifferentPayloadReturnsConflict() throws Exception {
        stubHumanActor("alice");
        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-platform", "valid token"));

        MvcResult first = postAuditEvent(
            idempotentBody(EVENT_ID, "forged-producer", "创建模型", false),
            "dts-platform"
        );
        MvcResult conflict = postAuditEvent(
            idempotentBody(EVENT_ID, "forged-producer", "修改后的模型", false),
            "dts-platform"
        );

        assertAll(
            () -> assertThat(first.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value()),
            () -> assertThat(responseField(first, "status")).isEqualTo("RECORDED"),
            () -> assertThat(responseField(first, "eventId")).isEqualTo(EVENT_ID),
            () -> assertThat(conflict.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value()),
            () -> assertThat(responseField(conflict, "status")).isEqualTo("IDEMPOTENCY_CONFLICT"),
            () -> assertThat(responseField(conflict, "eventId")).isEqualTo(EVENT_ID)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void redactsNestedEvidenceBeforeFingerprintingAndPersistence() throws Exception {
        stubHumanActor("alice");
        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-platform", "valid token"));
        Map<String, Object> first = new LinkedHashMap<>(
            idempotentBody("audit-sensitive-1", "forged-producer", "记录模型审计证据", false)
        );
        first.put(
            "payload",
            Map.of(
                "connection",
                Map.of("password", "first-password"),
                "evidence",
                List.of("Bearer first-token", Map.of("credential", "first-credential"))
            )
        );
        Map<String, Object> replay = new LinkedHashMap<>(first);
        replay.put(
            "payload",
            Map.of(
                "connection",
                Map.of("password", "second-password"),
                "evidence",
                List.of("Bearer second-token", Map.of("credential", "second-credential"))
            )
        );

        MvcResult recorded = postAuditEvent(first, "dts-platform");
        MvcResult duplicate = postAuditEvent(replay, "dts-platform");

        assertThat(recorded.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(responseField(duplicate, "status")).isEqualTo("DUPLICATE");
        ArgumentCaptor<AuditActionRequest> captor = ArgumentCaptor.forClass(AuditActionRequest.class);
        verify(auditV2Service, times(1)).record(captor.capture());
        Map<String, Object> details = (Map<String, Object>) captor.getValue().details().getFirst().value();
        Map<String, Object> payload = (Map<String, Object>) details.get("payload");
        assertThat((Map<String, Object>) payload.get("connection"))
            .doesNotContainKey("password")
            .containsEntry("redacted_key_1", "[REDACTED]");
        assertThat((List<Object>) payload.get("evidence"))
            .containsExactly("[REDACTED]", Map.of("redacted_key_1", "[REDACTED]"));
        assertThat(String.valueOf(details))
            .doesNotContain("password", "credential", "first-password", "first-token", "first-credential")
            .doesNotContain("second-password", "second-token", "second-credential");
    }

    @Test
    void authenticatedServiceIdentityPartitionsEventIdsInsteadOfBodyProducer() throws Exception {
        stubHumanActor("alice");
        when(authenticator.authenticate(any()))
            .thenReturn(
                new Decision(true, "dts-platform", "valid token"),
                new Decision(true, "dts-analytics", "valid token")
            );
        Map<String, Object> body = idempotentBody(EVENT_ID, "same-forged-producer", "创建模型", false);

        MvcResult platformResponse = postAuditEvent(body, "same-untrusted-header");
        MvcResult analyticsResponse = postAuditEvent(body, "same-untrusted-header");

        assertAll(
            () -> assertThat(platformResponse.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value()),
            () -> assertThat(responseField(platformResponse, "status")).isEqualTo("RECORDED"),
            () -> assertThat(responseField(platformResponse, "eventId")).isEqualTo(EVENT_ID),
            () -> assertThat(analyticsResponse.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value()),
            () -> assertThat(responseField(analyticsResponse, "status")).isEqualTo("RECORDED"),
            () -> assertThat(responseField(analyticsResponse, "eventId")).isEqualTo(EVENT_ID)
        );
    }

    private void stubHumanActor(String username) {
        AdminKeycloakUser user = new AdminKeycloakUser();
        user.setUsername(username);
        user.setFullName("Alice");
        when(userRepository.findByUsernameIgnoreCase(username)).thenReturn(Optional.of(user));
    }

    private Map<String, Object> idempotentBody(String eventId, String producer, String summary, boolean reversed) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (reversed) {
            metadata.put("correlationId", "correlation-81");
            metadata.put("tenantId", "tenant-a");
        } else {
            metadata.put("tenantId", "tenant-a");
            metadata.put("correlationId", "correlation-81");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (reversed) {
            body.put("metadata", metadata);
            body.put("result", "SUCCESS");
            body.put("resourceId", "model-81");
            body.put("resourceType", "MODEL_SPEC");
            body.put("operationType", "CREATE");
            body.put("buttonCode", "MODEL_SPEC_CREATE");
            body.put("module", "modeling");
            body.put("summary", summary);
            body.put("actor", "alice");
            body.put("sourceSystem", "platform");
            body.put("producer", producer);
            body.put("eventId", eventId);
        } else {
            body.put("eventId", eventId);
            body.put("producer", producer);
            body.put("sourceSystem", "platform");
            body.put("actor", "alice");
            body.put("summary", summary);
            body.put("module", "modeling");
            body.put("buttonCode", "MODEL_SPEC_CREATE");
            body.put("operationType", "CREATE");
            body.put("resourceType", "MODEL_SPEC");
            body.put("resourceId", "model-81");
            body.put("result", "SUCCESS");
            body.put("metadata", metadata);
        }
        return body;
    }

    private MvcResult postAuditEvent(Map<String, Object> body, String serviceName) throws Exception {
        return mockMvc
            .perform(
                post("/api/audit-events")
                    .header("X-DTS-Service", serviceName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(body))
            )
            .andReturn();
    }

    private String responseField(MvcResult result, String field) throws Exception {
        String content = result.getResponse().getContentAsString();
        if (content.isBlank()) {
            return null;
        }
        return objectMapper.readTree(content).path(field).asText(null);
    }
}
