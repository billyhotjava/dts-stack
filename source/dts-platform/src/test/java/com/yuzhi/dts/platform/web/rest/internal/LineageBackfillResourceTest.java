package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class LineageBackfillResourceTest {

    @Test
    void dbtModelDryRunReturnsNonMutatingNinetyDayPlan() {
        LineageBackfillResource resource = new LineageBackfillResource();
        Instant since = Instant.parse("2026-02-17T00:00:00Z");

        var response = resource.dryRun("dbt-model", since);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().type()).isEqualTo("DBT_MODEL");
        assertThat(response.getBody().dryRun()).isTrue();
        assertThat(response.getBody().mutatesData()).isFalse();
        assertThat(response.getBody().since()).isEqualTo(since);
        assertThat(response.getBody().lookbackDays()).isEqualTo(90);
        assertThat(response.getBody().dataSource()).isEqualTo("dbt artifacts");
        assertThat(response.getBody().plannedSteps()).anyMatch(step -> step.contains("no mutation"));
    }

    @Test
    void addaxRunDryRunUsesThirtyDayOperationalWindow() {
        LineageBackfillResource resource = new LineageBackfillResource();

        var response = resource.dryRun("ADDAX_RUN", null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().type()).isEqualTo("ADDAX_RUN");
        assertThat(response.getBody().lookbackDays()).isEqualTo(30);
        assertThat(response.getBody().dataSource()).contains("Addax");
        assertThat(response.getBody().mutatesData()).isFalse();
    }

    @Test
    void unsupportedTypeReturnsBadRequestWithoutMutationPlan() {
        LineageBackfillResource resource = new LineageBackfillResource();

        var response = resource.dryRun("unknown", null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().mutatesData()).isFalse();
        assertThat(response.getBody().warnings()).contains("supported types: DBT_MODEL, ADDAX_RUN, OPENLINEAGE_EVENT, MANUAL_DECLARATION");
    }

    @Test
    void requiresServiceInternalAuthority() {
        PreAuthorize preAuthorize = LineageBackfillResource.class.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains(AuthoritiesConstants.SERVICE_INTERNAL);
    }
}
