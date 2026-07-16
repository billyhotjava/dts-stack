package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelingVNextContractTest {

    @Test
    void pjmFixtureUsesTheSameCrossLayerContract() {
        ModelingVNextContract.PjmFixture fixture = ModelingVNextContract.pjmProjectNodeFixture();

        assertThat(fixture.contractVersion()).isEqualTo(ModelingVNextContract.CONTRACT_VERSION);
        assertThat(fixture.businessObject().objectKind()).isEqualTo(ModelingVNextContract.ObjectKind.FACT);
        assertThat(fixture.businessObject().processId()).isEqualTo("project-node-plan-loop");
        assertThat(fixture.businessObject().businessKey()).containsExactly("project_no", "subsystem", "node_task", "plan_date");
        assertThat(ModelingVNextContract.validateModelSpec(fixture.modelSpec())).isEmpty();
    }

    @Test
    void dwdDesignerModelRequiresGrainAndStandardBinding() {
        ModelingVNextContract.ModelSpec model = new ModelingVNextContract.ModelSpec(
            "model-1",
            "object-1",
            "project-node-plan-loop",
            ModelingVNextContract.Layer.DWD,
            ModelingVNextContract.ModelType.FACT,
            ModelingVNextContract.ImplementationMode.DESIGNER_GENERATED,
            "project_node_detail",
            new ModelingVNextContract.Grain("", List.of()),
            List.of(),
            List.of(new ModelingVNextContract.SourceRef("TABLE", "ods_project_subject_domain_v2", ModelingVNextContract.Layer.ODS)),
            1
        );

        assertThat(ModelingVNextContract.validateModelSpec(model))
            .containsExactly("DWD 模型必须声明粒度键", "DWD 设计器模型至少绑定一个数据标准");
    }
}
