package com.yuzhi.dts.platform.service.modeling.imports.preview;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationChecksumCodec;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector.DependencyTarget;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector.ProjectRequest;
import com.yuzhi.dts.platform.service.modeling.ModelPackageCanonicalProjector.SourceTarget;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecSnapshotCodec;
import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Column;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SemanticMetadata;
import java.util.List;
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
