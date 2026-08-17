package com.yuzhi.dts.platform.service.modeling.serving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.ServingSyncState;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecPlanWriteAccessPort;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogModelSemanticSyncCommandServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String ACTOR = "xiezm";
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-17T06:00:00Z");

    @Test
    void retriesFailedProjectionWithCasAndAuditWithoutChangingServingPointer() {
        Fixture fixture = fixture();
        ServingSyncState failed = state("SYNC_FAILED", 5, 7, "ANALYTICS_SEMANTIC_PUBLISH_UNAVAILABLE");
        ServingSyncState pending = state("SYNC_PENDING", 0, 8, null);
        when(fixture.repository.findSyncState(TENANT, MODEL_ID))
            .thenReturn(Optional.of(failed), Optional.of(pending));
        when(fixture.repository.requestSyncRetry(TENANT, MODEL_ID, 7)).thenReturn(true);

        var result = fixture.service.retry(TENANT, ACTOR, MODEL_ID, 7);

        assertThat(result.replayed()).isFalse();
        assertThat(result.status().syncStatus()).isEqualTo("SYNC_PENDING");
        assertThat(result.status().version()).isEqualTo(8);
        verify(fixture.repository).requestSyncRetry(TENANT, MODEL_ID, 7);
        verify(fixture.audit).auditActionStrict(
            eq("MODELING_SEMANTIC_SYNC_RETRY"),
            any(),
            eq(MODEL_ID.toString()),
            any()
        );
    }

    @Test
    void repeatedRetryIsIdempotentWhenProjectionIsAlreadyPending() {
        Fixture fixture = fixture();
        ServingSyncState pending = state("SYNC_PENDING", 0, 8, null);
        when(fixture.repository.findSyncState(TENANT, MODEL_ID)).thenReturn(Optional.of(pending));

        var result = fixture.service.retry(TENANT, ACTOR, MODEL_ID, 7);

        assertThat(result.replayed()).isTrue();
        assertThat(result.status().version()).isEqualTo(8);
        verify(fixture.repository, never()).requestSyncRetry(any(), any(), any(Long.class));
        verify(fixture.audit, never()).auditActionStrict(any(), any(), any(), any());
    }

    @Test
    void rejectsRetryWhenActorCannotMaintainOwningPlan() {
        Fixture fixture = fixture(false);

        assertThatThrownBy(() -> fixture.service.retry(TENANT, ACTOR, MODEL_ID, 7))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SEMANTIC_SYNC_RETRY_FORBIDDEN");
        verify(fixture.repository, never()).findSyncState(any(), any());
    }

    @Test
    void readReturnsExplicitNotRegisteredStatusForVisibleDraft() {
        Fixture fixture = fixture();
        when(fixture.repository.findSyncState(TENANT, MODEL_ID)).thenReturn(Optional.empty());

        var status = fixture.service.get(TENANT, MODEL_ID);

        assertThat(status.syncStatus()).isEqualTo("NOT_REGISTERED");
        assertThat(status.version()).isZero();
        assertThat(status.servingReady()).isFalse();
    }

    private static Fixture fixture() {
        return fixture(true);
    }

    private static Fixture fixture(boolean canMaintain) {
        CatalogModelServingProjectionRepository repository = mock(CatalogModelServingProjectionRepository.class);
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
        AuditService audit = mock(AuditService.class);
        ModelSpecView model = mock(ModelSpecView.class);
        when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        when(model.planId()).thenReturn(PLAN_ID);
        when(writeAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(canMaintain);
        CatalogModelSemanticSyncCommandService service = new CatalogModelSemanticSyncCommandService(
            repository,
            modelSpecs,
            writeAccess,
            audit,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> UUID.fromString("70000000-0000-0000-0000-000000000001")
        );
        return new Fixture(repository, audit, service);
    }

    private static ServingSyncState state(String status, int attempts, long version, String error) {
        ModelServingProjection projection = new ModelServingProjection(
            TENANT,
            MODEL_ID,
            CatalogAssetType.SEMANTIC_MODEL,
            "semantic-model:" + MODEL_ID,
            null,
            mock(CatalogModelServingContract.ServingRef.class),
            version,
            status,
            NOW
        );
        return new ServingSyncState(projection, attempts, error, null);
    }

    private record Fixture(
        CatalogModelServingProjectionRepository repository,
        AuditService audit,
        CatalogModelSemanticSyncCommandService service
    ) {}
}
