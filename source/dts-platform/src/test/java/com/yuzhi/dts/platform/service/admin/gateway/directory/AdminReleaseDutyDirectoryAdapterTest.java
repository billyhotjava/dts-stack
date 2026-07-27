package com.yuzhi.dts.platform.service.admin.gateway.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.PlatformOutboundAdminProperties;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminReleaseDutyDirectoryAdapter.DutyCheck;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayException;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTarget;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTransport;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ReleaseDutyDirectoryUnavailableException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;

@ExtendWith(MockitoExtension.class)
class AdminReleaseDutyDirectoryAdapterTest {

    @Mock
    private AdminGatewayTransport transport;

    private PlatformOutboundAdminProperties properties;
    private AdminReleaseDutyDirectoryAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new PlatformOutboundAdminProperties();
        properties.setEnabled(true);
        adapter = new AdminReleaseDutyDirectoryAdapter(transport, properties);
    }

    @Test
    void returnsTheAuthoritativeCurrentDuty() {
        when(
            transport.exchangeEnvelopeData(
                eq(AdminGatewayTarget.API),
                eq(HttpMethod.GET),
                eq(
                    "/platform/internal/release-duties/check?actorId=alice&duty=MODEL_MAINTAINER"
                ),
                eq(null),
                any(ParameterizedTypeReference.class),
                any()
            )
        )
            .thenReturn(
                new DutyCheck(
                    "alice",
                    "MODEL_MAINTAINER",
                    true,
                    Instant.parse("2026-07-28T00:00:00Z")
                )
            );

        assertThat(
            adapter.hasDuty(
                "alice",
                DeliveryActorRole.MODEL_MAINTAINER
            )
        )
            .isTrue();
    }

    @Test
    void treatsAUserMissingFromTheAuthoritativeDirectoryAsRevoked() {
        when(
            transport.exchangeEnvelopeData(
                eq(AdminGatewayTarget.API),
                eq(HttpMethod.GET),
                any(),
                eq(null),
                any(ParameterizedTypeReference.class),
                any()
            )
        )
            .thenThrow(
                new AdminGatewayException(
                    "missing",
                    404,
                    "/api/platform/internal/release-duties/check"
                )
            );

        assertThat(
            adapter.hasDuty(
                "alice",
                DeliveryActorRole.MODEL_MAINTAINER
            )
        )
            .isFalse();
    }

    @Test
    void failsClosedForForbiddenTimeoutOrDisabledAdminGateway() {
        when(
            transport.exchangeEnvelopeData(
                eq(AdminGatewayTarget.API),
                eq(HttpMethod.GET),
                any(),
                eq(null),
                any(ParameterizedTypeReference.class),
                any()
            )
        )
            .thenThrow(
                new AdminGatewayException(
                    "forbidden",
                    403,
                    "/api/platform/internal/release-duties/check"
                )
            );

        assertThatThrownBy(() ->
            adapter.hasDuty(
                "alice",
                DeliveryActorRole.MODEL_MAINTAINER
            )
        )
            .isInstanceOf(ReleaseDutyDirectoryUnavailableException.class);

        properties.setEnabled(false);
        assertThatThrownBy(() ->
            adapter.hasDuty(
                "alice",
                DeliveryActorRole.MODEL_MAINTAINER
            )
        )
            .isInstanceOf(ReleaseDutyDirectoryUnavailableException.class);
    }
}
