package com.yuzhi.dts.platform.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditActionCatalog;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuditServiceTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
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
