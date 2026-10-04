package com.yuzhi.dts.platform.service.audit;

import static com.yuzhi.dts.platform.service.audit.AuditOutboxReplayReason.OPERATOR_RETRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.AuditProperties;
import com.yuzhi.dts.platform.config.AuditProperties.TenancyMode;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository.EnqueueCommand;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository.ReplayCommand;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository.ReplayTarget;
import com.yuzhi.dts.platform.web.rest.AuditOutboxReplayRequest;
import com.yuzhi.dts.platform.web.rest.AuditOutboxReplayResource;
import com.yuzhi.dts.platform.web.rest.catalog.CatalogDomainResource;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class AuditForwarderServiceTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final UUID REPLAY_OUTBOX_ID = UUID.fromString("9db67377-87f6-4cea-b02c-51f118b5f9a6");
    private static final String REPLAY_BODY_JSON = "{\"eventId\":\"event-17\",\"producer\":\"dts-platform\"}";
    private static final String REPLAY_HASH = sha256(REPLAY_BODY_JSON);
    private static final Instant REPLAY_NOW = Instant.parse("2026-08-01T08:09:10Z");

    @Test
    void auditedCatalogDomainReadsUseWritableTransactionsForTheOutboxInsert() throws NoSuchMethodException {
        assertWritableCatalogRead("listDomains", int.class, int.class, String.class);
        assertWritableCatalogRead("getDomainTree", boolean.class, String.class);
        assertWritableCatalogRead("getDomainAssetStats", UUID.class);
    }

    @Test
    void recordShouldPersistStableEventBeforeAnyRemoteDelivery() throws Exception {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuditForwarderService service = service(outbox, objectMapper);
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "alice";
        event.action = "MODEL_SPEC_CREATE";
        event.module = "modeling";
        event.resourceType = "modeling.model-spec";
        event.resourceId = "spec-1";

        service.record(event);

        ArgumentCaptor<EnqueueCommand> captor = ArgumentCaptor.forClass(EnqueueCommand.class);
        verify(outbox).enqueue(captor.capture());
        EnqueueCommand command = captor.getValue();
        assertThat(command.tenantId()).isEqualTo("tenant-a");
        Map<String, Object> body = objectMapper.readValue(command.bodyJson(), MAP_TYPE);
        assertThat(command.eventId()).isNotBlank();
        assertThat(command.payloadHash()).hasSize(64);
        assertThat(body)
            .containsEntry("eventId", command.eventId())
            .containsEntry("producer", "dts-platform")
            .containsEntry("action", "MODEL_SPEC_CREATE")
            .containsEntry("resourceId", "spec-1");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "true,true,true", "true,true,false", "true,false,true", "true,false,false",
        "false,true,true", "false,true,false", "false,false,true", "false,false,false"
    })
    void strictAuditJoinsWritableTransactionsWhileReadAndOrdinaryAuditStayIndependent(
        boolean strict, boolean readOnly, boolean machine
    ) {
        var outbox = mock(PlatformAuditOutboxRepository.class);
        UUID receipt = UUID.randomUUID();
        boolean transactional = strict && !readOnly;
        if (transactional) when(outbox.enqueueTransactional(any())).thenReturn(receipt);
        else when(outbox.enqueue(any())).thenReturn(receipt);
        var service = service(outbox, new ObjectMapper());
        var event = machineEvent(machine ? "airflow" : "alice");
        boolean wasActive = TransactionSynchronizationManager.isActualTransactionActive();
        boolean wasReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        try {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(readOnly);
            if (machine) {
                if (strict) assertThat(service.recordTrustedMachineStrict(event, "transaction-test")).isEqualTo(receipt);
                else service.recordTrustedMachine(event, "transaction-test");
            } else {
                if (strict) assertThat(service.recordStrict(event)).isEqualTo(receipt);
                else service.record(event);
            }
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(wasActive);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(wasReadOnly);
        }
        if (transactional) {
            verify(outbox).enqueueTransactional(any());
            verify(outbox, never()).enqueue(any());
        } else {
            verify(outbox).enqueue(any());
            verify(outbox, never()).enqueueTransactional(any());
        }
    }

    @Test
    void strictRecordFailsClosedWhenDisabledAndPropagatesPersistenceFailure() {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        AuditProperties disabled = enabledProperties();
        disabled.setEnabled(false);
        AuditForwarderService disabledService = new AuditForwarderService(
            disabled,
            outbox,
            new ObjectMapper(),
            new AuditTenantResolver(disabled)
        );
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "alice";
        event.action = "AUDIT_OUTBOX_REPLAY_REQUESTED";

        assertThatThrownBy(() -> disabledService.recordStrict(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("disabled");
        disabledService.record(event);
        verify(outbox, never()).enqueue(any());

        AuditForwarderService enabledService = service(outbox, new ObjectMapper());
        when(outbox.enqueue(any())).thenThrow(new IllegalStateException("outbox unavailable"));
        assertThatThrownBy(() -> enabledService.recordStrict(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("outbox unavailable");
    }

    @Test
    void tenantResolverRejectsUnsupportedMultiTenantMode() {
        AuditProperties properties = enabledProperties();
        properties.setTenancyMode(TenancyMode.MULTI_TENANT);

        assertThatThrownBy(() -> new AuditTenantResolver(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SINGLE_TENANT");
    }

    @Test
    void tenantResolverRequiresExplicitModeAndTenantId() {
        AuditProperties missingMode = new AuditProperties();
        missingMode.setTenantId("tenant-a");
        assertThatThrownBy(() -> new AuditTenantResolver(missingMode))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SINGLE_TENANT");

        AuditProperties missingTenant = new AuditProperties();
        missingTenant.setTenancyMode(TenancyMode.SINGLE_TENANT);
        assertThatThrownBy(() -> new AuditTenantResolver(missingTenant))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("auditing.tenant-id is required");
    }

    @Test
    void recordShouldPersistEvenWhenRemoteGatewayIsUnavailable() {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        AuditForwarderService service = service(outbox, new ObjectMapper());
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "alice";
        event.action = "MODEL_RELEASE_CANDIDATE_PUBLISH";
        event.module = "modeling";

        service.record(event);

        verify(outbox).enqueue(any(EnqueueCommand.class));
    }

    @Test
    void recordShouldSkipMachineActors() {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        AuditForwarderService service = service(outbox, new ObjectMapper());
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "service:dts-analytics";
        event.action = "查看数据源列表";
        event.module = "platform.infra";

        service.record(event);

        verify(outbox, never()).enqueue(any(EnqueueCommand.class));
    }

    @Test
    void recordTrustedMachineShouldPersistSanitizedDeterministicAirflowEvent() throws Exception {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuditForwarderService service = service(outbox, objectMapper);
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.occurredAt = Instant.parse("2026-08-01T02:03:04Z");
        event.actor = "service:dts-airflow";
        event.actorRole = "MACHINE";
        event.action = "AIRFLOW_DAG_RECONCILE";
        event.module = "ingestion";
        event.resourceType = "airflow_dag";
        event.resourceId = "dag-17";
        event.payload = Map.of("runId", "run-17", "access_token", "must-not-persist");

        service.recordTrustedMachine(event, "scheduled__2026-08-01T02:03:04+00:00");
        service.recordTrustedMachine(event, "scheduled__2026-08-01T02:03:04+00:00");

        ArgumentCaptor<EnqueueCommand> captor = ArgumentCaptor.forClass(EnqueueCommand.class);
        verify(outbox, times(2)).enqueue(captor.capture());
        List<EnqueueCommand> commands = captor.getAllValues();
        assertThat(commands.get(0).eventId()).isEqualTo(commands.get(1).eventId());
        assertThat(commands.get(0).payloadHash()).isEqualTo(commands.get(1).payloadHash());
        assertThat(commands.get(0).bodyJson()).isEqualTo(commands.get(1).bodyJson());
        Map<String, Object> body = objectMapper.readValue(commands.get(0).bodyJson(), MAP_TYPE);
        assertThat(body).containsEntry("actor", "_system:airflow").containsEntry("actorRole", "MACHINE");
        Map<String, Object> payload = (Map<String, Object>) body.get("payload");
        assertThat(payload).containsEntry("runId", "run-17").doesNotContainKey("access_token");
        assertThat(payload.values()).contains("[REDACTED]");
        assertThat(commands.get(0).bodyJson()).doesNotContain("must-not-persist");
    }

    @Test
    void recordTrustedMachineShouldAcceptSchedulerAndRejectUntrustedActorOrIdentity() {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        AuditForwarderService service = service(outbox, new ObjectMapper());
        AuditForwarderService.PendingAuditEvent scheduler = machineEvent("scheduler");

        service.recordTrustedMachine(scheduler, "schedule/run-1");

        assertThat(scheduler.actor).isEqualTo("_system:scheduler");
        verify(outbox).enqueue(any(EnqueueCommand.class));
        assertThatThrownBy(() -> service.recordTrustedMachine(machineEvent("service:dts-analytics"), "run-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not trusted");
        assertThatThrownBy(() -> service.recordTrustedMachine(machineEvent("scheduler"), "bad identity with spaces"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid format");
    }

    @Test
    void recordTrustedMachineShouldPropagateOutboxFailureForCallerTransactionRollback() {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        when(outbox.enqueue(any(EnqueueCommand.class))).thenThrow(new IllegalStateException("outbox unavailable"));
        AuditForwarderService service = service(outbox, new ObjectMapper());

        assertThatThrownBy(() -> service.recordTrustedMachine(machineEvent("airflow"), "dag-run-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("outbox unavailable");
    }

    @Test
    void recordTrustedMachineStrictMustReturnReceiptAndFailWhenAuditIsDisabled() {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        UUID receipt = UUID.fromString("56b7b954-5fd6-4e89-a5e5-dab8f78b91d9");
        when(outbox.enqueue(any(EnqueueCommand.class))).thenReturn(receipt);
        AuditForwarderService service = service(outbox, new ObjectMapper());

        assertThat(service.recordTrustedMachineStrict(machineEvent("airflow"), "dag-run-17"))
            .isEqualTo(receipt);

        AuditProperties disabled = new AuditProperties();
        disabled.setEnabled(false);
        AuditForwarderService disabledService = new AuditForwarderService(
            disabled,
            outbox,
            new ObjectMapper(),
            mock(AuditTenantResolver.class)
        );
        assertThatThrownBy(() ->
            disabledService.recordTrustedMachineStrict(machineEvent("airflow"), "dag-run-18")
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("disabled");
    }

    @Test
    void recordShouldPersistStableButtonCodeForPlatformLoginEvents() throws Exception {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuditForwarderService service = service(outbox, objectMapper);
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "xiezm";
        event.action = "AUTH LOGIN";
        event.module = "platform";
        event.operationType = "LOGIN";
        event.resourceType = "portal_user";
        event.resourceId = "xiezm";
        event.result = "SUCCESS";

        service.record(event);

        Map<String, Object> body = capturedBody(outbox, objectMapper);
        assertThat(body).containsEntry("buttonCode", "ADMIN_AUTH_PLATFORM_LOGIN");
        assertThat(body).containsEntry("operationCode", "ADMIN_AUTH_PLATFORM_LOGIN");
    }

    @Test
    void recordShouldPersistStableButtonCodeForPlatformLogoutEvents() throws Exception {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuditForwarderService service = service(outbox, objectMapper);
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "xiezm";
        event.action = "AUTH LOGOUT";
        event.module = "platform";
        event.operationType = "LOGOUT";
        event.resourceType = "portal_user";
        event.resourceId = "xiezm";
        event.result = "SUCCESS";

        service.record(event);

        Map<String, Object> body = capturedBody(outbox, objectMapper);
        assertThat(body).containsEntry("buttonCode", "ADMIN_AUTH_PLATFORM_LOGOUT");
        assertThat(body).containsEntry("operationCode", "ADMIN_AUTH_PLATFORM_LOGOUT");
    }

    @Test
    void recordShouldRedactSensitiveNestedValuesBeforeHashingAndPersistence() throws Exception {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuditForwarderService service = service(outbox, objectMapper);
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "alice";
        event.action = "MODEL_SPEC_UPDATE";
        event.module = "modeling";
        event.payload = Map.of(
            "modelCode",
            "dim_account",
            "password",
            "must-not-leak",
            "connection",
            Map.of("access_token", "must-not-leak-either")
        );

        service.record(event);

        Map<String, Object> body = capturedBody(outbox, objectMapper);
        Map<String, Object> payload = (Map<String, Object>) body.get("payload");
        assertThat(payload).containsEntry("modelCode", "dim_account").doesNotContainKey("password");
        assertThat(payload.values()).contains("[REDACTED]");
        Map<String, Object> connection = (Map<String, Object>) payload.get("connection");
        assertThat(connection).doesNotContainKey("access_token");
        assertThat(connection.values()).contains("[REDACTED]");
        assertThat(capturedCommand(outbox).bodyJson()).doesNotContain("must-not-leak");
    }

    @Test
    void replayMovesOnlyMatchingDeadEventAndAuditsSafeSummary() {
        PlatformAuditOutboxRepository repository = mock(PlatformAuditOutboxRepository.class);
        AuditService auditService = mock(AuditService.class);
        when(repository.findReplayTarget("tenant-a", REPLAY_OUTBOX_ID)).thenReturn(Optional.of(deadReplayTarget()));
        when(
            repository.replayDead(
                new ReplayCommand(
                    "tenant-a",
                    REPLAY_OUTBOX_ID,
                    REPLAY_HASH,
                    REPLAY_BODY_JSON,
                    REPLAY_NOW,
                    REPLAY_NOW
                )
            )
        ).thenReturn(1);
        AuditOutboxReplayService replay = replayService(repository, auditService);

        AuditOutboxReplayService.ReplayView result = replay.replay(REPLAY_OUTBOX_ID, REPLAY_HASH, OPERATOR_RETRY);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.attemptCount()).isEqualTo(7);
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(auditService).auditActionStrict(
            eq("AUDIT_OUTBOX_REPLAY_REQUESTED"),
            eq(AuditStage.SUCCESS),
            eq(REPLAY_OUTBOX_ID.toString()),
            payload.capture()
        );
        assertThat(payload.getValue())
            .containsOnlyKeys("outboxId", "eventId", "producer", "attemptCount", "reasonCode")
            .containsEntry("outboxId", REPLAY_OUTBOX_ID.toString())
            .containsEntry("eventId", "event-17")
            .containsEntry("producer", "dts-platform")
            .containsEntry("attemptCount", 7)
            .containsEntry("reasonCode", "OPERATOR_RETRY");
    }

    @Test
    void replayFailsClosedForWrongTenantStateHashAndCasRace() {
        PlatformAuditOutboxRepository repository = mock(PlatformAuditOutboxRepository.class);
        AuditService auditService = mock(AuditService.class);
        AuditOutboxReplayService replay = replayService(repository, auditService);

        when(repository.findReplayTarget("tenant-a", REPLAY_OUTBOX_ID)).thenReturn(Optional.empty());
        assertReplayError(
            () -> replay.replay(REPLAY_OUTBOX_ID, REPLAY_HASH, OPERATOR_RETRY),
            "AUDIT_OUTBOX_NOT_FOUND"
        );

        when(repository.findReplayTarget("tenant-a", REPLAY_OUTBOX_ID))
            .thenReturn(
                Optional.of(
                    new ReplayTarget(
                        REPLAY_OUTBOX_ID,
                        "event-17",
                        "dts-platform",
                        REPLAY_HASH,
                        REPLAY_BODY_JSON,
                        "PENDING",
                        7
                    )
                )
            )
            .thenReturn(Optional.of(deadReplayTarget()))
            .thenReturn(
                Optional.of(
                    new ReplayTarget(
                        REPLAY_OUTBOX_ID,
                        "event-17",
                        "dts-platform",
                        REPLAY_HASH,
                        "{\"tampered\":true}",
                        "DEAD",
                        7
                    )
                )
            )
            .thenReturn(Optional.of(deadReplayTarget()));
        assertReplayError(
            () -> replay.replay(REPLAY_OUTBOX_ID, REPLAY_HASH, OPERATOR_RETRY),
            "AUDIT_OUTBOX_NOT_DEAD"
        );
        assertReplayError(
            () -> replay.replay(REPLAY_OUTBOX_ID, "b".repeat(64), OPERATOR_RETRY),
            "AUDIT_OUTBOX_PAYLOAD_CHANGED"
        );
        assertReplayError(
            () -> replay.replay(REPLAY_OUTBOX_ID, REPLAY_HASH, OPERATOR_RETRY),
            "AUDIT_OUTBOX_PAYLOAD_CHANGED"
        );
        when(
            repository.replayDead(
                new ReplayCommand(
                    "tenant-a",
                    REPLAY_OUTBOX_ID,
                    REPLAY_HASH,
                    REPLAY_BODY_JSON,
                    REPLAY_NOW,
                    REPLAY_NOW
                )
            )
        ).thenReturn(0);
        assertReplayError(
            () -> replay.replay(REPLAY_OUTBOX_ID, REPLAY_HASH, OPERATOR_RETRY),
            "AUDIT_OUTBOX_REPLAY_CONFLICT"
        );
        verify(auditService, never()).auditActionStrict(anyString(), any(), anyString(), any());
    }

    @Test
    void replayValidatesRequestAndRestrictsMutationToOperationalAdministrators() throws Exception {
        PlatformAuditOutboxRepository repository = mock(PlatformAuditOutboxRepository.class);
        AuditOutboxReplayService replay = replayService(repository, mock(AuditService.class));

        assertReplayError(
            () -> replay.replay(REPLAY_OUTBOX_ID, "bad", OPERATOR_RETRY),
            "AUDIT_OUTBOX_REPLAY_REQUEST_INVALID"
        );
        assertReplayError(
            () -> replay.replay(REPLAY_OUTBOX_ID, REPLAY_HASH, null),
            "AUDIT_OUTBOX_REPLAY_REQUEST_INVALID"
        );
        verify(repository, never()).findReplayTarget(anyString(), any());

        Method replayMethod = AuditOutboxReplayResource.class.getMethod(
            "replay",
            UUID.class,
            AuditOutboxReplayRequest.class
        );
        PreAuthorize authorization = replayMethod.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value())
            .contains("ROLE_SYS_ADMIN", "ROLE_ADMIN", "ROLE_OP_ADMIN")
            .doesNotContain("ROLE_SECURITY_AUDITOR");
        AuditOutboxReplayResource resource = new AuditOutboxReplayResource(mock(AuditOutboxReplayService.class));
        assertThat(resource.handleReplayError(AuditOutboxReplayException.notFound()).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(
            resource.handleReplayError(
                AuditOutboxReplayException.conflict("AUDIT_OUTBOX_REPLAY_CONFLICT", "Replay conflict")
            ).getStatusCode()
        ).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void replayEndpointReturns401And403BeforeAllowingOperationalAdministrator() throws Exception {
        UUID outboxId = REPLAY_OUTBOX_ID;
        String requestBody = new ObjectMapper().writeValueAsString(
            Map.of("expectedPayloadHash", REPLAY_HASH, "reasonCode", "OPERATOR_RETRY")
        );
        String unsupportedReasonBody = new ObjectMapper().writeValueAsString(
            Map.of("expectedPayloadHash", REPLAY_HASH, "reasonCode", "UNCONTROLLED_RETRY")
        );
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(ReplaySecurityTestConfiguration.class);
            context.refresh();
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            mockMvc
                .perform(
                    post("/api/audit-outbox/{id}/replay", outboxId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                )
                .andExpect(status().isUnauthorized());
            mockMvc
                .perform(
                    post("/api/audit-outbox/{id}/replay", outboxId)
                        .with(user("auditor").authorities(new SimpleGrantedAuthority("ROLE_SECURITY_AUDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                )
                .andExpect(status().isForbidden());
            mockMvc
                .perform(
                    post("/api/audit-outbox/{id}/replay", outboxId)
                        .with(user("operator").authorities(new SimpleGrantedAuthority("ROLE_OP_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unsupportedReasonBody)
                )
                .andExpect(status().isBadRequest());
            mockMvc
                .perform(
                    post("/api/audit-outbox/{id}/replay", outboxId)
                        .with(user("operator").authorities(new SimpleGrantedAuthority("ROLE_OP_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                )
                .andExpect(status().isAccepted());

            verify(context.getBean(AuditOutboxReplayService.class))
                .replay(outboxId, REPLAY_HASH, OPERATOR_RETRY);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class ReplaySecurityTestConfiguration {

        @Bean
        AuditOutboxReplayService replayService() {
            return mock(AuditOutboxReplayService.class);
        }

        @Bean
        AuditOutboxReplayResource replayResource(AuditOutboxReplayService service) {
            return new AuditOutboxReplayResource(service);
        }

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
                User.withUsername("test-user").password("{noop}password").authorities("ROLE_USER").build()
            );
        }
    }

    private AuditProperties enabledProperties() {
        AuditProperties properties = new AuditProperties();
        properties.setEnabled(true);
        properties.setTenancyMode(TenancyMode.SINGLE_TENANT);
        properties.setTenantId("tenant-a");
        return properties;
    }

    private AuditForwarderService service(PlatformAuditOutboxRepository outbox, ObjectMapper objectMapper) {
        AuditProperties properties = enabledProperties();
        return new AuditForwarderService(properties, outbox, objectMapper, new AuditTenantResolver(properties));
    }

    private AuditOutboxReplayService replayService(
        PlatformAuditOutboxRepository repository,
        AuditService auditService
    ) {
        AuditProperties properties = enabledProperties();
        return new AuditOutboxReplayService(
            repository,
            auditService,
            new AuditTenantResolver(properties),
            Clock.fixed(REPLAY_NOW, ZoneOffset.UTC)
        );
    }

    private ReplayTarget deadReplayTarget() {
        return new ReplayTarget(
            REPLAY_OUTBOX_ID,
            "event-17",
            "dts-platform",
            REPLAY_HASH,
            REPLAY_BODY_JSON,
            "DEAD",
            7
        );
    }

    private void assertReplayError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action)
            .isInstanceOf(AuditOutboxReplayException.class)
            .extracting("code")
            .isEqualTo(code);
    }

    private AuditForwarderService.PendingAuditEvent machineEvent(String actor) {
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.occurredAt = Instant.parse("2026-08-01T02:03:04Z");
        event.actor = actor;
        event.action = "SCHEDULE_RUN";
        event.module = "scheduler";
        return event;
    }

    private Map<String, Object> capturedBody(PlatformAuditOutboxRepository outbox, ObjectMapper objectMapper) throws Exception {
        return objectMapper.readValue(capturedCommand(outbox).bodyJson(), MAP_TYPE);
    }

    private EnqueueCommand capturedCommand(PlatformAuditOutboxRepository outbox) {
        ArgumentCaptor<EnqueueCommand> captor = ArgumentCaptor.forClass(EnqueueCommand.class);
        verify(outbox).enqueue(captor.capture());
        return captor.getValue();
    }

    private static void assertWritableCatalogRead(String methodName, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        Method method = CatalogDomainResource.class.getDeclaredMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).as("%s must declare a transaction", methodName).isNotNull();
        assertThat(transactional.readOnly())
            .as("%s writes a durable audit outbox row and cannot run in a read-only transaction", methodName)
            .isFalse();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
