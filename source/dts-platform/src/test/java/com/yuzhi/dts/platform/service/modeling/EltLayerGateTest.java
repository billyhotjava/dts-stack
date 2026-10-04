package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.EltLayerGate.Diagnostic;
import com.yuzhi.dts.platform.service.modeling.EltLayerGate.LayerNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class EltLayerGateTest {

    private final EltLayerGate gate = new EltLayerGate();

    private List<String> codes(LayerNode node) {
        return gate.evaluate(List.of(node)).stream().map(Diagnostic::code).toList();
    }

    @Test
    void dwsAndAdsAreValidModelingEntries() {
        assertThat(codes(new LayerNode("n1", "DWS", false, false, false))).isEmpty();
        assertThat(codes(new LayerNode("n2", "ADS", false, false, false))).isEmpty();
        // 大小写不敏感
        assertThat(codes(new LayerNode("n3", "dws", false, false, false))).isEmpty();
    }

    @Test
    void odsAndStgAreBlockedAsLineageOnly() {
        assertThat(codes(new LayerNode("n1", "ODS", true, true, false))).containsExactly(EltLayerGate.INVALID_LAYER);
        assertThat(codes(new LayerNode("n2", "STG", true, true, false))).containsExactly(EltLayerGate.INVALID_LAYER);
    }

    @Test
    void unknownLayerIsRejected() {
        assertThat(codes(new LayerNode("n1", "FOO", true, true, false))).containsExactly(EltLayerGate.INVALID_LAYER);
        assertThat(codes(new LayerNode("n2", "", true, true, false))).containsExactly(EltLayerGate.INVALID_LAYER);
    }

    @Test
    void dwdWithGrainAndStandardCodePasses() {
        assertThat(codes(new LayerNode("n1", "DWD", true, true, false))).isEmpty();
    }

    @Test
    void dwdMissingGrainOrStandardCodeOrConnectingToPublishIsBlocked() {
        assertThat(codes(new LayerNode("n1", "DWD", false, true, false))).containsExactly(EltLayerGate.GRAIN_MISMATCH);
        assertThat(codes(new LayerNode("n2", "DWD", true, false, false))).containsExactly(EltLayerGate.STANDARD_CODE_REQUIRED);
        assertThat(codes(new LayerNode("n3", "DWD", true, true, true))).containsExactly(EltLayerGate.INVALID_LAYER);
        // 多重缺失累加
        assertThat(codes(new LayerNode("n4", "DWD", false, false, false)))
            .containsExactly(EltLayerGate.GRAIN_MISMATCH, EltLayerGate.STANDARD_CODE_REQUIRED);
    }

    @Test
    void evaluatesAllNodesAndAggregatesDiagnostics() {
        List<Diagnostic> diagnostics = gate.evaluate(
            List.of(
                new LayerNode("ok", "DWS", false, false, false),
                new LayerNode("bad", "ODS", true, true, false)
            )
        );
        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).nodeId()).isEqualTo("bad");
        assertThat(diagnostics.get(0).code()).isEqualTo(EltLayerGate.INVALID_LAYER);
    }

    @Test
    void nullAndEmptyInputsAreSafe() {
        assertThat(gate.evaluate(null)).isEmpty();
        assertThat(gate.evaluate(List.of())).isEmpty();
    }
}
