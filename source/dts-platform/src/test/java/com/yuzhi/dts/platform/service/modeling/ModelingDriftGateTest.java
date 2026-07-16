package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelingDriftGateTest {

    @Test
    void detectsFieldAndGrainDriftBetweenModelSpecAndDbtSnapshot() {
        ModelingDriftGate.Snapshot expected = new ModelingDriftGate.Snapshot(
            List.of("project_no", "plan_date"),
            "project_no + plan_date",
            List.of("ods.project_node"),
            List.of("std.project.code"),
            "checksum-a",
            true,
            true,
            true
        );
        ModelingDriftGate.Snapshot actual = new ModelingDriftGate.Snapshot(
            List.of("project_no", "plan_date", "delay_days"),
            "project_no + plan_month",
            List.of("ods.project_node"),
            List.of("std.project.code"),
            "checksum-b",
            true,
            true,
            true
        );

        assertThat(ModelingDriftGate.compare(expected, actual).kinds())
            .containsExactlyInAnyOrder(ModelingDriftGate.DriftKind.FIELD_DRIFT, ModelingDriftGate.DriftKind.GRAIN_DRIFT, ModelingDriftGate.DriftKind.ARTIFACT_CHECKSUM_DRIFT);
    }

    @Test
    void blocksReleaseWhenParseTestOrBusinessRegistrationIsMissing() {
        ModelingDriftGate.Snapshot snapshot = new ModelingDriftGate.Snapshot(
            List.of("project_no"),
            "project_no",
            List.of("ods.project_node"),
            List.of(),
            "checksum",
            false,
            false,
            false
        );

        ModelingDriftGate.ReleaseGateResult result = ModelingDriftGate.evaluate(snapshot, List.of(ModelingDriftGate.DriftKind.FIELD_DRIFT));

        assertThat(result.publishable()).isFalse();
        assertThat(result.blockers()).containsExactlyInAnyOrder("UNREGISTERED_BUSINESS_OBJECT", "DBT_PARSE_FAILED", "DBT_TEST_FAILED", "FIELD_DRIFT");
    }

    @Test
    void allowsControlledReleaseWhenSnapshotIsRegisteredAndClean() {
        ModelingDriftGate.Snapshot snapshot = new ModelingDriftGate.Snapshot(
            List.of("project_no"),
            "project_no",
            List.of("ods.project_node"),
            List.of("std.project.code"),
            "checksum",
            true,
            true,
            true
        );

        assertThat(ModelingDriftGate.evaluate(snapshot, List.of()).publishable()).isTrue();
    }
}
