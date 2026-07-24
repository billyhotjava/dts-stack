package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ControlledIndicatorDerivationCompilerTest {

    private final ControlledIndicatorDerivationCompiler compiler = new ControlledIndicatorDerivationCompiler();

    @Test
    void compilesOnlyDeclaredMetricTokensAndWhitelistedFunctions() {
        assertThat(
            compiler.compile(
                "{{metric:GMV}} / nullif({{metric:ORDER_COUNT}}, 0)",
                List.of("GMV", "ORDER_COUNT")
            )
        ).isEqualTo("\"GMV\" / nullif(\"ORDER_COUNT\", 0)");
    }

    @Test
    void supportsGeneratorOwnedAliasesWithoutAcceptingUserIdentifiers() {
        assertThat(
            compiler.compile(
                "round({{metric:GMV}} / nullif({{metric:ORDER_COUNT}}, 0), 2)",
                Map.of("GMV", "dep_0.GMV", "ORDER_COUNT", "dep_1.ORDER_COUNT")
            )
        ).isEqualTo("round(dep_0.GMV / nullif(dep_1.ORDER_COUNT, 0), 2)");
    }

    @Test
    void rejectsRawSqlUnknownFunctionsAndUndeclaredTokens() {
        assertThatThrownBy(() -> compiler.compile("{{metric:GMV}}; drop table orders", List.of("GMV")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> compiler.compile("pg_sleep({{metric:GMV}})", List.of("GMV")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> compiler.compile("{{metric:SECRET}} + 1", List.of("GMV")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
