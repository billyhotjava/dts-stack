package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.PlanState;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ExpectedImplementationVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelLifecycleImportedDbtImplementationTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000070");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000070");
    private static final UUID IMPLEMENTATION_ID = UUID.fromString("60000000-0000-0000-0000-000000000070");
    private static final String MODEL_CHECKSUM = "a".repeat(64);
    private static final String IMPLEMENTATION_CHECKSUM = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-07-25T08:00:00Z");

    @Test
    void savesOnlyTheConverterOwnedDbtPayloadAndReturnsTheExactPersistedPins() {
        Fixture fixture = fixture();
        SaveImplementationCommand command = dbtCommand("DBT");
        ImplementationView saved = implementation(command);
        when(
            fixture.lifecycle().saveImportedDbtImplementation(
                "tenant-a",
                "alice",
                fixture.model(),
                ModelStatus.DRAFT,
                "pjm",
                "model.pjm.budget",
                command,
                0,
                null,
                NOW
            )
        ).thenReturn(Optional.of(saved));

        ImplementationView result = fixture.service().saveImportedDbtImplementation(
            "tenant-a",
            "alice",
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 7, MODEL_CHECKSUM),
            ModelStatus.DRAFT,
            new ExpectedImplementationVersion(MODEL_ID, 0, null),
            "pjm",
            "model.pjm.budget",
            command
        );

        assertThat(result)
            .extracting(
                ImplementationView::implementationRevision,
                ImplementationView::implementationChecksum,
                ImplementationView::ownership,
                ImplementationView::inputMode
            )
            .containsExactly(1, IMPLEMENTATION_CHECKSUM, ImplementationMode.DBT_MANAGED, InputMode.GENERATED);
    }

    @Test
    void keepsTheNormalSaveClosedToDbtAndRejectsAnyNonDbtImportGenerator() {
        Fixture fixture = fixture();
        SaveImplementationCommand dbt = dbtCommand("DBT");

        assertThatThrownBy(() ->
            fixture.service().saveImplementation(
                "tenant-a",
                "alice",
                MODEL_ID,
                new ExpectedVersion(MODEL_ID, 7, MODEL_CHECKSUM),
                new ExpectedImplementationVersion(MODEL_ID, 0, null),
                "pjm",
                "model.pjm.budget",
                dbt
            )
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED");

        assertThatThrownBy(() ->
            fixture.service().saveImportedDbtImplementation(
                "tenant-a",
                "alice",
                MODEL_ID,
                new ExpectedVersion(MODEL_ID, 7, MODEL_CHECKSUM),
                ModelStatus.DRAFT,
                new ExpectedImplementationVersion(MODEL_ID, 0, null),
                "pjm",
                "model.pjm.budget",
                dbtCommand("DATE_DIMENSION")
            )
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_IMPORT_IMPLEMENTATION_INVALID");
        verify(fixture.lifecycle(), never()).saveImportedDbtImplementation(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyInt(),
            any(),
            any()
        );
    }

    @Test
    void rejectsAStatusPinThatNoLongerMatchesTheCanonicalModel() {
        Fixture fixture = fixture();

        assertThatThrownBy(() ->
            fixture.service().saveImportedDbtImplementation(
                "tenant-a",
                "alice",
                MODEL_ID,
                new ExpectedVersion(MODEL_ID, 7, MODEL_CHECKSUM),
                ModelStatus.DESIGNING,
                new ExpectedImplementationVersion(MODEL_ID, 0, null),
                "pjm",
                "model.pjm.budget",
                dbtCommand("DBT")
            )
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_STATUS_CONFLICT");
    }

    private static Fixture fixture() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository modelSpecRepository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(7);
        when(model.checksum()).thenReturn(MODEL_CHECKSUM);
        when(model.status()).thenReturn(ModelStatus.DRAFT);
        when(model.modelType()).thenReturn(ModelType.DIMENSION);
        when(model.implementationMode()).thenReturn(ImplementationMode.DBT_MANAGED);
        when(model.materialization()).thenReturn("table");
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(modelSpecRepository.lockPlan("tenant-a", PLAN_ID)).thenReturn(Optional.of(new PlanState(PLAN_ID, "DRAFT")));
        when(writeAccess.canMaintain("tenant-a", PLAN_ID, "alice")).thenReturn(true);
        when(lifecycle.findImplementation("tenant-a", MODEL_ID)).thenReturn(Optional.empty());
        ModelLifecycleService service = new ModelLifecycleService(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            mock(ModelSpecStageGateService.class),
            writeAccess,
            mock(ModelLifecycleCompilerPort.class),
            mock(ModelReleaseRegistrationPort.class),
            mock(ModelLifecycleTestEvidencePort.class),
            mock(ModelLifecyclePublicationService.class),
            mock(ModelingVNextApplicationService.class),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(service, lifecycle, model);
    }

    private static SaveImplementationCommand dbtCommand(String generatorType) {
        return new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(
                new GeneratedInput(
                    generatorType,
                    Map.of(
                        "projectKey",
                        "pjm",
                        "dbtUniqueId",
                        "model.pjm.budget",
                        "resourcePath",
                        "models/dwd/budget.sql",
                        "effectiveSqlChecksum",
                        "c".repeat(64)
                    )
                )
            ),
            List.of(),
            Map.of(),
            ImplementationMode.DBT_MANAGED,
            "table",
            "import-run:model.pjm.budget:impl"
        );
    }

    private static ImplementationView implementation(SaveImplementationCommand command) {
        return new ImplementationView(
            IMPLEMENTATION_ID,
            MODEL_ID,
            PLAN_ID,
            7,
            MODEL_CHECKSUM,
            ImplementationMode.DBT_MANAGED,
            "pjm",
            "model.pjm.budget",
            "ACTIVE",
            1,
            IMPLEMENTATION_CHECKSUM,
            command.inputMode(),
            command.inputs(),
            command.fieldMappings(),
            command.settings(),
            command.materialization()
        );
    }

    private record Fixture(
        ModelLifecycleService service,
        ModelLifecycleRepository lifecycle,
        ModelSpecView model
    ) {}
}
