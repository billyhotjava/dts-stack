package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.*;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.service.governance.IndicatorAnalysisContract.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class IndicatorQueryPlanTest {
    private final UUID datasource = UUID.randomUUID();
    private final Map<VersionRef, GovIndicatorDefinition> definitions = new LinkedHashMap<>();
    private final ControlledIndicatorDerivationCompiler compiler = new ControlledIndicatorDerivationCompiler();

    @Test void aggregatesInputsBeforeAlignmentAndKeepsFilterValuesOutOfSql() {
        var numerator = atomic("AMOUNT"); var denominator = atomic("ORDERS");
        var ratio = formula("AVERAGE", numerator, denominator);
        String hostile = "east' OR 1=1 --";
        var query = new Query(List.of(ratio), null, List.of("region"), List.of(new Predicate("region", "EQ", hostile)), 100);
        var plan = planner(query).build();
        assertThat(plan.versions()).containsExactly(ratio, numerator, denominator);
        assertThat(plan.parameters()).containsExactly(hostile, hostile);
        assertThat(plan.sql()).doesNotContain(hostile).contains("GROUP BY", " UNION ", "IS NOT DISTINCT FROM", "NULLIF", "MISSING_DEPENDENCY_GROUP", "ZERO_DENOMINATOR");
        assertThat(plan.sql().split("LIMIT", -1)).hasSize(2);
    }

    @Test void rejectsUnmappedFiltersBeforeAnyExecution() {
        var ref = atomic("TOTAL");
        var query = new Query(List.of(ref), null, List.of(), List.of(new Predicate("private_field", "EQ", "x")), 10);
        assertThatThrownBy(() -> planner(query).build()).isInstanceOf(IndicatorConflictException.class).hasMessageContaining("没有公共维度映射");
    }

    @Test void neverPicksTheLatestVersion() {
        var v1 = atomic("TOTAL"); var v2 = new VersionRef(v1.id(), "v2");
        GovIndicatorDefinition second = new GovIndicatorDefinition(); second.setMeasureField("new_amount"); definitions.put(v2, second);
        var plan = planner(new Query(List.of(v1), null, List.of(), List.of(), 10)).build();
        assertThat(plan.versions()).containsExactly(v1);
        assertThat(plan.sql()).doesNotContain("new_amount");
    }

    @Test void rejectsCrossSourceFormulasAndPrecomputedRollups() {
        var one = atomic("A"); var two = atomic("B"); var ratio = formula("R", one, two);
        var query = new Query(List.of(ratio), null, List.of("region"), List.of(), 10);
        assertThatThrownBy(() -> new IndicatorQueryPlan(query, definitions::get, d -> source(d.getCode().equals("A") ? datasource : UUID.randomUUID()), compiler).build())
            .hasMessageContaining("跨数据源");
        definitions.get(one).setMetricType("DERIVED"); definitions.get(one).setExecutionMode("PRECOMPUTED");
        assertThatThrownBy(() -> planner(new Query(List.of(one), null, List.of(), List.of(), 10)).build()).hasMessageContaining("结果粒度");
    }

    @Test void failsClosedWhenSnapshotAccessIsDenied() {
        var ref = atomic("TOTAL");
        var query = new Query(List.of(ref), null, List.of(), List.of(), 10);
        assertThatThrownBy(() -> new IndicatorQueryPlan(query, ignored -> { throw new org.springframework.security.access.AccessDeniedException("denied"); }, d -> source(datasource), compiler).build())
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private IndicatorQueryPlan planner(Query query) { return new IndicatorQueryPlan(query, definitions::get, d -> source(datasource), compiler); }
    private IndicatorQueryPlan.Source source(UUID id) { return new IndicatorQueryPlan.Source(id, "public", "facts", Set.of("region_code", "amount")); }
    private VersionRef atomic(String code) {
        VersionRef ref = new VersionRef(UUID.randomUUID(), "v1");
        GovIndicatorDefinition value = new GovIndicatorDefinition(); value.setId(ref.id()); value.setVersion(ref.version()); value.setCode(code); value.setMetricType("ATOMIC");
        value.setMeasureField("amount"); value.setAggregationType("SUM");
        value.setAnalysisConfig(IndicatorMapper.writeAnalysisConfig(new Config(Map.of("region", "region_code"), null, List.of("region"), List.of("SUM"), List.of(), List.of(), null, null, false)));
        definitions.put(ref, value); return ref;
    }
    private VersionRef formula(String code, VersionRef left, VersionRef right) {
        var ref = atomic(code); var value = definitions.get(ref); value.setMetricType("DERIVED"); value.setExecutionMode("FORMULA");
        value.setSourceRefs("[{\"sourceType\":\"INDICATOR_VERSION\",\"sourceId\":\"" + left.id() + "\",\"sourceVersion\":\"v1\"},{\"sourceType\":\"INDICATOR_VERSION\",\"sourceId\":\"" + right.id() + "\",\"sourceVersion\":\"v1\"}]");
        value.setExpressionSql("{{metric:" + definitions.get(left).getCode() + "}} / {{metric:" + definitions.get(right).getCode() + "}}"); return ref;
    }
}
