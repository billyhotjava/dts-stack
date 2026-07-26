package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.AttributeSemantic;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ReuseScope;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ScopeType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationPolicy;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.LoadStrategy;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class Sprint73DimensionModelContractTest {

    private static final UUID DOMAIN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DATA_MART_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void dataMartDimensionCarriesOrderedSemanticAttributesAndOneBusinessKey() {
        CreateCommand command = new CreateCommand(
            DOMAIN_ID,
            "项目",
            "统一描述财务项目",
            "owner-1",
            ReuseScope.DOMAIN,
            List.of(),
            "create-project",
            ScopeType.DATA_MART,
            DATA_MART_ID,
            List.of(
                new AttributeSemantic("PROJECT_CODE", "项目编码", "项目业务唯一编码", true, "STD_PROJECT", "v1", 1),
                new AttributeSemantic("PROJECT_NAME", "项目名称", "项目正式名称", false, null, null, 2)
            )
        );

        assertThat(DimensionDefinitionContract.validateCreate(command)).isEmpty();
    }

    @Test
    void scopeAndAttributeInvariantsAreExplicit() {
        CreateCommand command = new CreateCommand(
            DOMAIN_ID,
            "项目",
            "统一描述财务项目",
            "owner-1",
            ReuseScope.DOMAIN,
            List.of(),
            "create-project",
            ScopeType.DATA_MART,
            null,
            List.of(
                new AttributeSemantic("PROJECT_CODE", "项目编码", "编码", true, null, null, 1),
                new AttributeSemantic("PROJECT_CODE", "重复编码", "重复", true, null, null, 3)
            )
        );

        assertThat(DimensionDefinitionContract.validateCreate(command))
            .extracting(FieldIssue::code)
            .contains("DIMENSION_DEFINITION_DATA_MART_REQUIRED", "DIMENSION_DEFINITION_ATTRIBUTES_INVALID");
    }

    @Test
    void implementationPolicySeparatesPhysicalNameLoadAndRetentionFromScd() {
        List<ModelField> fields = List.of(
            new ModelField("project_code", "varchar", false, null, FieldRole.KEY, null, "PROJECT_CODE", false, null)
        );
        ImplementationPolicy policy = new ImplementationPolicy(
            "dwd_finance_project",
            LoadStrategy.INCREMENTAL,
            365,
            List.of("project_code")
        );

        assertThat(policy.physicalName()).isEqualTo("dwd_finance_project");
        assertThat(policy.loadStrategy()).isEqualTo(LoadStrategy.INCREMENTAL);
        assertThat(policy.retentionDays()).isEqualTo(365);
        assertThat(fields.getFirst().dimensionAttributeCode()).isEqualTo("PROJECT_CODE");
        assertThat(ModelSpecContract.validatePhysicalName(policy.physicalName())).isEmpty();
        assertThat(ModelSpecContract.validatePhysicalName("财务项目表")).isNotEmpty();
    }

    @Test
    void dimensionAttributeBudgetRejectsMoreThanTwoHundredEntries() {
        List<AttributeSemantic> attributes = IntStream
            .rangeClosed(1, 201)
            .mapToObj(index ->
                new AttributeSemantic(
                    "ATTRIBUTE_" + index,
                    "属性" + index,
                    "业务属性" + index,
                    index == 1,
                    null,
                    null,
                    index
                )
            )
            .toList();
        CreateCommand command = new CreateCommand(
            DOMAIN_ID,
            "超大维度",
            "容量边界测试",
            "owner-1",
            ReuseScope.DOMAIN,
            List.of(),
            "create-large-dimension",
            ScopeType.DOMAIN,
            null,
            attributes
        );

        assertThat(DimensionDefinitionContract.validateCreate(command))
            .extracting(FieldIssue::code)
            .contains("DIMENSION_DEFINITION_ATTRIBUTES_INVALID");
    }

    @Test
    void dimensionAttributeStandardReferenceAndVersionMustBePaired() {
        CreateCommand command = new CreateCommand(
            DOMAIN_ID,
            "项目",
            "统一描述财务项目",
            "owner-1",
            ReuseScope.DOMAIN,
            List.of(),
            "create-project-with-incomplete-standard",
            ScopeType.DOMAIN,
            null,
            List.of(new AttributeSemantic("PROJECT_CODE", "项目编码", "编码", true, "STD_PROJECT", null, 1))
        );

        assertThat(DimensionDefinitionContract.validateCreate(command))
            .extracting(FieldIssue::code)
            .contains("DIMENSION_DEFINITION_ATTRIBUTES_INVALID");
    }

    @Test
    void implementationRetentionRejectsValuesAboveTheOperationalBudget() {
        assertThat(
            ModelSpecContract.validateCreateShape(
                Map.of(
                    "implementationPolicy",
                    Map.of(
                        "physicalName",
                        "dwd_finance_project",
                        "loadStrategy",
                        "FULL",
                        "retentionDays",
                        36_001,
                        "partitionFields",
                        List.of()
                    )
                )
            )
        )
            .extracting(ModelSpecContract.FieldIssue::code)
            .contains("MODEL_SPEC_IMPLEMENTATION_POLICY_INVALID");
    }
}
