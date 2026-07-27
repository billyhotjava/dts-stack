package com.yuzhi.dts.platform.service.admin.gateway.directory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yuzhi.dts.platform.config.PlatformOutboundAdminProperties;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayEnvelope;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayException;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayRequestOptions;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTarget;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTransport;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ReleaseDutyDirectoryPort;
import com.yuzhi.dts.platform.service.modeling.ReleaseDutyDirectoryUnavailableException;
import java.time.Instant;
import java.util.Locale;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/** Pairwise service-auth adapter for dts-admin's current Keycloak duty lookup. */
@Component
public class AdminReleaseDutyDirectoryAdapter
    implements ReleaseDutyDirectoryPort {

    private static final ParameterizedTypeReference<
        AdminGatewayEnvelope<DutyCheck>
    > DUTY_CHECK_ENVELOPE = new ParameterizedTypeReference<>() {};

    private final AdminGatewayTransport transport;
    private final PlatformOutboundAdminProperties properties;

    public AdminReleaseDutyDirectoryAdapter(
        AdminGatewayTransport transport,
        PlatformOutboundAdminProperties properties
    ) {
        this.transport = transport;
        this.properties = properties;
    }

    @Override
    public boolean hasDuty(String actorId, DeliveryActorRole duty) {
        if (
            !properties.isEnabled() ||
            !StringUtils.hasText(actorId) ||
            duty == null
        ) {
            throw new ReleaseDutyDirectoryUnavailableException(
                "Current release-duty directory is unavailable"
            );
        }
        String actor = actorId.trim();
        String expectedDuty = duty.name();
        String suffix = UriComponentsBuilder
            .fromPath("/platform/internal/release-duties/check")
            .queryParam("actorId", actor)
            .queryParam("duty", expectedDuty)
            .build()
            .encode()
            .toUriString();
        try {
            DutyCheck response = transport.exchangeEnvelopeData(
                AdminGatewayTarget.API,
                HttpMethod.GET,
                suffix,
                null,
                DUTY_CHECK_ENVELOPE,
                AdminGatewayRequestOptions.defaults()
            );
            if (
                response == null ||
                !actor.equals(response.actorId()) ||
                !expectedDuty.equals(
                    normalize(response.duty())
                )
            ) {
                throw new ReleaseDutyDirectoryUnavailableException(
                    "Current release-duty response did not match the request"
                );
            }
            return response.hasDuty();
        } catch (AdminGatewayException failure) {
            if (
                failure.getUpstreamStatus() != null &&
                failure.getUpstreamStatus() == 404
            ) {
                return false;
            }
            throw new ReleaseDutyDirectoryUnavailableException(
                "Current release-duty lookup failed closed",
                failure
            );
        }
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value)
            ? value.trim().toUpperCase(Locale.ROOT)
            : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DutyCheck(
        String actorId,
        String duty,
        boolean hasDuty,
        Instant checkedAt
    ) {}
}
