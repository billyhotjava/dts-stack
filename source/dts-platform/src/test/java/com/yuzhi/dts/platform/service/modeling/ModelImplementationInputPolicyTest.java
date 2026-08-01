package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
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
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelImplementationInputPolicyTest {

    private static final String TENANT = "tenant-a";
    private static final UUID OWNER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void enforcesFourModelInputMatrixAndRegisteredGenerator() {
        ModelImplementationInputPolicy adapter = adapter(mock(ModelSpecSourceValidationPort.class));
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
        ModelImplementationInputPolicy adapter = adapter(validation);
        UUID binding = UUID.randomUUID();
        ModelSpecView owner = owner(ModelType.DIMENSION);
        when(validation.isCurrentBindingForGate(TENANT, PLAN_ID, binding, "v1")).thenReturn(false);

        assertThat(adapter.validate(TENANT, owner, physical(binding, "v1")).code()).isEqualTo("PHYSICAL_ASSET_NOT_CONFIRMED");
    }

    @Test
    void rejectsStaleCrossPlanSelfAndCyclicUpstreamInput() {
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelImplementationInputPolicy adapter = new ModelImplementationInputPolicy(
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

    @Test
    void convertsOnlyExpectedDomainUnavailabilityAndPropagatesInfrastructureFailures() {
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelImplementationInputPolicy policy = new ModelImplementationInputPolicy(
            models,
            repository,
            lifecycle,
            mock(ModelSpecSourceValidationPort.class)
        );
        UUID upstreamId = UUID.randomUUID();
        ModelRevisionRef upstreamRef = new ModelRevisionRef(upstreamId, 1);
        when(models.revision(TENANT, upstreamRef)).thenThrow(
            new ModelSpecException("MODEL_SPEC_NOT_FOUND", "not found", ModelSpecException.Kind.NOT_FOUND)
        );
        assertThat(policy.validate(TENANT, owner(ModelType.SUMMARY), upstream(upstreamId, 1)).code())
            .isEqualTo("MODEL_IMPLEMENTATION_INPUT_STALE");

        IllegalStateException databaseFailure = new IllegalStateException("database unavailable");
        doThrow(databaseFailure).when(models).revision(TENANT, upstreamRef);
        assertThatThrownBy(() -> policy.validate(TENANT, owner(ModelType.SUMMARY), upstream(upstreamId, 1)))
            .isSameAs(databaseFailure);
    }

    @Test
    void propagatesInfrastructureFailuresWhileWalkingTheUpstreamGraph() {
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelImplementationInputPolicy policy = new ModelImplementationInputPolicy(
            models,
            repository,
            lifecycle,
            mock(ModelSpecSourceValidationPort.class)
        );
        UUID upstreamId = UUID.randomUUID();
        UUID dependencyId = UUID.randomUUID();
        ModelRevisionRef upstreamRef = new ModelRevisionRef(upstreamId, 1);
        ModelRevisionRef dependencyRef = new ModelRevisionRef(dependencyId, 1);
        ModelSpecView upstreamModel = owner(ModelType.FACT);
        when(upstreamModel.dependsOn()).thenReturn(List.of(dependencyRef));
        when(models.revision(TENANT, upstreamRef)).thenReturn(upstreamModel);
        StoredModelSpec current = mock(StoredModelSpec.class);
        when(current.revision()).thenReturn(1);
        when(repository.findCurrent(TENANT, upstreamId)).thenReturn(Optional.of(current));
        when(lifecycle.findImplementation(TENANT, upstreamId)).thenReturn(
            Optional.of(implementation(upstreamId, 1, "a".repeat(64)))
        );
        IllegalStateException databaseFailure = new IllegalStateException("database unavailable");
        when(models.revision(TENANT, dependencyRef)).thenThrow(databaseFailure);

        assertThatThrownBy(() -> policy.validate(TENANT, owner(ModelType.SUMMARY), upstream(upstreamId, 1)))
            .isSameAs(databaseFailure);
    }

    @Test
    void missingTransitiveDependencyIsReportedAsStaleInsteadOfSelfReference() {
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        ModelSpecRepository repository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelImplementationInputPolicy policy = new ModelImplementationInputPolicy(
            models,
            repository,
            lifecycle,
            mock(ModelSpecSourceValidationPort.class)
        );
        UUID upstreamId = UUID.randomUUID();
        UUID missingDependencyId = UUID.randomUUID();
        ModelRevisionRef upstreamRef = new ModelRevisionRef(upstreamId, 1);
        ModelRevisionRef missingDependencyRef = new ModelRevisionRef(missingDependencyId, 1);
        ModelSpecView upstreamModel = owner(ModelType.FACT);
        when(upstreamModel.dependsOn()).thenReturn(List.of(missingDependencyRef));
        when(models.revision(TENANT, upstreamRef)).thenReturn(upstreamModel);
        when(models.revision(TENANT, missingDependencyRef)).thenThrow(
            new ModelSpecException("MODEL_SPEC_NOT_FOUND", "not found", ModelSpecException.Kind.NOT_FOUND)
        );
        StoredModelSpec current = mock(StoredModelSpec.class);
        when(current.revision()).thenReturn(1);
        when(repository.findCurrent(TENANT, upstreamId)).thenReturn(Optional.of(current));
        when(lifecycle.findImplementation(TENANT, upstreamId)).thenReturn(
            Optional.of(implementation(upstreamId, 1, "a".repeat(64)))
        );

        assertThat(policy.validate(TENANT, owner(ModelType.SUMMARY), upstream(upstreamId, 1)).code())
            .isEqualTo("MODEL_IMPLEMENTATION_INPUT_STALE");
    }

    private static ModelImplementationInputPolicy adapter(ModelSpecSourceValidationPort sourceValidation) {
        return new ModelImplementationInputPolicy(
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
        when(result.dependsOn()).thenReturn(List.of());
        return result;
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
