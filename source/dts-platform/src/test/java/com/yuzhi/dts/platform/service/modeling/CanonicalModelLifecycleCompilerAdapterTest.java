package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyService.Resolution;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.DependencyRole;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.ModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSource;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSourceFact;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Snapshot;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceKind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.CanonicalDbtProjectBundleAssembler;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtProjectBundleManifest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtProjectBundleManifest.BundleFile;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CanonicalModelLifecycleCompilerAdapterTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void writesTheFourOrdinaryArtifactsWithEphemeralStgMetadata() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecView model = model(ImplementationMode.DESIGNER_GENERATED);
        ImplementationView implementation = implementation(ImplementationMode.DESIGNER_GENERATED);
        CanonicalModelLifecycleCompilerAdapter compiler = new CanonicalModelLifecycleCompilerAdapter(modelSpecs, null);

        List<ModelLifecycleContract.ArtifactWrite> artifacts = compiler.compile("tenant-a", model, implementation);
        List<ModelLifecycleContract.ArtifactWrite> replay = compiler.compile("tenant-a", model, implementation);
        List<ModelLifecycleContract.ArtifactWrite> renamedTarget = compiler.compile(
            "tenant-a",
            model,
            implementation(ImplementationMode.DESIGNER_GENERATED, "dwd_customer_detail_v2", "c".repeat(64))
        );

        assertThat(replay).isEqualTo(artifacts);
        assertThat(renamedTarget).extracting(ModelLifecycleContract.ArtifactWrite::path)
            .containsExactlyInAnyOrderElementsOf(
                artifacts.stream().map(ModelLifecycleContract.ArtifactWrite::path).toList()
            );
        assertThat(renamedTarget).filteredOn(artifact -> artifact.artifactType().equals("SQL")).singleElement().satisfies(
            renamed -> {
                ModelLifecycleContract.ArtifactWrite current = artifacts
                    .stream()
                    .filter(artifact -> artifact.artifactType().equals("SQL"))
                    .findFirst()
                    .orElseThrow();
                assertThat(renamed.content()).contains("alias='dwd_customer_detail_v2'");
                assertThat(renamed.checksum()).isNotEqualTo(current.checksum());
            }
        );
        assertThat(artifacts).extracting(ModelLifecycleContract.ArtifactWrite::artifactType)
            .containsExactlyInAnyOrder("STG_SQL", "SQL", "SCHEMA", "TEST");
        assertThat(artifacts).allSatisfy(artifact -> assertThat(artifact.physicalAssetRef()).isNull());
        assertThat(artifacts).filteredOn(artifact -> artifact.artifactType().equals("STG_SQL")).singleElement().satisfies(artifact -> {
            assertThat(artifact.path()).endsWith("/stg_model_30000000_0000_0000_0000_000000000001.sql");
            assertThat(artifact.content()).startsWith("{{ config(materialized='ephemeral') }}");
            assertThat(artifact.nodeKind()).isEqualTo("STG");
            assertThat(artifact.materialization()).isEqualTo("ephemeral");
        });
        assertThat(artifacts).filteredOn(artifact -> artifact.artifactType().equals("SQL")).singleElement()
            .extracting(ModelLifecycleContract.ArtifactWrite::content)
            .asString()
            .contains("materialized='table'")
            .contains("alias='dwd_customer_detail'")
            .contains("'tenantId':'tenant-a'")
            .contains("'planId':'10000000-0000-0000-0000-000000000001'")
            .contains("'modelSpecId':'30000000-0000-0000-0000-000000000001'")
            .contains("'revision':2")
            .contains("'implementationRevision':5")
            .contains("{{ ref('stg_model_30000000_0000_0000_0000_000000000001') }}");
    }

    @Test
    void assemblesValidatesFreezesAndRestoresTheCanonicalFourFileProject() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecView model = model(ImplementationMode.DESIGNER_GENERATED);
        ImplementationView implementation = implementation(ImplementationMode.DESIGNER_GENERATED);
        List<ModelLifecycleContract.ArtifactWrite> artifacts = new CanonicalModelLifecycleCompilerAdapter(modelSpecs, null)
            .compile("tenant-a", model, implementation);
        LinkedHashMap<String, String> generatedFiles = new LinkedHashMap<>();
        artifacts.forEach(artifact -> generatedFiles.putIfAbsent(artifact.path(), artifact.content()));

        Map<String, String> projectFiles = CanonicalDbtProjectBundleAssembler.assemble(
            ModelImplementationExecutionPlanner.systemManagedDbtProjectKey(model),
            implementation.materialization(),
            generatedFiles
        );
        AdvancedDbtDraftStaticValidator.ValidatedProject validated = new AdvancedDbtDraftStaticValidator().validate(projectFiles);
        List<BundleFile> bundleFiles = projectFiles
            .entrySet()
            .stream()
            .map(entry -> bundleFile(entry.getKey(), entry.getValue()))
            .toList();
        DbtProjectBundleManifest.BundleSnapshot frozen = DbtProjectBundleManifest.freeze(
            new ObjectMapper().findAndRegisterModules(),
            bundleFiles,
            validated
        );
        DbtProjectBundleManifest.RestoredBundle restored = DbtProjectBundleManifest.restore(
            new ObjectMapper().findAndRegisterModules(),
            frozen.manifest(),
            frozen.bundleChecksum(),
            frozen.projectChecksum()
        );

        assertThat(artifacts).extracting(ModelLifecycleContract.ArtifactWrite::artifactType)
            .containsExactlyInAnyOrder("STG_SQL", "SQL", "SCHEMA", "TEST");
        assertThat(generatedFiles).hasSize(3);
        assertThat(projectFiles).hasSize(4).containsKey("dbt_project.yml");
        assertThat(validated.projectKey()).isEqualTo("dts");
        assertThat(frozen.fileCount()).isEqualTo(4);
        assertThat(restored.files()).extracting(BundleFile::path)
            .containsExactlyInAnyOrderElementsOf(projectFiles.keySet());
    }

    @Test
    void dbtManagedCompilationRequiresDedicatedImportedArtifacts() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecView model = model(ImplementationMode.DBT_MANAGED);
        ImplementationView implementation = implementation(ImplementationMode.DBT_MANAGED);

        assertThatThrownBy(() ->
            new CanonicalModelLifecycleCompilerAdapter(modelSpecs, null).compile("tenant-a", model, implementation)
        ).isInstanceOf(ModelSpecException.class).hasMessageContaining("dedicated import path");
        verifyNoInteractions(modelSpecs);
    }

    @Test
    void resolvesThePinnedPhysicalRelationFromTheAuthoritativePlanBinding() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecSourceValidationPort sourceValidation = mock(ModelSpecSourceValidationPort.class);
        ModelSpecView model = model(ImplementationMode.DESIGNER_GENERATED);
        ImplementationView implementation = implementation(ImplementationMode.DESIGNER_GENERATED);
        UUID bindingId = UUID.fromString("50000000-0000-0000-0000-000000000001");
        when(sourceValidation.resolveCurrentBindingForCompiler("tenant-a", PLAN_ID, bindingId, "source-v2"))
            .thenReturn(Optional.of(new SourceRef(
                SourceKind.TABLE,
                "landing.customer_current",
                Layer.ODS,
                SourceRole.PRIMARY,
                null,
                null,
                null,
                0,
                bindingId,
                "source-v2"
            )));

        List<ModelLifecycleContract.ArtifactWrite> artifacts = new CanonicalModelLifecycleCompilerAdapter(modelSpecs, sourceValidation)
            .compile("tenant-a", model, implementation);

        assertThat(artifacts).filteredOn(artifact -> artifact.artifactType().equals("STG_SQL")).singleElement()
            .extracting(ModelLifecycleContract.ArtifactWrite::content)
            .asString()
            .contains("{{ source('landing', 'customer_current') }}");
    }

	@Test
	void compilesPhysicalAndDimensionInputsFromTheSharedFixedDependencySnapshot() {
		ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
		ModelImplementationDependencyService dependencyService = mock(ModelImplementationDependencyService.class);
		UUID dimensionId = UUID.fromString("30000000-0000-0000-0000-000000000002");
		UUID bindingId = UUID.fromString("50000000-0000-0000-0000-000000000001");
		ModelSpecView dimension = dimensionModel(dimensionId);
		ModelSpecView owner = modelWithDimension(dimensionId);
		ImplementationView base = implementation(ImplementationMode.DESIGNER_GENERATED);
		ImplementationView implementation = new ImplementationView(
			base.id(),
			base.modelSpecId(),
			base.planId(),
			base.revision(),
			base.modelChecksum(),
			base.ownership(),
			base.projectKey(),
			base.dbtUniqueId(),
			base.status(),
			base.implementationRevision(),
			base.implementationChecksum(),
			base.inputMode(),
			base.inputs(),
			List.of(new ModelLifecycleContract.FieldMapping("src_0.customer_id", "customer_id")),
			Map.of(
				"targetPhysicalName", "dwd_customer_detail",
				"loadStrategy", "FULL",
				"partitionFields", List.of(),
				"joins", List.of(Map.of(
					"inputIndex", 1,
					"type", "LEFT",
					"leftField", "src_0.customer_id",
					"rightField", "src_1.customer_id"
				))
			),
			base.materialization()
		);
		Snapshot snapshot = new Snapshot(
			owner.id(),
			owner.revision(),
			owner.checksum(),
			implementation.implementationRevision(),
			implementation.implementationChecksum(),
			List.of(new PhysicalSource(bindingId, "source-v2", "source.dts.binding_1")),
			List.of(new ModelInput(
				dimensionId,
				dimension.revision(),
				dimension.checksum(),
				2,
				"c".repeat(64),
				"model.dts.dim_customer",
				DependencyRole.DIMENSION
			)),
			"d".repeat(64)
		);
		Resolution resolution = new Resolution(
			snapshot,
			Map.of(bindingId, new PhysicalSourceFact(bindingId, "source-v2", true, "CONNECTION_TABLE", "landing.customer_current"))
		);
		when(dependencyService.resolveCurrent(
			"tenant-a",
			owner,
			implementation,
			implementation.projectKey(),
			"dwd_customer_detail"
		)).thenReturn(resolution);
		when(modelSpecs.revision("tenant-a", new ModelRevisionRef(dimensionId, dimension.revision()))).thenReturn(dimension);

		List<ModelLifecycleContract.ArtifactWrite> artifacts = new CanonicalModelLifecycleCompilerAdapter(
			modelSpecs,
			null,
			dependencyService
		).compile("tenant-a", owner, implementation);

		assertThat(artifacts).filteredOn(artifact -> artifact.artifactType().equals("STG_SQL")).singleElement()
			.extracting(ModelLifecycleContract.ArtifactWrite::content)
			.asString()
			.contains("{{ source('landing', 'customer_current') }}")
			.contains("{{ ref('dim_customer') }}")
			.contains("LEFT JOIN source_1 src_1");
		assertThat(artifacts).filteredOn(artifact -> artifact.artifactType().equals("SQL")).singleElement()
			.extracting(ModelLifecycleContract.ArtifactWrite::content)
			.asString()
			.contains("'dependencyChecksum':'" + "d".repeat(64) + "'");
	}

    private static ImplementationView implementation(ImplementationMode ownership) {
        return implementation(ownership, "dwd_customer_detail", "b".repeat(64));
    }

    private static ImplementationView implementation(
        ImplementationMode ownership,
        String targetPhysicalName,
        String implementationChecksum
    ) {
        return new ImplementationView(
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            MODEL_ID,
            PLAN_ID,
            2,
            "a".repeat(64),
            ownership,
            "plan_10000000_0000_0000_0000_000000000001",
            "model.plan_10000000_0000_0000_0000_000000000001.model_30000000_0000_0000_0000_000000000001",
            "ACTIVE",
            5,
            implementationChecksum,
            InputMode.PHYSICAL_ASSET,
            List.of(new PhysicalAssetInput(UUID.fromString("50000000-0000-0000-0000-000000000001"), "source-v2")),
            List.of(),
            Map.of(
                "targetPhysicalName", targetPhysicalName,
                "loadStrategy", "FULL",
                "partitionFields", List.of()
            ),
            "table"
        );
    }

    private static ModelSpecView model(ImplementationMode ownership) {
        return new ModelSpecView(
            2,
            MODEL_ID,
            PLAN_ID,
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            ModelType.DIMENSION,
            Layer.DWD,
            "customer_detail",
            null,
            ownership,
            "table",
            null,
            null,
            new Grain("one row per customer", List.of("customer_id")),
            null,
            null,
            List.of(new ModelField("customer_id", "varchar", false, null, ModelSpecContract.FieldRole.KEY, null)),
            List.of(new SourceRef(
                SourceKind.TABLE,
                "ods.customer",
                Layer.ODS,
                SourceRole.PRIMARY,
                null,
                null,
                null,
                0,
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                "source-v2"
            )),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            ModelStatus.DRAFT,
            2,
            "a".repeat(64),
            Instant.EPOCH,
            Instant.EPOCH,
            CompatibilityMode.CANONICAL,
            null
        );
    }

	private static ModelSpecView modelWithDimension(UUID dimensionId) {
		ModelSpecView base = model(ImplementationMode.DESIGNER_GENERATED);
		return copyModel(
			base,
			base.id(),
			base.modelType(),
			base.revision(),
			base.fields(),
			List.of(new ModelRevisionRef(dimensionId, 1))
		);
	}

	private static ModelSpecView dimensionModel(UUID dimensionId) {
		ModelSpecView base = model(ImplementationMode.DESIGNER_GENERATED);
		return copyModel(base, dimensionId, ModelType.DIMENSION, 1, base.fields(), List.of());
	}

	private static ModelSpecView copyModel(
		ModelSpecView base,
		UUID id,
		ModelType modelType,
		int revision,
		List<ModelField> fields,
		List<ModelRevisionRef> dimensionRefs
	) {
		return new ModelSpecView(
			base.contractVersion(), id, base.planId(), base.domainId(), modelType, base.layer(), base.name(), base.description(),
			base.implementationMode(), base.materialization(), base.businessActivityRef(), base.consumptionScenario(), base.grain(),
			base.factShape(), base.timeSemantics(), fields, base.sourceRefs(), base.dependsOn(), dimensionRefs, base.metricRefs(),
			base.standardBindings(), base.generationStrategy(), base.dimensionProfile(), base.dimensionDefinitionRef(), base.status(),
			revision, "a".repeat(64), base.createdAt(), base.updatedAt(), base.compatibilityMode(), base.legacyRefs()
		);
	}

    private static BundleFile bundleFile(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new BundleFile(path, content, ModelPackageChecksum.sha256(bytes), bytes.length);
    }
}
