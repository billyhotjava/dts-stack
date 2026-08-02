package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DbtImplementationDraftRetentionServiceTest {

    @Test
    void purgesExpiredRowsInBoundedBatchesCleansOrphansAndStrictlyAuditsCounts() {
        DbtImplementationDraftRepository repository = org.mockito.Mockito.mock(DbtImplementationDraftRepository.class);
        AdvancedDbtDraftStaticValidator validator = org.mockito.Mockito.mock(AdvancedDbtDraftStaticValidator.class);
        DbtImplementationDraftAuditRecorder audit = org.mockito.Mockito.mock(DbtImplementationDraftAuditRecorder.class);
        Instant now = Instant.parse("2026-08-02T00:00:00Z");
        when(repository.purgeExpiredBatch(now, 2)).thenReturn(2, 1);
        when(validator.purgeOrphanedWorkspaces(now.minus(Duration.ofHours(24)), 2)).thenReturn(1);
        DbtImplementationDraftRetentionService service = new DbtImplementationDraftRetentionService(
            repository,
            validator,
            audit,
            Clock.fixed(now, ZoneOffset.UTC),
            2,
            5,
            Duration.ofHours(24)
        );

        DbtImplementationDraftRetentionService.PurgeResult result = service.purgeExpired();

        assertThat(result.draftCount()).isEqualTo(3);
        assertThat(result.workspaceCount()).isEqualTo(1);
        verify(repository, org.mockito.Mockito.times(2)).purgeExpiredBatch(now, 2);
        verify(audit).recordMachineSuccess(
            eq("MODELING_DBT_DRAFT_PURGE"),
            argThat(identity -> identity.startsWith("dbt-draft-purge:")),
            eq(now),
            eq("dbt-drafts"),
            argThat(payload -> payload.get("draftCount").equals(3) && payload.get("workspaceCount").equals(1))
        );
    }
}
