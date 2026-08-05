package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.FieldIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Regression reproduction for "创建维度失败（DIMENSION_DEFINITION_REQUEST_INVALID）":
 * replicates DimensionDefinitionResource.decode with the exact payload the workbench sends.
 */
class WorkbenchDimensionCreateDecodeReproTest {

    private static final Set<String> CREATE_FIELDS = DimensionDefinitionContract.CREATE_FIELDS;

    private final ObjectMapper lenientMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void workbenchConceptDimensionPayloadDecodesWithoutRequestInvalid() throws Exception {
        ObjectNode body = (ObjectNode) lenientMapper.readTree(
            """
            {
              "domainId":"20000000-0000-0000-0000-000000000001",
              "name":"预算科目",
              "definition":"统一预算科目定义",
              "ownerId":"owner-1",
              "reuseScope":"DOMAIN",
              "scopeType":"DOMAIN",
              "dataMartId":null,
              "attributes":[],
              "hierarchies":[],
              "idempotencyKey":"create-workbench-customer"
            }
            """
        );

        List<FieldIssue> issues = new ArrayList<>();
        body
            .fieldNames()
            .forEachRemaining(field -> {
                if (!CREATE_FIELDS.contains(field)) {
                    issues.add(new FieldIssue(field, "DIMENSION_DEFINITION_FIELD_NOT_ALLOWED", "Field is not allowed at this boundary"));
                }
            });
        assertThat(issues).as("unknown fields").isEmpty();

        CreateCommand command;
        try {
            command = strictObjectMapper().treeToValue(body, CreateCommand.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new AssertionError("DIMENSION_DEFINITION_FIELD_INVALID: " + exception.getMessage(), exception);
        }
        assertThat(command.name()).isEqualTo("预算科目");
        assertThat(command.scopeType()).isEqualTo(DimensionDefinitionContract.ScopeType.DOMAIN);
        assertThat(command.attributes()).isEmpty();
    }

    @Test
    void rejectsDomainCodeInsteadOfUuidWithRequestInvalid() {
        ObjectNode body = (ObjectNode) lenientMapper.createObjectNode();
        body.put("domainId", "FinanceDomain");
        body.put("name", "预算科目");
        body.put("definition", "统一预算科目定义");
        body.put("ownerId", "owner-1");
        body.put("reuseScope", "DOMAIN");
        body.put("scopeType", "DOMAIN");
        body.put("idempotencyKey", "create-domain-code-repro");

        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> strictObjectMapper().treeToValue(body, CreateCommand.class))
            .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class)
            .withFailMessage("domainId code must fail UUID parsing -> DIMENSION_DEFINITION_FIELD_INVALID");
    }

    private ObjectMapper strictObjectMapper() {
        return lenientMapper
            .copy()
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS
            )
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
