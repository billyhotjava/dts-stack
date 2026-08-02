package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.DriftAction;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.DriftInput;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.MappingPin;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.MergeCheckpoint;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RenameMapping;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.TechnicalPin;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ModelSpecImportThreeWayReconcilerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE_EXTERNAL = checksum('a');
    private static final String BASE_IMPLEMENTATION = checksum('b');
    private static final String INCOMING = checksum('c');
    private static final String CURRENT = checksum('d');
    private static final String MODEL_ETAG = checksum('e');

    private final ModelSpecImportThreeWayReconciler reconciler = new ModelSpecImportThreeWayReconciler(MAPPER);

    @ParameterizedTest
    @MethodSource("matrix")
    void evaluatesTheFiveCanonicalThreeWayRows(
        String incoming,
        String current,
        DriftAction action,
        String reasonCode
    ) {
        var decision = reconciler.evaluate(input(checkpoint(), incoming, current, new MappingPin(7, MODEL_ETAG)));

        assertThat(decision.action()).isEqualTo(action);
        assertThat(decision.reasonCode()).isEqualTo(reasonCode);
    }

    private static Stream<Arguments> matrix() {
        return Stream.of(
            Arguments.of(BASE_EXTERNAL, BASE_IMPLEMENTATION, DriftAction.SKIP, "NO_CHANGE"),
            Arguments.of(BASE_EXTERNAL, CURRENT, DriftAction.SKIP, "CURRENT_ONLY_CHANGED"),
            Arguments.of(INCOMING, BASE_IMPLEMENTATION, DriftAction.UPDATE, "INCOMING_ONLY_CHANGED"),
            Arguments.of(INCOMING, INCOMING, DriftAction.SKIP, "CONVERGED_CHANGE"),
            Arguments.of(INCOMING, CURRENT, DriftAction.CONFLICT, "BOTH_CHANGED_DIVERGED")
        );
    }

    @Test
    void failsClosedWhenNoAcceptedBaseExists() {
        var decision = reconciler.evaluate(input(null, INCOMING, CURRENT, new MappingPin(7, MODEL_ETAG)));

        assertThat(decision.action()).isEqualTo(DriftAction.CONFLICT);
        assertThat(decision.reasonCode()).isEqualTo("BASE_CHECKPOINT_MISSING");
    }

    @Test
    void treatsModelEtagDriftAsMappingRevalidationAndPreservesBusinessSemantics() throws Exception {
        JsonNode currentModel = MAPPER.readTree(
            """
            {
              "name":"客户确认名称",
              "description":"客户业务定义",
              "businessActivityRef":"FIN_BUDGET",
              "consumptionScenario":"预算执行分析",
              "grain":{"statement":"一张凭证一行"},
              "standardBindings":[{"fieldName":"amount","referenceCode":"FIN_AMOUNT"}],
              "governanceBindings":{"owner":"finance"},
              "fields":[{"name":"amount","displayName":"预算金额","dataType":"decimal(18,2)","role":"MEASURE","securityLevel":"INTERNAL"}],
              "materialization":"table"
            }
            """
        );
        JsonNode incomingModel = MAPPER.readTree(
            """
            {
              "name":"dbt_model_name",
              "description":"external docs",
              "businessActivityRef":"external",
              "consumptionScenario":"external",
              "grain":{"statement":"unknown"},
              "standardBindings":[],
              "governanceBindings":{},
              "fields":[{"name":"amount","displayName":"amount","dataType":"decimal(20,4)","role":"ATTRIBUTE","securityLevel":"PUBLIC"}],
              "materialization":"incremental"
            }
            """
        );
        DriftInput input = new DriftInput(
            checkpoint(),
            new TechnicalPin(9, BASE_IMPLEMENTATION),
            INCOMING,
            new MappingPin(8, checksum('f')),
            currentModel,
            incomingModel
        );

        var decision = reconciler.evaluate(input);

        assertThat(decision.action()).isEqualTo(DriftAction.BLOCKED_REMAP);
        assertThat(decision.reasonCode()).isEqualTo("MAPPING_STALE");
        assertThat(decision.proposedModelSpec().path("name").asText()).isEqualTo("客户确认名称");
        assertThat(decision.proposedModelSpec().path("description").asText()).isEqualTo("客户业务定义");
        assertThat(decision.proposedModelSpec().path("businessActivityRef").asText()).isEqualTo("FIN_BUDGET");
        assertThat(decision.proposedModelSpec().path("consumptionScenario").asText()).isEqualTo("预算执行分析");
        assertThat(decision.proposedModelSpec().path("grain").path("statement").asText()).isEqualTo("一张凭证一行");
        assertThat(decision.proposedModelSpec().path("standardBindings")).hasSize(1);
        assertThat(decision.proposedModelSpec().path("governanceBindings").path("owner").asText()).isEqualTo("finance");
        assertThat(decision.proposedModelSpec().path("fields").get(0).path("displayName").asText()).isEqualTo("预算金额");
        assertThat(decision.proposedModelSpec().path("fields").get(0).path("role").asText()).isEqualTo("MEASURE");
        assertThat(decision.proposedModelSpec().path("fields").get(0).path("securityLevel").asText()).isEqualTo("INTERNAL");
        assertThat(decision.proposedModelSpec().path("fields").get(0).path("dataType").asText()).isEqualTo("decimal(20,4)");
        assertThat(decision.proposedModelSpec().path("materialization").asText()).isEqualTo("incremental");
    }

    @Test
    void continuesTheTechnicalMatrixAfterAnExplicitMappingRevalidation() {
        DriftInput input = new DriftInput(
            checkpoint(),
            new TechnicalPin(9, BASE_IMPLEMENTATION),
            INCOMING,
            new MappingPin(8, checksum('f')),
            MAPPER.createObjectNode(),
            MAPPER.createObjectNode(),
            true
        );

        var decision = reconciler.evaluate(input);

        assertThat(decision.action()).isEqualTo(DriftAction.UPDATE);
        assertThat(decision.reasonCode()).isEqualTo("INCOMING_ONLY_CHANGED");
        assertThat(decision.mappingRevalidationRequired()).isTrue();
    }

    @Test
    void acceptsOnlyExplicitBijectiveRenameMappings() {
        assertThat(
            reconciler.validateRenameMappings(
                List.of(
                    new RenameMapping("model.finance.old_a", "model.finance.new_a"),
                    new RenameMapping("model.finance.old_b", "model.finance.new_b")
                )
            )
        ).containsExactlyEntriesOf(
            java.util.Map.of(
                "model.finance.old_a",
                "model.finance.new_a",
                "model.finance.old_b",
                "model.finance.new_b"
            )
        );
        assertThatThrownBy(() ->
            reconciler.validateRenameMappings(
                List.of(
                    new RenameMapping("model.finance.old_a", "model.finance.new"),
                    new RenameMapping("model.finance.old_b", "model.finance.new")
                )
            )
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bijective");
    }

    private static DriftInput input(
        MergeCheckpoint checkpoint,
        String incoming,
        String current,
        MappingPin mapping
    ) {
        return new DriftInput(
            checkpoint,
            new TechnicalPin(9, current),
            incoming,
            mapping,
            MAPPER.createObjectNode(),
            MAPPER.createObjectNode()
        );
    }

    private static MergeCheckpoint checkpoint() {
        return new MergeCheckpoint(
            "tenant-a",
            "finance",
            "model.finance.fact_budget",
            BASE_EXTERNAL,
            5,
            BASE_IMPLEMENTATION,
            7,
            MODEL_ETAG
        );
    }

    private static String checksum(char value) {
        return String.valueOf(value).repeat(64);
    }
}
