package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository.ModelSpecReference;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository.StoredRevision;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository.StoredUnit;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository.UnitDependent;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.ExpectedVersion;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitCommand;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitStatus;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecDomainReadAccessPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeasurementUnitApplicationServiceTest {

    private static final String TENANT = "server-tenant";
    private static final UUID UNIT_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID BASE_ID = UUID.fromString("60000000-0000-0000-0000-000000000002");
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID HIDDEN_MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID HIDDEN_DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-07-19T06:00:00Z");

    @Mock
    private MeasurementUnitRepository repository;

    @Mock
    private ModelSpecDomainReadAccessPort domainReadAccess;

    private MeasurementUnitSnapshotCodec codec;
    private MeasurementUnitApplicationService service;

    @BeforeEach
    void setUp() {
        codec = new MeasurementUnitSnapshotCodec(new ObjectMapper().findAndRegisterModules());
        service = new MeasurementUnitApplicationService(
            repository,
            codec,
            domainReadAccess,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> UNIT_ID,
            TENANT
        );
    }

    @Test
    void createsAStableUnitAndAppendsItsInitialSnapshot() {
        MeasurementUnitCommand command = command("kg", "千克", "MASS", BigDecimal.ONE, null);
        when(repository.findByCode("KG")).thenReturn(Optional.empty());
        when(repository.insertCurrent(any(), eq("alice"))).thenReturn(1);

        MeasurementUnitView created = service.create("alice", command);

        assertThat(created.id()).isEqualTo(UNIT_ID);
        assertThat(created.code()).isEqualTo("KG");
        assertThat(created.status()).isEqualTo(MeasurementUnitStatus.ACTIVE);
        assertThat(created.version()).isEqualTo(1);
        assertThat(created.checksum()).matches("[0-9a-f]{64}");
        verify(repository).insertRevision(eq(created), anyString(), eq("alice"));
    }

    @Test
    void rejectsBaseUnitsFromAnotherQuantityKindAndCyclesWithoutLeakingTheirBody() {
        MeasurementUnitView current = view(UNIT_ID, "CM", "LENGTH", new BigDecimal("0.01"), BASE_ID, 1);
        StoredUnit storedCurrent = stored(current);
        MeasurementUnitView massBase = view(BASE_ID, "KG", "MASS", BigDecimal.ONE, null, 1);
        when(repository.findCurrent(UNIT_ID)).thenReturn(Optional.of(storedCurrent));
        when(repository.findCurrent(BASE_ID)).thenReturn(Optional.of(stored(massBase)));

        assertThatThrownBy(
            () -> service.update("alice", UNIT_ID, expected(current), command("cm", "厘米", "LENGTH", new BigDecimal("0.01"), BASE_ID))
        )
            .isInstanceOf(MeasurementUnitException.class)
            .satisfies(error -> {
                MeasurementUnitException invalid = (MeasurementUnitException) error;
                assertThat(invalid.code()).isEqualTo("MEASUREMENT_UNIT_BASE_INVALID");
                assertThat(invalid.getMessage()).doesNotContain("KG").doesNotContain("MASS");
            });

        MeasurementUnitView lengthBase = view(BASE_ID, "M", "LENGTH", BigDecimal.ONE, UNIT_ID, 1);
        when(repository.findCurrent(BASE_ID)).thenReturn(Optional.of(stored(lengthBase)));
        assertThatThrownBy(
            () -> service.update("alice", UNIT_ID, expected(current), command("cm", "厘米", "LENGTH", new BigDecimal("0.01"), BASE_ID))
        )
            .isInstanceOf(MeasurementUnitException.class)
            .extracting(error -> ((MeasurementUnitException) error).code())
            .isEqualTo("MEASUREMENT_UNIT_BASE_CYCLE");
    }

    @Test
    void updatesAndDeactivatesWithCasWhileEveryMutationAppendsARevision() {
        MeasurementUnitView current = view(UNIT_ID, "KG", "MASS", BigDecimal.ONE, null, 1);
        when(repository.findCurrent(UNIT_ID)).thenReturn(Optional.of(stored(current)));
        when(repository.findByCode("KG")).thenReturn(Optional.of(stored(current)));
        when(repository.compareAndSet(eq(stored(current)), any(), eq("alice"))).thenReturn(1);

        MeasurementUnitView updated = service.update(
            "alice",
            UNIT_ID,
            expected(current),
            command("kg", "千克（SI）", "MASS", BigDecimal.ONE, null)
        );

        assertThat(updated.version()).isEqualTo(2);
        assertThat(updated.name()).isEqualTo("千克（SI）");
        verify(repository).insertRevision(eq(updated), anyString(), eq("alice"));

        when(repository.findCurrent(UNIT_ID)).thenReturn(Optional.of(stored(updated)));
        when(repository.compareAndSet(eq(stored(updated)), any(), eq("alice"))).thenReturn(1);
        MeasurementUnitView deactivated = service.deactivate("alice", UNIT_ID, expected(updated));

        assertThat(deactivated.status()).isEqualTo(MeasurementUnitStatus.INACTIVE);
        assertThat(deactivated.version()).isEqualTo(3);
        verify(repository).insertRevision(eq(deactivated), anyString(), eq("alice"));
    }

    @Test
    void returnsVersionSnapshotsAndRedactsRestrictedModelReferences() {
        MeasurementUnitView current = view(UNIT_ID, "KG", "MASS", BigDecimal.ONE, null, 2);
        MeasurementUnitView first = view(UNIT_ID, "KG", "MASS", BigDecimal.ONE, null, 1);
        when(repository.findCurrent(UNIT_ID)).thenReturn(Optional.of(stored(current)));
        when(repository.listRevisions(UNIT_ID))
            .thenReturn(List.of(new StoredRevision(UNIT_ID, 1, first.checksum(), codec.write(first), NOW)));
        when(repository.listUnitDependents(UNIT_ID))
            .thenReturn(List.of(new UnitDependent(BASE_ID, "G", "克", 1, MeasurementUnitStatus.ACTIVE)));
        when(repository.listModelSpecReferences(TENANT, UNIT_ID))
            .thenReturn(
                List.of(
                    new ModelSpecReference(MODEL_ID, UUID.randomUUID(), DOMAIN_ID, "visible_model", "DRAFT", 4, 2),
                    new ModelSpecReference(HIDDEN_MODEL_ID, UUID.randomUUID(), HIDDEN_DOMAIN_ID, "secret_model", "DRAFT", 3, 1)
                )
            );
        when(domainReadAccess.canRead(DOMAIN_ID)).thenReturn(true);
        when(domainReadAccess.canRead(HIDDEN_DOMAIN_ID)).thenReturn(false);

        assertThat(service.versions(UNIT_ID)).extracting(MeasurementUnitView::version).containsExactly(1);
        var impact = service.references(UNIT_ID);

        assertThat(impact.totalReferences()).isEqualTo(3);
        assertThat(impact.restrictedReferences()).isEqualTo(1);
        assertThat(impact.items()).filteredOn(item -> item.restricted())
            .allSatisfy(item -> {
                assertThat(item.resourceId()).isNull();
                assertThat(item.displayName()).isNull();
                assertThat(item.repairRoute()).isNull();
            });
        assertThat(impact.items()).filteredOn(item -> MODEL_ID.equals(item.resourceId()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.driftStatus()).isEqualTo("CURRENT");
                assertThat(item.repairRoute()).contains("/modeling/models/" + MODEL_ID);
            });
        assertThat(impact.items()).noneSatisfy(item -> assertThat(item.displayName()).isEqualTo("secret_model"));
    }

    private MeasurementUnitCommand command(
        String code,
        String name,
        String quantityKind,
        BigDecimal factor,
        UUID baseUnitRef
    ) {
        return new MeasurementUnitCommand(code, name, code, quantityKind, factor, baseUnitRef, 6);
    }

    private MeasurementUnitView view(
        UUID id,
        String code,
        String quantityKind,
        BigDecimal factor,
        UUID baseUnitRef,
        int version
    ) {
        return codec.toView(
            id,
            new MeasurementUnitCommand(code, code + " name", code, quantityKind, factor, baseUnitRef, 6),
            MeasurementUnitStatus.ACTIVE,
            version,
            NOW,
            NOW
        );
    }

    private static StoredUnit stored(MeasurementUnitView view) {
        return new StoredUnit(
            view.id(),
            view.code(),
            view.name(),
            view.symbol(),
            view.quantityKind(),
            view.conversionFactor(),
            view.baseUnitRef(),
            view.precision(),
            view.status(),
            view.version(),
            view.checksum(),
            view.createdAt(),
            view.updatedAt()
        );
    }

    private static ExpectedVersion expected(MeasurementUnitView view) {
        return new ExpectedVersion(view.id(), view.version(), view.checksum());
    }
}
