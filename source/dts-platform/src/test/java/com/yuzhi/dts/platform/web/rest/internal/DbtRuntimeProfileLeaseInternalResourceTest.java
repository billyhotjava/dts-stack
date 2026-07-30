package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileException;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService.LeaseView;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DbtRuntimeProfileLeaseInternalResourceTest {

    private static final UUID LEASE_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void consumesRenewsAndReleasesOnlyByOpaqueLeaseId() {
        DbtRuntimeProfileLeaseService leases = mock(
            DbtRuntimeProfileLeaseService.class
        );
        LeaseView view = new LeaseView(
            LEASE_ID,
            "dev",
            Instant.parse("2026-07-27T13:10:00Z"),
            "credential-v3"
        );
        when(leases.consume(LEASE_ID)).thenReturn(view);
        when(leases.renew(LEASE_ID)).thenReturn(view);
        var resource = new DbtRuntimeProfileLeaseInternalResource(
            leases
        );

        assertThat(resource.consume(LEASE_ID)).isEqualTo(view);
        assertThat(resource.renew(LEASE_ID)).isEqualTo(view);
        assertThat(resource.release(LEASE_ID).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        verify(leases).consume(LEASE_ID);
        verify(leases).renew(LEASE_ID);
        verify(leases).release(LEASE_ID);
    }

    @Test
    void mapsMissingAndConsumedLeasesWithoutLeakingCause() {
        DbtRuntimeProfileLeaseService leases = mock(
            DbtRuntimeProfileLeaseService.class
        );
        var resource = new DbtRuntimeProfileLeaseInternalResource(
            leases
        );

        var missing = resource.handle(
            new DbtRuntimeProfileException(
                "DBT_PROFILE_LEASE_NOT_FOUND",
                "Runtime profile lease does not exist"
            )
        );
        var consumed = resource.handle(
            new DbtRuntimeProfileException(
                "DBT_PROFILE_LEASE_ALREADY_CONSUMED",
                "Runtime profile lease has already been consumed"
            )
        );

        assertThat(missing.getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody())
            .containsEntry("code", "DBT_PROFILE_LEASE_NOT_FOUND")
            .doesNotContainKey("cause");
        assertThat(consumed.getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void serializesTheCrossLanguageLeaseIdAsProfileLeaseId()
        throws Exception {
        LeaseView view = new LeaseView(
            LEASE_ID,
            "dev",
            Instant.parse("2026-07-27T13:10:00Z"),
            "credential-v3"
        );

        String json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .writeValueAsString(view);

        assertThat(json)
            .contains(
                "\"profileLeaseId\":\"10000000-0000-0000-0000-000000000001\""
            )
            .doesNotContain("\"leaseId\"");
    }
}
