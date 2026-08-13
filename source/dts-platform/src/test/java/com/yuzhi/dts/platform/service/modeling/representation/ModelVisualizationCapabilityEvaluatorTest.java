package com.yuzhi.dts.platform.service.modeling.representation;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.CapabilityReason;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.RepresentationScope;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.VisualizationCapability;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ModelVisualizationCapabilityEvaluatorTest {

    private final ModelVisualizationCapabilityEvaluator evaluator = new ModelVisualizationCapabilityEvaluator();

    @ParameterizedTest
    @MethodSource("capabilities")
    void keepsVisualizationCapabilityOrthogonalToImplementationOwnership(
        RepresentationScope scope,
        ImplementationMode ownership,
        ModelVisualizationCapabilityEvaluator.ProjectionTrust trust,
        VisualizationCapability expected,
        CapabilityReason expectedReason
    ) {
        var decision = evaluator.evaluate(scope, ownership, trust);

        assertThat(decision.capability()).isEqualTo(expected);
        if (expectedReason == null) {
            assertThat(decision.reasons()).isEmpty();
        } else {
            assertThat(decision.reasons()).contains(expectedReason);
        }
    }

    private static Stream<Arguments> capabilities() {
        var trusted = new ModelVisualizationCapabilityEvaluator.ProjectionTrust(true, true, true, true, false, false);
        return Stream.of(
            Arguments.of(RepresentationScope.BUSINESS, ImplementationMode.DESIGNER_GENERATED, trusted, VisualizationCapability.BUSINESS_VISUAL_EDIT, null),
            Arguments.of(RepresentationScope.BUSINESS, ImplementationMode.DBT_MANAGED, trusted, VisualizationCapability.BUSINESS_VISUAL_READ, null),
            Arguments.of(RepresentationScope.TECHNICAL, ImplementationMode.DBT_MANAGED, trusted, VisualizationCapability.ADVANCED_DBT_IMPLEMENTATION, null),
            Arguments.of(RepresentationScope.TECHNICAL, ImplementationMode.DESIGNER_GENERATED, trusted, VisualizationCapability.DESIGNER_DBT_PREVIEW, null),
            Arguments.of(
                RepresentationScope.TECHNICAL,
                ImplementationMode.DBT_MANAGED,
                new ModelVisualizationCapabilityEvaluator.ProjectionTrust(false, false, false, false, false, false),
                VisualizationCapability.ADVANCED_DBT_IMPLEMENTATION,
                null
            ),
            Arguments.of(
                RepresentationScope.BUSINESS,
                ImplementationMode.DBT_MANAGED,
                new ModelVisualizationCapabilityEvaluator.ProjectionTrust(false, true, true, true, false, false),
                VisualizationCapability.BLOCKED,
                CapabilityReason.MODEL_REPRESENTATION_NO_IMPLEMENTATION
            ),
            Arguments.of(
                RepresentationScope.BUSINESS,
                ImplementationMode.DBT_MANAGED,
                new ModelVisualizationCapabilityEvaluator.ProjectionTrust(true, false, true, true, false, false),
                VisualizationCapability.BLOCKED,
                CapabilityReason.MODEL_REPRESENTATION_FIELDS_UNTRUSTED
            ),
            Arguments.of(
                RepresentationScope.BUSINESS,
                ImplementationMode.DBT_MANAGED,
                new ModelVisualizationCapabilityEvaluator.ProjectionTrust(true, true, true, false, false, false),
                VisualizationCapability.BLOCKED,
                CapabilityReason.MODEL_REPRESENTATION_ARTIFACT_PIN_MISMATCH
            ),
            Arguments.of(
                RepresentationScope.BUSINESS,
                ImplementationMode.DBT_MANAGED,
                new ModelVisualizationCapabilityEvaluator.ProjectionTrust(true, true, false, true, true, false),
                VisualizationCapability.BLOCKED,
                CapabilityReason.MODEL_REPRESENTATION_DYNAMIC_DEPENDENCY
            )
        );
    }
}
