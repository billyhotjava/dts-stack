package com.yuzhi.dts.platform.service.modeling.observability;

import static com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyResponse;
import static com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplySummary;
import static com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.AttemptStatus;
import static com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginDisposition;
import static com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Kind;
import static com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ModelSpecImportApplyException;
import static com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.DbtCompatibilityView;
import static com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.ImportProjectionCompatibility;
import static com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.InspectionCompatibility;
import static com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.MaterializationCompatibility;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.web.rest.ApiResponses;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

class ModelingRoundtripMetricsAspectTest {

    @Test
    void recordsPartialApplyAndOnlyBoundedSummaryLabels() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ModelingRoundtripMetricsAspect aspect = new ModelingRoundtripMetricsAspect(new ModelingRoundtripMetrics(registry));
        ApplyResponse response = new ApplyResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            BeginDisposition.STARTED,
            AttemptStatus.PARTIAL,
            new ApplySummary(3, 0, 1, 1, 0, 0, 1, 1),
            List.of()
        );
        ProceedingJoinPoint joinPoint = joinPoint("apply", ApiResponses.ok(response));

        aspect.importApply(joinPoint);

        assertThat(
            registry
                .get("dts.modeling.dbt.roundtrip.operations")
                .tags("operation", "import_apply", "result", "partial")
                .counter()
                .count()
        ).isEqualTo(1);
        assertThat(
            registry
                .get("dts.modeling.dbt.roundtrip.items")
                .tags("phase", "apply", "state", "failed")
                .summary()
                .totalAmount()
        ).isEqualTo(1);
        assertThat(registry.getMeters())
            .allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                    .allSatisfy(tag -> assertThat(tag.getKey()).isIn("operation", "result", "phase", "state", "surface"))
            );
    }

    @Test
    void classifiesConflictWithoutUsingTheErrorCodeAsAMetricLabel() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ModelingRoundtripMetricsAspect aspect = new ModelingRoundtripMetricsAspect(new ModelingRoundtripMetrics(registry));
        ProceedingJoinPoint joinPoint = joinPoint(
            "apply",
            new ModelSpecImportApplyException("SECRET_MODEL_CAS_CONFLICT_123", "conflict", Kind.CONFLICT, null)
        );

        assertThatThrownBy(() -> aspect.importApply(joinPoint)).isInstanceOf(ModelSpecImportApplyException.class);

        assertThat(
            registry
                .get("dts.modeling.dbt.roundtrip.operations")
                .tags("operation", "import_apply", "result", "conflict")
                .counter()
                .count()
        ).isEqualTo(1);
        assertThat(registry.getMeters())
            .allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                    .noneSatisfy(tag -> assertThat(tag.getValue()).contains("SECRET_MODEL_CAS_CONFLICT_123"))
            );
    }

    @Test
    void recordsThreeIndependentCompatibilityAxesAndArchiveSize() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ModelingRoundtripMetrics metrics = new ModelingRoundtripMetrics(registry);

        metrics.recordInspect(
            4096,
            new DbtCompatibilityView(
                InspectionCompatibility.SUPPORTED,
                ImportProjectionCompatibility.IMPORTABLE,
                MaterializationCompatibility.NOT_CERTIFIED,
                "1.10.2",
                "v12",
                "postgres",
                "1.10.0",
                "candidate",
                List.of()
            )
        );

        assertThat(registry.get("dts.modeling.dbt.roundtrip.inspect.bytes").summary().totalAmount()).isEqualTo(4096);
        assertThat(
            registry
                .get("dts.modeling.dbt.roundtrip.compatibility")
                .tags("axis", "materialization", "state", "not_certified")
                .counter()
                .count()
        ).isEqualTo(1);
    }

    private ProceedingJoinPoint joinPoint(String methodName, Object outcome) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = TestMethods.class.getDeclaredMethod(methodName);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getName()).thenReturn(methodName);
        when(joinPoint.getSignature()).thenReturn(signature);
        if (outcome instanceof Throwable failure) {
            when(joinPoint.proceed()).thenThrow(failure);
        } else {
            when(joinPoint.proceed()).thenReturn(outcome);
        }
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        return joinPoint;
    }

    private static final class TestMethods {

        private void apply() {}

    }
}
