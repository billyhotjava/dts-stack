package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
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

    private static BundleFile bundleFile(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new BundleFile(path, content, ModelPackageChecksum.sha256(bytes), bytes.length);
    }
}
