package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ControlledMetricDslCompiler.SqlDialect;
import org.junit.jupiter.api.Test;

class ControlledMetricDslCompilerTest {

    private final ControlledMetricDslCompiler compiler = new ControlledMetricDslCompiler(new ObjectMapper());

    private String pg(String type, String formula) {
        return compiler.compile(type, formula, SqlDialect.POSTGRES);
    }

    // ---- whitelist functions ----

    @Test
    void sumQuotesFieldForPostgres() {
        assertThat(pg("sum", "{\"field\":\"order_amount\"}")).isEqualTo("sum(\"order_amount\")");
    }

    @Test
    void namespacedAggregationTypesMatchWorkbenchFormulaOptions() {
        assertThat(pg("aggregation/sum", "{\"field\":\"order_amount\"}")).isEqualTo("sum(\"order_amount\")");
        assertThat(pg("aggregation/count_distinct", "{\"field\":\"order_id\"}")).isEqualTo("count(distinct \"order_id\")");
    }

    @Test
    void countWithoutFieldRendersCountStar() {
        assertThat(pg("count", "{}")).isEqualTo("count(*)");
    }

    @Test
    void countDistinctAvgMinMax() {
        assertThat(pg("count_distinct", "{\"field\":\"user_id\"}")).isEqualTo("count(distinct \"user_id\")");
        assertThat(pg("avg", "{\"field\":\"amount\"}")).isEqualTo("avg(\"amount\")");
        assertThat(pg("min", "{\"field\":\"amount\"}")).isEqualTo("min(\"amount\")");
        assertThat(pg("max", "{\"field\":\"amount\"}")).isEqualTo("max(\"amount\")");
    }

    @Test
    void ratioRendersNullSafeDivision() {
        String sql = pg(
            "ratio",
            "{\"numerator\":{\"aggregation\":\"count_distinct\",\"field\":\"project_id\"},\"denominator\":{\"aggregation\":\"count_distinct\",\"field\":\"dept_id\"}}"
        );
        assertThat(sql).isEqualTo("case when count(distinct \"dept_id\") = 0 then null else count(distinct \"project_id\") / count(distinct \"dept_id\") end");
    }

    @Test
    void countIfDistinctRendersConditionalCount() {
        String sql = pg(
            "count_if",
            "{\"field\":\"project_id\",\"condition\":{\"field\":\"status\",\"operator\":\"=\",\"value\":\"在研\"},\"distinct\":true}"
        );
        assertThat(sql).isEqualTo("count(distinct case when \"status\" = '在研' then \"project_id\" end)");
    }

    @Test
    void countIfNonDistinctRendersSumCase() {
        String sql = pg("count_if", "{\"condition\":{\"field\":\"flag\",\"operator\":\"=\",\"value\":\"Y\"}}");
        assertThat(sql).isEqualTo("sum(case when \"flag\" = 'Y' then 1 else 0 end)");
    }

    @Test
    void sumIfRendersConditionalSum() {
        String sql = pg("sum_if", "{\"field\":\"amount\",\"condition\":{\"field\":\"flag\",\"operator\":\"=\",\"value\":\"Y\"}}");
        assertThat(sql).isEqualTo("sum(case when \"flag\" = 'Y' then \"amount\" else 0 end)");
    }

    @Test
    void caseWhenPassesNumericLiteralsUnquoted() {
        String sql = pg("case_when", "{\"condition\":{\"field\":\"amount\",\"operator\":\">\",\"value\":\"100\"},\"then\":\"1\",\"else\":\"0\"}");
        assertThat(sql).isEqualTo("case when \"amount\" > 100 then 1 else 0 end");
    }

    @Test
    void qualifiedIdentifierQuotesEachSegment() {
        assertThat(pg("sum", "{\"field\":\"t.order_amount\"}")).isEqualTo("sum(\"t\".\"order_amount\")");
    }

    // ---- dialect ----

    @Test
    void dorisUsesBacktickQuotingAndDateTruncArgOrder() {
        assertThat(compiler.compile("sum", "{\"field\":\"amount\"}", SqlDialect.DORIS)).isEqualTo("sum(`amount`)");
        assertThat(compiler.compile("date_trunc", "{\"grain\":\"month\",\"field\":\"stat_date\"}", SqlDialect.DORIS))
            .isEqualTo("date_trunc(`stat_date`, 'month')");
    }

    @Test
    void dateTruncPostgresArgOrder() {
        assertThat(pg("date_trunc", "{\"grain\":\"month\",\"field\":\"stat_date\"}")).isEqualTo("date_trunc('month', \"stat_date\")");
    }

    // ---- safety: default-reject, no raw SQL, injection, bad operator/grain ----

    @Test
    void rawSqlTypesAreRejected() {
        assertThatThrownBy(() -> pg("sql", "{\"expression\":\"select 1\"}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pg("custom", "{\"expression\":\"drop table t\"}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pg("expression", "{\"expression\":\"x\"}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pg("unknown_fn", "{}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void injectionInIdentifierIsRejected() {
        assertThatThrownBy(() -> pg("sum", "{\"field\":\"amount; drop table users\"}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pg("sum", "{\"field\":\"amount) from secrets --\"}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void injectionInLiteralIsRejected() {
        assertThatThrownBy(() ->
            pg("count_if", "{\"condition\":{\"field\":\"flag\",\"operator\":\"=\",\"value\":\"x'; drop table t --\"}}")
        )
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownOperatorAndGrainAreRejected() {
        assertThatThrownBy(() -> pg("count_if", "{\"condition\":{\"field\":\"f\",\"operator\":\"like\",\"value\":\"a\"}}"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pg("date_trunc", "{\"grain\":\"fortnight\",\"field\":\"d\"}")).isInstanceOf(IllegalArgumentException.class);
    }
}
