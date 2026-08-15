package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import java.util.List;
import org.junit.jupiter.api.Test;

class DbtModelFieldProjectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void projectsValidatedColumnsAndPreservesExistingGovernanceMetadata() {
        ModelField existing = new ModelField(
            "plan_year",
            "旧名称",
            "integer",
            true,
            "source.plan_year",
            FieldRole.KEY,
            "L2",
            null,
            false,
            null
        );
        String schema =
            """
            {"columns":[
              {"name":"plan_year","description":"计划年份","dataType":"integer","role":"KEY","tests":["not_null"]},
              {"name":"project_total_cnt","description":"项目总数","dataType":"bigint","role":"MEASURE","tests":[]}
            ],"tests":[]}
            """;

        List<ModelField> projected = DbtModelFieldProjector.project(objectMapper, schema, List.of(existing));

        assertThat(projected).hasSize(2);
        assertThat(projected.get(0).displayName()).isEqualTo("计划年份");
        assertThat(projected.get(0).nullable()).isFalse();
        assertThat(projected.get(0).sourceFieldRef()).isEqualTo("source.plan_year");
        assertThat(projected.get(0).securityLevel()).isEqualTo("L2");
        assertThat(projected.get(1).name()).isEqualTo("project_total_cnt");
        assertThat(projected.get(1).role()).isEqualTo(FieldRole.MEASURE);
        assertThat(projected.get(1).nullable()).isTrue();
    }

    @Test
    void rejectsDuplicateContractColumnsBeforeAnyModelWrite() {
        String schema =
            """
            {"columns":[
              {"name":"project_total_cnt","dataType":"bigint","role":"MEASURE","tests":[]},
              {"name":"PROJECT_TOTAL_CNT","dataType":"bigint","role":"MEASURE","tests":[]}
            ]}
            """;

        assertThatThrownBy(() -> DbtModelFieldProjector.project(objectMapper, schema, List.of()))
            .isInstanceOf(DbtImplementationDraftContract.DraftException.class)
            .extracting(error -> ((DbtImplementationDraftContract.DraftException) error).code())
            .isEqualTo("DBT_DRAFT_SCHEMA_INVALID");
    }
}
