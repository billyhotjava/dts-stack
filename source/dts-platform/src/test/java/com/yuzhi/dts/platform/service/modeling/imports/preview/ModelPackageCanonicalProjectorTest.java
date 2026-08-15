package com.yuzhi.dts.platform.service.modeling.imports.preview;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationChecksumCodec;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector.DependencyTarget;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector.ProjectRequest;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector.SourceTarget;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ScdType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.StandardBinding;
import com.yuzhi.dts.platform.service.modeling.ModelSpecSnapshotCodec;
import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Column;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SemanticMetadata;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelPackageCanonicalProjectorTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000070");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000070");
    private static final UUID BINDING_ID = UUID.fromString("30000000-0000-0000-0000-000000000070");

    private ModelPackageCanonicalProjector projector;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        projector = new ModelPackageCanonicalProjector(
            objectMapper,
            new ModelSpecSnapshotCodec(objectMapper),
            new ModelImplementationChecksumCodec(objectMapper)
        );
    }

    @Test
    void invokesCanonicalContractAndBlocksTypeLayerMismatch() {
        PackageModel base = ModelPackageFixtures.validPackage().models().getFirst();
        SemanticMetadata semantics = base.semantics();
        PackageModel invalid = withSemantics(
            base,
            new SemanticMetadata(
                "FACT",
                "DWS",
                semantics.grain(),
                semantics.factShape(),
                semantics.timeSemantics(),
                semantics.domainCode(),
                semantics.sourceRefs(),
                semantics.consumptionScenarios(),
                semantics.fieldRoles(),
                semantics.dimensionStrategy(),
                semantics.dimensionDefinitionCode(),
                semantics.overrideSource(),
                false
            )
        );

        var projection = projector.project(request(invalid, List.of(), null));

        assertThat(projection.issues()).extracting("code").contains("MODEL_SPEC_TYPE_LAYER_MISMATCH");
        assertThat(projection.modelSpecChecksum()).isNull();
    }

    @Test
    void blocksUnsupportedFieldTypeAndRole() {
        PackageModel base = ModelPackageFixtures.validPackage().models().getFirst();
        PackageModel invalid = new PackageModel(
            base.dbtUniqueId(),
            base.name(),
            base.description(),
            base.resourcePath(),
            base.sql(),
            base.materialization(),
            base.config(),
            base.tags(),
            List.of(new Column("budget_id", null, "geography", "OWNER", List.of("not_null"))),
            base.tests(),
            base.dependencies(),
            base.semantics(),
            base.conversion()
        );

        var projection = projector.project(request(invalid, List.of(), null));

        assertThat(projection.issues()).extracting("code").contains("MODEL_SPEC_FIELD_INVALID");
        assertThat(projection.modelSpecChecksum()).isNull();
    }

    @Test
    void blocksUnpinnedDependency() {
        PackageModel base = ModelPackageFixtures.validPackage().models().getFirst();

        var projection = projector.project(
            request(
                base,
                List.of(new DependencyTarget("model.pjm.dimension", null, 0, null, 0, null, ModelType.DIMENSION)),
                null
            )
        );

        assertThat(projection.issues()).extracting("code").contains("MODEL_SPEC_DEPENDENCY_INVALID");
    }

    @Test
    void dimensionWithoutResolvedCurrentDefinitionIsBlockedByCanonicalContract() {
        PackageModel base = ModelPackageFixtures.validPackage().models().getFirst();
        SemanticMetadata semantics = base.semantics();
        PackageModel dimension = withSemantics(
            base,
            new SemanticMetadata(
                "DIMENSION",
                "DWD",
                semantics.grain(),
                null,
                null,
                semantics.domainCode(),
                semantics.sourceRefs(),
                semantics.consumptionScenarios(),
                semantics.fieldRoles(),
                semantics.dimensionStrategy(),
                "dim_70000000000000000000000000000070",
                semantics.overrideSource(),
                false
            )
        );

        var projection = projector.project(request(dimension, List.of(), null));

        assertThat(projection.issues()).extracting("code").contains("MODEL_SPEC_DIMENSION_DEFINITION_REQUIRED");
    }

    @Test
    void projectsTargetEnvironmentStandardBindingsFromTheRequestSidecar() {
        PackageModel model = ModelPackageFixtures.validPackage().models().getFirst();
        String field = model.columns().getFirst().name();
        StandardBinding binding = new StandardBinding(
            field,
            UUID.fromString("40000000-0000-0000-0000-000000000083"),
            1,
            null,
            null,
            null,
            null,
            "L2"
        );

        var base = request(model, List.of(), null);
        var projection = projector.project(
            new ProjectRequest(
                base.planId(),
                base.domainId(),
                base.projectKey(),
                base.model(),
                base.externalDbtOwned(),
                base.sources(),
                base.dependencies(),
                base.dimensionDefinitionRef(),
                List.of(binding)
            )
        );

        assertThat(projection.modelSpecCommand().standardBindings()).containsExactly(binding);
    }

    @Test
    void preservesPackageFieldLabelsAndStaticDimensionStrategy() {
        PackageModel base = ModelPackageFixtures.validPackage().models().getFirst();
        SemanticMetadata semantics = base.semantics();
        PackageModel dimension = new PackageModel(
            base.dbtUniqueId(),
            base.name(),
            base.description(),
            base.resourcePath(),
            base.sql(),
            base.materialization(),
            base.config(),
            base.tags(),
            List.of(new Column("budget_id", "预算标识", "timestamp without time zone", "KEY", List.of("not_null_budget_id"))),
            base.tests(),
            base.dependencies(),
            new SemanticMetadata(
                "DIMENSION",
                "DWD",
                semantics.grain(),
                null,
                null,
                semantics.domainCode(),
                semantics.sourceRefs(),
                semantics.consumptionScenarios(),
                semantics.fieldRoles(),
                "STATIC_DBT_SQL_TYPE_1",
                "dim_70000000000000000000000000000070",
                semantics.overrideSource(),
                false
            ),
            base.conversion()
        );

        var projection = projector.project(
            request(
                dimension,
                List.of(),
                new com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionDefinitionRef(
                    UUID.fromString("70000000-0000-0000-0000-000000000070"),
                    1
                )
            )
        );

        assertThat(projection.modelSpecCommand().fields().getFirst())
            .extracting("dataType", "nullable", "displayName")
            .containsExactly("timestamp", false, "预算标识");
        assertThat(projection.modelSpecCommand().generationStrategy().type()).isEqualTo("STATIC_DBT_SQL_TYPE_1");
        assertThat(projection.modelSpecCommand().dimensionProfile().scdPolicy().type()).isEqualTo(ScdType.TYPE1);
    }

    @Test
    void projectsExecutableDbtTargetSettingsFromAliasWithModelNameFallback() {
        PackageModel base = ModelPackageFixtures.validPackage().models().getFirst();

        var fallback = projector.project(request(base, List.of(), null));

        assertThat(fallback.implementationCommand().settings())
            .containsEntry("targetPhysicalName", "budget")
            .containsEntry("loadStrategy", "FULL")
            .containsEntry("partitionFields", List.of());

        PackageModel aliased = new PackageModel(
            base.dbtUniqueId(),
            base.name(),
            base.description(),
            base.resourcePath(),
            base.sql(),
            base.materialization(),
            Map.of("materialized", "table", "alias", "biz_dwd_budget_v2"),
            base.tags(),
            base.columns(),
            base.tests(),
            base.dependencies(),
            base.semantics(),
            base.conversion()
        );

        var projection = projector.project(request(aliased, List.of(), null));

        assertThat(projection.issues()).noneMatch(issue -> issue.code().startsWith("IMPLEMENTATION_"));
        assertThat(projection.implementationCommand().settings())
            .containsEntry("targetPhysicalName", "biz_dwd_budget_v2")
            .containsEntry("loadStrategy", "FULL")
            .containsEntry("partitionFields", List.of());
    }

    private static ProjectRequest request(
        PackageModel model,
        List<DependencyTarget> dependencies,
        com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionDefinitionRef dimensionDefinitionRef
    ) {
        return new ProjectRequest(
            PLAN_ID,
            DOMAIN_ID,
            "pjm",
            model,
            true,
            List.of(new SourceTarget(BINDING_ID, "TABLE", "source.pjm.budget", "ODS", "source-v1")),
            dependencies,
            dimensionDefinitionRef
        );
    }

    private static PackageModel withSemantics(PackageModel model, SemanticMetadata semantics) {
        return new PackageModel(
            model.dbtUniqueId(),
            model.name(),
            model.description(),
            model.resourcePath(),
            model.sql(),
            model.materialization(),
            model.config(),
            model.tags(),
            model.columns(),
            model.tests(),
            model.dependencies(),
            semantics,
            model.conversion()
        );
    }
}
