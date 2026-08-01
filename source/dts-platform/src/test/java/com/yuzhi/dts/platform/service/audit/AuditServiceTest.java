package com.yuzhi.dts.platform.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditActionCatalog;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuditServiceTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditActionAsShouldAcceptTrustedAirflowActorWithoutUsingRequestIdentity() {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        ObjectProvider<AuditForwarderService> provider = mockProvider(forwarder);
        AuditService service = newService(provider, mock(PortalSessionRegistry.class));
        Instant occurredAt = Instant.parse("2026-08-01T02:03:04Z");

        service.auditActionAs(
            "service:dts-airflow",
            "dag-run/2026-08-01T02:03:04Z",
            occurredAt,
            "AIRFLOW_DAG_RECONCILE",
            AuditStage.SUCCESS,
            "dag-17",
            Map.of("summary", "Airflow reconciliation complete", "password", "must-not-persist")
        );

        ArgumentCaptor<AuditForwarderService.PendingAuditEvent> captor = ArgumentCaptor.forClass(
            AuditForwarderService.PendingAuditEvent.class
        );
        verify(forwarder).recordTrustedMachine(captor.capture(), eq("dag-run/2026-08-01T02:03:04Z"));
        AuditForwarderService.PendingAuditEvent event = captor.getValue();
        assertThat(event.actor).isEqualTo("_system:airflow");
        assertThat(event.actorRole).isEqualTo("MACHINE");
        assertThat(event.occurredAt).isEqualTo(occurredAt);
        assertThat(event.clientIp).isNull();
        assertThat(event.requestUri).isNull();
    }

    @Test
    void auditActionAsStrictMustReturnDurableMachineReceipt() {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        ObjectProvider<AuditForwarderService> provider = mockProvider(forwarder);
        AuditService service = newService(provider, mock(PortalSessionRegistry.class));
        UUID receipt = UUID.fromString("56b7b954-5fd6-4e89-a5e5-dab8f78b91d9");
        when(forwarder.recordTrustedMachineStrict(any(), eq("run-17"))).thenReturn(receipt);

        assertThat(
            service.auditActionAsStrict(
                "airflow",
                "run-17",
                Instant.parse("2026-08-01T02:03:04Z"),
                "MODEL_MATERIALIZATION_RUNTIME_SPEC_BLOCKED",
                AuditStage.FAIL,
                "dispatch-17",
                Map.of("errorCode", "MODEL_SOURCE_AVAILABILITY_FENCE_ACTIVE")
            )
        ).isEqualTo(receipt);
        verify(forwarder).recordTrustedMachineStrict(any(), eq("run-17"));
    }

    @Test
    void auditActionAsStrictMustRejectBlankActionCodeBeforeResolvingForwarder() {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        ObjectProvider<AuditForwarderService> provider = mockProvider(forwarder);
        AuditService service = newService(provider, mock(PortalSessionRegistry.class));

        assertThatThrownBy(() ->
            service.auditActionAsStrict(
                "airflow",
                "run-blank-action",
                Instant.parse("2026-08-01T02:03:04Z"),
                " ",
                AuditStage.SUCCESS,
                "dispatch-17",
                Map.of()
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("actionCode is required");

        verify(provider, never()).getIfAvailable();
        verify(forwarder, never()).recordTrustedMachineStrict(any(), any());
    }

    @Test
    void auditActionAsShouldRejectBlankOrUntrustedMachineActorBeforeResolvingForwarder() {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        ObjectProvider<AuditForwarderService> provider = mockProvider(forwarder);
        AuditService service = newService(provider, mock(PortalSessionRegistry.class));
        Instant occurredAt = Instant.parse("2026-08-01T02:03:04Z");

        assertThatThrownBy(() ->
            service.auditActionAs(" ", "run-1", occurredAt, "SCHEDULE_RUN", AuditStage.SUCCESS, "schedule-1", Map.of())
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("machineActor is required");
        assertThatThrownBy(() ->
            service.auditActionAs(
                "service:dts-analytics",
                "run-1",
                occurredAt,
                "SCHEDULE_RUN",
                AuditStage.SUCCESS,
                "schedule-1",
                Map.of()
            )
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not trusted");

        verify(provider, never()).getIfAvailable();
        verify(forwarder, never()).recordTrustedMachine(any(), any());
    }

    @Test
    void auditActionShouldKeepExistingAuthenticatedUserFlow() {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        ObjectProvider<AuditForwarderService> provider = mockProvider(forwarder);
        AuditService service = newService(provider, mock(PortalSessionRegistry.class));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "alice",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
            )
        );

        service.auditAction("USER_ACTION", AuditStage.SUCCESS, "resource-1", Map.of("summary", "user action"));

        ArgumentCaptor<AuditForwarderService.PendingAuditEvent> captor = ArgumentCaptor.forClass(
            AuditForwarderService.PendingAuditEvent.class
        );
        verify(forwarder).record(captor.capture());
        assertThat(captor.getValue().actor).isEqualTo("alice");
        assertThat(captor.getValue().actorRole).isEqualTo("ROLE_USER");
    }

    @Test
    void auditActionStrictReturnsReceiptAndFailsForMissingActorOrForwarder() {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        ObjectProvider<AuditForwarderService> provider = mockProvider(forwarder);
        AuditService service = newService(provider, mock(PortalSessionRegistry.class));

        assertThatThrownBy(() ->
            service.auditActionStrict(
                "AUDIT_OUTBOX_REPLAY_REQUESTED",
                AuditStage.SUCCESS,
                "outbox-1",
                Map.of("operator", "payload-actor-must-not-be-trusted")
            )
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("actor");
        verify(provider, never()).getIfAvailable();

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "alice",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_OP_ADMIN"))
            )
        );
        UUID receiptId = UUID.fromString("41f86eb0-a55a-44e6-b009-845679d0e042");
        when(forwarder.recordStrict(any())).thenReturn(receiptId);
        assertThat(
            service.auditActionStrict(
                "AUDIT_OUTBOX_REPLAY_REQUESTED",
                AuditStage.SUCCESS,
                "outbox-1",
                Map.of("reasonCode", "OPERATOR_RETRY", "actorName", "spoofed-display-name")
            )
        ).isEqualTo(receiptId);
        ArgumentCaptor<AuditForwarderService.PendingAuditEvent> strictEvent = ArgumentCaptor.forClass(
            AuditForwarderService.PendingAuditEvent.class
        );
        verify(forwarder).recordStrict(strictEvent.capture());
        assertThat(strictEvent.getValue().actor).isEqualTo("alice");
        assertThat(strictEvent.getValue().actorName).isNotEqualTo("spoofed-display-name");

        ObjectProvider<AuditForwarderService> missingProvider = mockProvider(null);
        AuditService missingForwarder = newService(missingProvider, mock(PortalSessionRegistry.class));
        assertThatThrownBy(() ->
            missingForwarder.auditActionStrict(
                "AUDIT_OUTBOX_REPLAY_REQUESTED",
                AuditStage.SUCCESS,
                "outbox-1",
                Map.of("reasonCode", "OPERATOR_RETRY")
            )
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("not available");
        missingForwarder.auditAction(
            "USER_ACTION",
            AuditStage.SUCCESS,
            "resource-1",
            Map.of("summary", "ordinary best-effort audit")
        );
    }

    @Test
    void strictAuditMustFailClosedWhenRequestContextEnrichmentFails() {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        ObjectProvider<AuditForwarderService> provider = mockProvider(forwarder);
        PkiContextEnricher enricher = mock(PkiContextEnricher.class);
        doThrow(new IllegalStateException("malformed-certificate-secret"))
            .when(enricher)
            .enrichWithPkiContext(any(), any(), any());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "alice",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_OP_ADMIN"))
            )
        );
        AuditService service = newService(provider, mock(PortalSessionRegistry.class), enricher);

        assertThatThrownBy(() ->
            service.auditActionStrict(
                "AUDIT_OUTBOX_REPLAY_REQUESTED",
                AuditStage.SUCCESS,
                "outbox-1",
                Map.of("reasonCode", "OPERATOR_RETRY")
            )
        )
            .isInstanceOf(AuditService.AuditContextEnrichmentException.class)
            .hasMessage("AUDIT_CONTEXT_ENRICHMENT_FAILED")
            .hasRootCauseMessage("malformed-certificate-secret");
        verify(forwarder, never()).recordStrict(any());
    }

    @Test
    void bestEffortAuditMustPersistAnExplicitEnrichmentFailureMarker() {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        ObjectProvider<AuditForwarderService> provider = mockProvider(forwarder);
        PkiContextEnricher enricher = mock(PkiContextEnricher.class);
        doThrow(new IllegalStateException("malformed-certificate-secret"))
            .when(enricher)
            .enrichWithPkiContext(any(), any(), any());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "alice",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
            )
        );
        AuditService service = newService(provider, mock(PortalSessionRegistry.class), enricher);

        service.auditAction("USER_ACTION", AuditStage.SUCCESS, "resource-1", Map.of("summary", "action"));

        ArgumentCaptor<AuditForwarderService.PendingAuditEvent> captor = ArgumentCaptor.forClass(
            AuditForwarderService.PendingAuditEvent.class
        );
        verify(forwarder).record(captor.capture());
        assertThat(captor.getValue().payload).isInstanceOf(Map.class);
        Map<?, ?> recordedPayload = (Map<?, ?>) captor.getValue().payload;
        assertThat(recordedPayload.get("contextEnrichmentStatus")).isEqualTo("FAILED");
        assertThat(recordedPayload.get("contextEnrichmentErrorCode")).isEqualTo("AUDIT_CONTEXT_ENRICHMENT_FAILED");
        assertThat(recordedPayload.containsValue("malformed-certificate-secret")).isFalse();
    }

    @Test
    void recordAsShouldPreferHumanPayloadActorWhenPrimaryActorIsService() {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        ObjectProvider<AuditForwarderService> provider = mockProvider(forwarder);
        PortalSessionRegistry registry = mock(PortalSessionRegistry.class);
        when(registry.resolveDisplayName("opadmin")).thenReturn(Optional.of("运维管理员"));

        AuditService service = newService(provider, registry);

        service.recordAs(
            "service:dts-airflow",
            "查看数据源列表",
            "platform.infra",
            "infra.datasource",
            "list",
            "SUCCESS",
            Map.of("summary", "查看数据源列表", "operator", "opadmin"),
            null
        );

        ArgumentCaptor<AuditForwarderService.PendingAuditEvent> captor = ArgumentCaptor.forClass(
            AuditForwarderService.PendingAuditEvent.class
        );
        verify(forwarder).record(captor.capture());
        AuditForwarderService.PendingAuditEvent event = captor.getValue();
        assertThat(event.actor).isEqualTo("opadmin");
        assertThat(event.actorName).isEqualTo("运维管理员");
    }

    @Test
    void recordAsShouldNotGuessActorFromSummaryOrStatusFields() {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        ObjectProvider<AuditForwarderService> provider = mockProvider(forwarder);

        AuditService service = newService(provider, mock(PortalSessionRegistry.class));

        service.recordAs(
            "service:dts-analytics",
            "执行",
            "system",
            "sql",
            "query",
            "SUCCESS",
            Map.of("summary", "SQL 查询完成", "database", "POSTGRESQL", "result", "SUCCESS"),
            null
        );

        verify(provider, never()).getIfAvailable();
        verify(forwarder, never()).record(org.mockito.ArgumentMatchers.any(AuditForwarderService.PendingAuditEvent.class));
    }

    @Test
    void recordAsShouldUsePayloadClientIpWhenCurrentRequestOnlyHasContainerIp() {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        ObjectProvider<AuditForwarderService> provider = mockProvider(forwarder);
        PkiContextEnricher enricher = mock(PkiContextEnricher.class);
        when(enricher.resolveClientIp(any())).thenReturn("172.19.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        AuditService service = newService(provider, mock(PortalSessionRegistry.class), enricher);

        service.recordAs(
            "alice",
            "AUTH LOGIN",
            "platform",
            "portal_user",
            "alice",
            "SUCCESS",
            Map.of("summary", "业务端登录成功：alice", "clientIp", "10.20.0.1"),
            null
        );

        ArgumentCaptor<AuditForwarderService.PendingAuditEvent> captor = ArgumentCaptor.forClass(
            AuditForwarderService.PendingAuditEvent.class
        );
        verify(forwarder).record(captor.capture());
        assertThat(captor.getValue().clientIp).isEqualTo("10.20.0.1");
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<AuditForwarderService> mockProvider(AuditForwarderService forwarder) {
        ObjectProvider<AuditForwarderService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(forwarder);
        return provider;
    }

    private AuditService newService(ObjectProvider<AuditForwarderService> provider, PortalSessionRegistry registry) {
        return newService(provider, registry, mock(PkiContextEnricher.class));
    }

    private AuditService newService(ObjectProvider<AuditForwarderService> provider, PortalSessionRegistry registry, PkiContextEnricher enricher) {
        return new AuditService(
            provider,
            mock(AuditActionCatalog.class),
            registry,
            new ObjectMapper(),
            new OperationTypeNormalizer(),
            enricher
        );
    }
}
