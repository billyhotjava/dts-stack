package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import java.util.List;
import java.util.Map;
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

    @Test
    void marksVisualAggregateOutputsOfSummaryModelsAsMeasures() {
        List<ModelField> fields = List.of(
            field("risk_category", FieldRole.KEY),
            field("risk_count", FieldRole.ATTRIBUTE)
        );
        Map<String, Object> settings = Map.of(
            "groupBy", List.of("risk_category"),
            "aggregations", List.of(Map.of("targetField", "risk_count", "sourceField", "src_0.risk_name", "function", "COUNT", "distinct", false))
        );

        List<ModelField> projected = DbtModelFieldProjector.withAggregateMeasures(fields, ModelType.SUMMARY, settings);

        assertThat(projected).extracting(ModelField::role).containsExactly(FieldRole.KEY, FieldRole.MEASURE);
    }

    @Test
    void leavesFieldsUntouchedOutsideVisualSummaryAggregation() {
        List<ModelField> fields = List.of(field("dept", FieldRole.KEY), field("total", FieldRole.ATTRIBUTE));
        Map<String, Object> settings = Map.of(
            "aggregations", List.of(Map.of("targetField", "total", "sourceField", "src_0.amount", "function", "SUM", "distinct", false))
        );

        assertThat(DbtModelFieldProjector.withAggregateMeasures(fields, ModelType.FACT, settings)).isEqualTo(fields);
        assertThat(DbtModelFieldProjector.withAggregateMeasures(fields, ModelType.SUMMARY, null)).isEqualTo(fields);
        assertThat(DbtModelFieldProjector.withAggregateMeasures(fields, ModelType.SUMMARY, Map.of())).isEqualTo(fields);
    }

    @Test
    void keepsGrainKeysWhenAnAggregateTargetIsDeclaredAsKey() {
        List<ModelField> fields = List.of(field("cnt", FieldRole.KEY));
        Map<String, Object> settings = Map.of(
            "aggregations", List.of(Map.of("targetField", "cnt", "sourceField", "src_0.id", "function", "COUNT", "distinct", false))
        );

        assertThat(DbtModelFieldProjector.withAggregateMeasures(fields, ModelType.SUMMARY, settings))
            .extracting(ModelField::role)
            .containsExactly(FieldRole.KEY);
    }

    private static ModelField field(String name, FieldRole role) {
        return new ModelField(name, name, "text", false, null, role, null, null, false, null);
    }

    @Test
    void keepsAnExistingDisplayNameWhenTheGeneratedContractHasNoDescription() {
        ModelField existing = new ModelField(
            "project_id",
            "项目编号",
            "string",
            true,
            "source.project_id",
            FieldRole.KEY,
            null,
            null,
            false,
            null
        );
        String schema =
            """
            {"columns":[{"name":"project_id","dataType":"text","role":"KEY","tests":["not_null"]}]}
            """;

        List<ModelField> projected = DbtModelFieldProjector.project(objectMapper, schema, List.of(existing));

        assertThat(projected).singleElement().satisfies(field -> {
            assertThat(field.displayName()).isEqualTo("项目编号");
            assertThat(field.dataType()).isEqualTo("text");
        });
    }
}
