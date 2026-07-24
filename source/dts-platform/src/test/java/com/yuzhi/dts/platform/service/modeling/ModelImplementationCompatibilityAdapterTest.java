package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.GenerationStrategy;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceKind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRole;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelImplementationCompatibilityAdapterTest {

    private static final String TENANT = "tenant-a";
    private static final UUID OWNER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void projectsEachLegacyKindButRefusesMixedKinds() {
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelImplementationCompatibilityAdapter adapter = new ModelImplementationCompatibilityAdapter(
            models,
            mock(ModelSpecRepository.class),
            lifecycle,
            mock(ModelSpecSourceValidationPort.class)
        );
        ModelSpecView physical = owner(ModelType.DIMENSION);
        UUID binding = UUID.randomUUID();
        when(physical.sourceRefs()).thenReturn(List.of(source(binding, "v1")));
        assertThat(adapter.projectLegacy(TENANT, physical).inputMode()).isEqualTo(InputMode.PHYSICAL_ASSET);

        ModelSpecView upstream = owner(ModelType.FACT);
        UUID upstreamId = UUID.randomUUID();
        ModelRevisionRef upstreamRef = new ModelRevisionRef(upstreamId, 2);
        ModelSpecView upstreamTarget = owner(ModelType.FACT);
        when(models.revision(TENANT, upstreamRef)).thenReturn(upstreamTarget);
        when(lifecycle.findImplementation(TENANT, upstreamId)).thenReturn(
            Optional.of(implementation(upstreamId, 2, upstreamTarget.checksum()))
        );
        when(upstream.dependsOn()).thenReturn(List.of(upstreamRef));
        assertThat(adapter.projectLegacy(TENANT, upstream).inputMode()).isEqualTo(InputMode.UPSTREAM_MODEL);

        ModelSpecView generated = owner(ModelType.DIMENSION);
        when(generated.generationStrategy()).thenReturn(new GenerationStrategy("DATE_DIMENSION", "calendar"));
        assertThat(adapter.projectLegacy(TENANT, generated).inputMode()).isEqualTo(InputMode.GENERATED);

        when(generated.sourceRefs()).thenReturn(List.of(source(UUID.randomUUID(), "v1")));
        assertThat(adapter.projectLegacy(TENANT, generated)).extracting(ModelImplementationCompatibilityAdapter.CompatibilityProjection::code)
            .isEqualTo("MODEL_IMPLEMENTATION_LEGACY_INPUT_CONFLICT");
    }

    @Test
    void enforcesFourModelInputMatrixAndRegisteredGenerator() {
        ModelImplementationCompatibilityAdapter adapter = adapter(mock(ModelSpecSourceValidationPort.class));
        assertThat(adapter.validate(TENANT, owner(ModelType.DIMENSION), generated("DATE_DIMENSION")).valid()).isTrue();
        assertThat(adapter.validate(TENANT, owner(ModelType.FACT), generated("DATE_DIMENSION")).code())
            .isEqualTo("MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED");
        assertThat(adapter.validate(TENANT, owner(ModelType.SUMMARY), generated("DATE_DIMENSION")).code())
            .isEqualTo("MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED");
        assertThat(adapter.validate(TENANT, owner(ModelType.APPLICATION), generated("DATE_DIMENSION")).code())
            .isEqualTo("MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED");
        assertThat(adapter.validate(TENANT, owner(ModelType.DIMENSION), generated("UNREGISTERED")).code())
            .isEqualTo("MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED");
    }

    @Test
    void rejectsUnconfirmedPhysicalBinding() {
        ModelSpecSourceValidationPort validation = mock(ModelSpecSourceValidationPort.class);
        ModelImplementationCompatibilityAdapter adapter = adapter(validation);
        UUID binding = UUID.randomUUID();
        ModelSpecView owner = owner(ModelType.DIMENSION);
        SourceRef source = source(binding, "v1");
        when(owner.sourceRefs()).thenReturn(List.of(source));
        when(validation.isCurrentBindingForGate(TENANT, PLAN_ID, source)).thenReturn(false);

        assertThat(adapter.validate(TENANT, owner, physical(binding, "v1")).code()).isEqualTo("PHYSICAL_ASSET_NOT_CONFIRMED");
    }

    @Test
    void rejectsStaleCrossPlanSelfAndCyclicUpstreamInput() {
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelImplementationCompatibilityAdapter adapter = new ModelImplementationCompatibilityAdapter(
            models,
            repository,
            lifecycle,
            mock(ModelSpecSourceValidationPort.class)
        );
        UUID upstreamId = UUID.randomUUID();
        ModelSpecView owner = owner(ModelType.APPLICATION);
        assertThat(adapter.validate(TENANT, owner, upstream(OWNER_ID, 1)).code()).isEqualTo("MODEL_IMPLEMENTATION_SELF_REFERENCE");

        ModelSpecView stale = owner(ModelType.SUMMARY);
        when(models.revision(TENANT, new ModelRevisionRef(upstreamId, 1))).thenReturn(stale);
        StoredModelSpec current = mock(StoredModelSpec.class);
        when(current.revision()).thenReturn(2);
        when(repository.findCurrent(TENANT, upstreamId)).thenReturn(Optional.of(current));
        assertThat(adapter.validate(TENANT, owner(ModelType.SUMMARY), upstream(upstreamId, 1)).code()).isEqualTo("MODEL_IMPLEMENTATION_INPUT_STALE");

        ModelSpecView crossPlan = owner(ModelType.SUMMARY);
        UUID otherPlan = UUID.randomUUID();
        when(crossPlan.planId()).thenReturn(otherPlan);
        when(crossPlan.status()).thenReturn(ModelStatus.DRAFT);
        when(crossPlan.modelType()).thenReturn(ModelType.FACT);
        when(crossPlan.layer()).thenReturn(Layer.DWD);
        when(models.revision(TENANT, new ModelRevisionRef(upstreamId, 3))).thenReturn(crossPlan);
        when(current.revision()).thenReturn(3);
        when(lifecycle.findImplementation(TENANT, upstreamId)).thenReturn(
            Optional.of(implementation(upstreamId, 3, "a".repeat(64)))
        );
        assertThat(adapter.validate(TENANT, owner(ModelType.SUMMARY), upstream(upstreamId, 3)).code()).isEqualTo("MODEL_IMPLEMENTATION_INPUT_STALE");

        when(crossPlan.planId()).thenReturn(PLAN_ID);
        when(crossPlan.dependsOn()).thenReturn(List.of(new ModelRevisionRef(OWNER_ID, 1)));
        assertThat(adapter.validate(TENANT, owner(ModelType.SUMMARY), upstream(upstreamId, 3)).code())
            .isEqualTo("MODEL_IMPLEMENTATION_SELF_REFERENCE");
    }

    private static ModelImplementationCompatibilityAdapter adapter(ModelSpecSourceValidationPort sourceValidation) {
        return new ModelImplementationCompatibilityAdapter(
            mock(ModelSpecApplicationService.class),
            mock(ModelSpecRepository.class),
            mock(ModelLifecycleRepository.class),
            sourceValidation
        );
    }

    private static ModelSpecView owner(ModelType type) {
        ModelSpecView result = mock(ModelSpecView.class);
        when(result.id()).thenReturn(OWNER_ID);
        when(result.planId()).thenReturn(PLAN_ID);
        when(result.modelType()).thenReturn(type);
        when(result.layer()).thenReturn(type == ModelType.SUMMARY ? Layer.DWS : type == ModelType.APPLICATION ? Layer.ADS : Layer.DWD);
        when(result.implementationMode()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(result.contractVersion()).thenReturn(ModelSpecContract.CONTRACT_VERSION);
        when(result.compatibilityMode()).thenReturn(CompatibilityMode.CANONICAL);
        when(result.status()).thenReturn(ModelStatus.DRAFT);
        when(result.revision()).thenReturn(1);
        when(result.checksum()).thenReturn("a".repeat(64));
        when(result.materialization()).thenReturn("table");
        when(result.sourceRefs()).thenReturn(List.of());
        when(result.dependsOn()).thenReturn(List.of());
        return result;
    }

    private static SourceRef source(UUID binding, String version) {
        return new SourceRef(SourceKind.TABLE, "asset-1", Layer.ODS, SourceRole.PRIMARY, "source", null, null, 1, binding, version);
    }

    private static SaveImplementationCommand physical(UUID binding, String version) {
        return new SaveImplementationCommand(
            InputMode.PHYSICAL_ASSET, List.of(new PhysicalAssetInput(binding, version)), List.of(), Map.of(),
            ImplementationMode.DESIGNER_GENERATED, "table", "physical"
        );
    }

    private static SaveImplementationCommand upstream(UUID modelId, int revision) {
        return new SaveImplementationCommand(
            InputMode.UPSTREAM_MODEL,
            List.of(new UpstreamModelInput(modelId, revision, "a".repeat(64), 4, "b".repeat(64), "model.plan.upstream")),
            List.of(),
            Map.of(),
            ImplementationMode.DESIGNER_GENERATED, "table", "upstream"
        );
    }

    private static ImplementationView implementation(UUID modelId, int modelRevision, String modelChecksum) {
        return new ImplementationView(
            UUID.randomUUID(),
            modelId,
            PLAN_ID,
            modelRevision,
            modelChecksum,
            ImplementationMode.DESIGNER_GENERATED,
            "plan",
            "model.plan.upstream",
            "ACTIVE",
            4,
            "b".repeat(64),
            InputMode.GENERATED,
            List.of(new GeneratedInput("DATE_DIMENSION", Map.of())),
            List.of(),
            Map.of(),
            "table"
        );
    }

    private static SaveImplementationCommand generated(String generatorType) {
        return new SaveImplementationCommand(
            InputMode.GENERATED, List.of(new GeneratedInput(generatorType, Map.of())), List.of(), Map.of(),
            ImplementationMode.DESIGNER_GENERATED, "table", "generated"
        );
    }
}
