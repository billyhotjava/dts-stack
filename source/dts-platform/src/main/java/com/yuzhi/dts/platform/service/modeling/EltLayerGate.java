package com.yuzhi.dts.platform.service.modeling;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * ELT 分层准入闸（Sprint-41 / F3，整合自 dts-metrics 的分层诊断规则）。
 *
 * <p>受控模式下对建模涉及的资产/节点强制 ELT 分层准入：DWS/ADS 为建模入口、DWD 为受控高级建模
 * （必须声明 grain/主键并绑定标准码、不可直连发布节点）、ODS/STG 仅血缘禁止建模。
 *
 * <p>纯函数评估器：{@link #evaluate(List)} 返回诊断列表（不抛异常），由调用方（F3-T02 集成）决定如何
 * 据 {@link Diagnostic#code()} 阻断与映射 HTTP（F3-T03）。无状态、可单测、可被未来抽取——与
 * {@link ControlledMetricDslCompiler} 同属独立 {@code modeling} 组件，不耦合任何已退役的语义建模实现。
 */
@Component
public class EltLayerGate {

    /** 诊断码——与 dts-metrics 字面一致，便于前端复用。 */
    public static final String INVALID_LAYER = "invalid_layer";
    public static final String GRAIN_MISMATCH = "grain_mismatch";
    public static final String STANDARD_CODE_REQUIRED = "standard_code_required";

    /**
     * 建模节点的分层准入输入。
     *
     * @param nodeId            节点/资产标识（用于诊断定位）
     * @param warehouseLayer    数据层（DWS/ADS/DWD/ODS/STG，大小写不敏感）
     * @param hasGrain          是否声明了 grain 或主键
     * @param hasStandardCode   是否绑定了标准码
     * @param connectsToPublish 是否直连发布节点
     */
    public record LayerNode(String nodeId, String warehouseLayer, boolean hasGrain, boolean hasStandardCode, boolean connectsToPublish) {}

    /** 一条分层准入诊断（均为阻断级 ERROR）。 */
    public record Diagnostic(String nodeId, String code, String message) {}

    /** 评估所有节点，返回阻断诊断；空列表表示通过。 */
    public List<Diagnostic> evaluate(List<LayerNode> nodes) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (nodes == null) {
            return diagnostics;
        }
        for (LayerNode node : nodes) {
            evaluateNode(node, diagnostics);
        }
        return diagnostics;
    }

    private void evaluateNode(LayerNode node, List<Diagnostic> diagnostics) {
        if (node == null) {
            return;
        }
        String layer = node.warehouseLayer() == null ? "" : node.warehouseLayer().trim().toUpperCase(Locale.ROOT);
        switch (layer) {
            case "DWS", "ADS" -> {
                // 建模入口，放行。
            }
            case "DWD" -> {
                if (!node.hasGrain()) {
                    diagnostics.add(new Diagnostic(node.nodeId(), GRAIN_MISMATCH, "DWD 高级建模必须声明 grain 或主键"));
                }
                if (!node.hasStandardCode()) {
                    diagnostics.add(new Diagnostic(node.nodeId(), STANDARD_CODE_REQUIRED, "DWD 高级建模必须绑定标准码"));
                }
                if (node.connectsToPublish()) {
                    diagnostics.add(new Diagnostic(node.nodeId(), INVALID_LAYER, "DWD 节点不能直连发布节点"));
                }
            }
            case "ODS", "STG" -> diagnostics.add(new Diagnostic(node.nodeId(), INVALID_LAYER, "ODS/STG 仅用于血缘，不能作为建模入口"));
            default -> diagnostics.add(
                new Diagnostic(node.nodeId(), INVALID_LAYER, "未知或不支持的数据层: " + (StringUtils.hasText(layer) ? layer : "(空)"))
            );
        }
    }
}
