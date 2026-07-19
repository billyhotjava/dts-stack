package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.governance.StdCodeDirectory;
import com.yuzhi.dts.platform.domain.modeling.MetadataStandard;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository.StoredUnit;
import com.yuzhi.dts.platform.repository.governance.StdCodeDirectoryRepository;
import com.yuzhi.dts.platform.repository.modeling.MetadataStandardRepository;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.StandardBinding;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStandardEvidencePort.StandardEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class GovernanceModelSpecStandardEvidenceAdapterTest {

    private static final UUID UNIT_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID ELEMENT_ID = UUID.fromString("60000000-0000-0000-0000-000000000002");

    @Test
    void reportsCurrentOnlyWhenTheActiveOwnerVersionMatches() {
        MeasurementUnitRepository units = mock(MeasurementUnitRepository.class);
        when(units.findCurrent(UNIT_ID)).thenReturn(Optional.of(unit(2, MeasurementUnitStatus.ACTIVE)));

        StandardEvidence evidence = adapter(mock(MetadataStandardRepository.class), mock(StdCodeDirectoryRepository.class), units)
            .evaluate("tenant-a", modelWithUnitVersion(2));

        assertThat(evidence).isEqualTo(StandardEvidence.CURRENT);
    }

    @Test
    void reportsStaleForOwnerVersionDriftOrInactiveOwner() {
        MeasurementUnitRepository units = mock(MeasurementUnitRepository.class);
        when(units.findCurrent(UNIT_ID)).thenReturn(Optional.of(unit(2, MeasurementUnitStatus.ACTIVE)));
        GovernanceModelSpecStandardEvidenceAdapter adapter = adapter(
            mock(MetadataStandardRepository.class),
            mock(StdCodeDirectoryRepository.class),
            units
        );

        assertThat(adapter.evaluate("tenant-a", modelWithUnitVersion(1))).isEqualTo(StandardEvidence.STALE);

        when(units.findCurrent(UNIT_ID)).thenReturn(Optional.of(unit(2, MeasurementUnitStatus.INACTIVE)));
        assertThat(adapter.evaluate("tenant-a", modelWithUnitVersion(2))).isEqualTo(StandardEvidence.STALE);
    }

    @Test
    void reportsUnknownWhenTheProfessionalOwnerCannotBeRead() {
        MeasurementUnitRepository units = mock(MeasurementUnitRepository.class);
        when(units.findCurrent(UNIT_ID)).thenThrow(new DataAccessResourceFailureException("owner unavailable"));

        StandardEvidence evidence = adapter(mock(MetadataStandardRepository.class), mock(StdCodeDirectoryRepository.class), units)
            .evaluate("tenant-a", modelWithUnitVersion(2));

        assertThat(evidence).isEqualTo(StandardEvidence.UNKNOWN);
    }

    @Test
    void resolvesDataElementAndReferenceCodeOwnerVersions() {
        MetadataStandardRepository elements = mock(MetadataStandardRepository.class);
        StdCodeDirectoryRepository codes = mock(StdCodeDirectoryRepository.class);
        MetadataStandard element = new MetadataStandard();
        element.setId(ELEMENT_ID);
        element.setVersion(3);
        StdCodeDirectory code = new StdCodeDirectory();
        code.setCodeTypeId("PAYMENT_STATUS");
        code.setVersion("v4");
        code.setStatus(1);
        when(elements.findById(ELEMENT_ID)).thenReturn(Optional.of(element));
        when(codes.findById("PAYMENT_STATUS")).thenReturn(Optional.of(code));

        GovernanceModelSpecStandardEvidenceAdapter adapter = adapter(elements, codes, mock(MeasurementUnitRepository.class));

        assertThat(adapter.evaluate("tenant-a", modelWithElementVersion(3))).isEqualTo(StandardEvidence.CURRENT);
        assertThat(adapter.evaluate("tenant-a", modelWithReferenceCodeVersion(4))).isEqualTo(StandardEvidence.CURRENT);
        assertThat(adapter.evaluate("tenant-a", modelWithElementVersion(2))).isEqualTo(StandardEvidence.STALE);
        assertThat(adapter.evaluate("tenant-a", modelWithReferenceCodeVersion(3))).isEqualTo(StandardEvidence.STALE);
    }

    @Test
    void rejectsInactiveOrNonNumericReferenceCodeOwnerVersions() {
        StdCodeDirectoryRepository codes = mock(StdCodeDirectoryRepository.class);
        StdCodeDirectory code = new StdCodeDirectory();
        code.setCodeTypeId("PAYMENT_STATUS");
        code.setVersion("2026.07");
        code.setStatus(1);
        when(codes.findById("PAYMENT_STATUS")).thenReturn(Optional.of(code));
        GovernanceModelSpecStandardEvidenceAdapter adapter = adapter(
            mock(MetadataStandardRepository.class),
            codes,
            mock(MeasurementUnitRepository.class)
        );

        assertThat(adapter.evaluate("tenant-a", modelWithReferenceCodeVersion(1))).isEqualTo(StandardEvidence.STALE);
        code.setVersion("v1");
        code.setStatus(0);
        assertThat(adapter.evaluate("tenant-a", modelWithReferenceCodeVersion(1))).isEqualTo(StandardEvidence.STALE);
    }

    private static ModelSpecView modelWithUnitVersion(int version) {
        ModelSpecView model = mock(ModelSpecView.class);
        ModelField field = new ModelField("amount", "decimal", false, "source.amount", FieldRole.MEASURE, "INTERNAL");
        when(model.fields()).thenReturn(List.of(field));
        when(model.standardBindings())
            .thenReturn(List.of(new StandardBinding("amount", null, null, null, null, UNIT_ID, version, "INTERNAL")));
        return model;
    }

    private static ModelSpecView modelWithElementVersion(int version) {
        return modelWithBinding(new StandardBinding("amount", ELEMENT_ID, version, null, null, null, null, "INTERNAL"));
    }

    private static ModelSpecView modelWithReferenceCodeVersion(int version) {
        return modelWithBinding(new StandardBinding("amount", null, null, "PAYMENT_STATUS", version, null, null, "INTERNAL"));
    }

    private static ModelSpecView modelWithBinding(StandardBinding binding) {
        ModelSpecView model = mock(ModelSpecView.class);
        ModelField field = new ModelField("amount", "decimal", false, "source.amount", FieldRole.MEASURE, "INTERNAL");
        when(model.fields()).thenReturn(List.of(field));
        when(model.standardBindings()).thenReturn(List.of(binding));
        return model;
    }

    private static GovernanceModelSpecStandardEvidenceAdapter adapter(
        MetadataStandardRepository elements,
        StdCodeDirectoryRepository codes,
        MeasurementUnitRepository units
    ) {
        return new GovernanceModelSpecStandardEvidenceAdapter(elements, codes, units);
    }

    private static StoredUnit unit(int version, MeasurementUnitStatus status) {
        return new StoredUnit(
            UNIT_ID,
            "UNIT_CNY",
            "人民币",
            "CNY",
            "CURRENCY",
            BigDecimal.ONE,
            null,
            2,
            status,
            version,
            "a".repeat(64),
            Instant.EPOCH,
            Instant.EPOCH
        );
    }
}
