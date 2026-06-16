package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * F3-T03：受控建模违规码解析单测。
 *
 * <p>{@code controlledViolationCode} 解析 ControlledMetricDslCompiler / EltLayerGate 抛出的 "code: message"
 * 前缀；handleSemanticValidationFailure 据此做状态映射：{@code unsafe_expression} → 422，
 * 分层码 → 400 并透出具体码，其余 → 400 + SEMANTIC_VALIDATION_FAILED（状态映射为该解析结果的平凡三元消费）。
 */
class SemanticModelingResourceTest {

    @Test
    void parsesKnownControlledViolationPrefixes() {
        assertThat(SemanticModelingResource.controlledViolationCode("unsafe_expression: 受控模式不支持的指标类型")).isEqualTo("unsafe_expression");
        assertThat(SemanticModelingResource.controlledViolationCode("invalid_layer: ODS/STG 仅用于血缘")).isEqualTo("invalid_layer");
        assertThat(SemanticModelingResource.controlledViolationCode("grain_mismatch: DWD 必须声明 grain")).isEqualTo("grain_mismatch");
        assertThat(SemanticModelingResource.controlledViolationCode("standard_code_required: 必须绑定标准码")).isEqualTo("standard_code_required");
    }

    @Test
    void returnsNullForUnknownOrUnprefixedMessages() {
        assertThat(SemanticModelingResource.controlledViolationCode("模型未绑定业务对象，不能提交审核")).isNull();
        assertThat(SemanticModelingResource.controlledViolationCode("foo: bar")).isNull();
        assertThat(SemanticModelingResource.controlledViolationCode(":leading")).isNull();
        assertThat(SemanticModelingResource.controlledViolationCode(null)).isNull();
        assertThat(SemanticModelingResource.controlledViolationCode("")).isNull();
    }
}
