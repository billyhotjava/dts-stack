package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class IndicatorValidationSignatureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GovIndicatorDefinitionRepository repository = mock(GovIndicatorDefinitionRepository.class);

    @Test
    void changesWhenAnyRenderedArtifactInputChanges() {
        GovIndicatorDefinition dependency = completeIndicator("GMV");
        dependency.setStatus("PUBLISHED");
        dependency.setVersion("1.0");
        when(repository.findFirstByCodeIgnoreCase(anyString()))
            .thenAnswer(invocation -> "GMV".equalsIgnoreCase(invocation.getArgument(0))
                ? Optional.of(dependency)
                : Optional.empty());

        List<Mutation> mutations = List.of(
            mutation("derived", value -> value.setIsDerived(false)),
            mutation("dataset", value -> value.setDatasetId("dataset-2")),
            mutation("name", value -> value.setName("Changed name")),
            mutation("code", value -> value.setCode("CHANGED_CODE")),
            mutation("domain", value -> value.setDomain("operations")),
            mutation("aggregation", value -> value.setAggregationType("AVG")),
            mutation("measure", value -> value.setMeasureField("net_amount")),
            mutation("date", value -> value.setDateColumn("settled_at")),
            mutation("source table", value -> value.setSourceTable("fact_settlement")),
            mutation("source layer", value -> value.setSourceLayer("DWS")),
            mutation("target layer", value -> value.setTargetLayer("DWS")),
            mutation("numerator", value -> value.setNumeratorExpression("sum(net_amount)")),
            mutation("denominator", value -> value.setDenominatorExpression("count(payment_id)")),
            mutation("precision", value -> value.setPrecisionScale(4)),
            mutation("filter", value -> value.setStaticFilter("status = 'SETTLED'")),
            mutation(
                "join",
                value -> value.setJoinConfig(
                    "[{\"type\":\"LEFT\",\"table\":\"dim_store\",\"alias\":\"store\",\"on\":\"fact_orders.store_id = store.id\"}]"
                )
            ),
            mutation("expression", value -> value.setExpressionSql("SELECT AVG(amount) FROM fact_orders")),
            mutation(
                "dimensions",
                value -> value.setDimensionFields("[{\"field\":\"store_code\",\"displayName\":\"Store\"}]")
            ),
            mutation("time grain", value -> value.setTimeGrain("DAY")),
            mutation("window", value -> value.setWindowFunction("YOY")),
            mutation("unit", value -> value.setUnit("万元")),
            mutation("minimum threshold", value -> value.setThresholdMin(new BigDecimal("2.5"))),
            mutation("maximum threshold", value -> value.setThresholdMax(new BigDecimal("250.0"))),
            mutation("dependencies", value -> value.setDependencyIndicators("[\"GMV\",\"ORDER_COUNT\"]"))
        );

        for (Mutation mutation : mutations) {
            GovIndicatorDefinition baseline = completeIndicator("TARGET");
            String before = IndicatorValidationSignature.compute(baseline, objectMapper, repository);
            mutation.change().accept(baseline);

            assertThat(IndicatorValidationSignature.compute(baseline, objectMapper, repository))
                .as(mutation.label())
                .isNotEqualTo(before);
        }
    }

    @Test
    void canonicalJsonFormattingDoesNotInvalidateTheSameArtifact() {
        GovIndicatorDefinition left = completeIndicator("TARGET");
        left.setDimensionFields("[{\"displayName\":\"Region\",\"field\":\"region_code\"}]");
        left.setJoinConfig(
            "[{\"on\":\"fact_orders.region_id = region.id\",\"alias\":\"region\",\"table\":\"dim_region\",\"type\":\"LEFT\"}]"
        );
        left.setDependencyIndicators("[ \"GMV\" ]");

        GovIndicatorDefinition right = completeIndicator("TARGET");
        right.setDimensionFields("[ { \"field\" : \"region_code\", \"displayName\" : \"Region\" } ]");
        right.setJoinConfig(
            "[ { \"type\" : \"LEFT\", \"table\" : \"dim_region\", \"alias\" : \"region\", \"on\" : \"fact_orders.region_id = region.id\" } ]"
        );
        right.setDependencyIndicators("[\"GMV\"]");

        GovIndicatorDefinition dependency = completeIndicator("GMV");
        when(repository.findFirstByCodeIgnoreCase("GMV")).thenReturn(Optional.of(dependency));

        assertThat(IndicatorValidationSignature.compute(left, objectMapper, repository))
            .isEqualTo(IndicatorValidationSignature.compute(right, objectMapper, repository));
    }

    @Test
    void changesWhenDependencyArtifactStateChanges() {
        GovIndicatorDefinition target = completeIndicator("TARGET");
        GovIndicatorDefinition dependency = completeIndicator("GMV");
        when(repository.findFirstByCodeIgnoreCase("GMV")).thenReturn(Optional.of(dependency));

        String before = IndicatorValidationSignature.compute(target, objectMapper, repository);
        dependency.setDimensionFields("[{\"field\":\"store_code\"}]");

        assertThat(IndicatorValidationSignature.compute(target, objectMapper, repository))
            .isNotEqualTo(before);
    }

    @Test
    void rejectsTrailingJsonBeforeResolvingDependencies() {
        GovIndicatorDefinition trailingDimensions = completeIndicator("TARGET");
        trailingDimensions.setDimensionFields("[{\"field\":\"region_code\"}] trailing");

        assertThatThrownBy(() ->
            IndicatorValidationSignature.compute(trailingDimensions, objectMapper, repository)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dimensionFields");
        verify(repository, never()).findFirstByCodeIgnoreCase(anyString());

        GovIndicatorDefinition trailingDependencies = completeIndicator("TARGET");
        trailingDependencies.setDependencyIndicators("[\"GMV\"] trailing");

        assertThatThrownBy(() ->
            IndicatorValidationSignature.compute(trailingDependencies, objectMapper, repository)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dependencyIndicators");
        verify(repository, never()).findFirstByCodeIgnoreCase(anyString());
    }

    @Test
    void boundsDependencyCountAndInputBeforeRepositoryQueries() {
        GovIndicatorDefinition tooMany = completeIndicator("TARGET");
        List<String> dependencies = new java.util.ArrayList<>();
        for (int index = 0; index < 33; index++) {
            dependencies.add("\"DEP_" + index + "\"");
        }
        tooMany.setDependencyIndicators("[" + String.join(",", dependencies) + "]");

        assertThatThrownBy(() ->
            IndicatorValidationSignature.compute(tooMany, objectMapper, repository)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("32");
        verify(repository, never()).findFirstByCodeIgnoreCase(anyString());

        GovIndicatorDefinition tooLarge = completeIndicator("TARGET");
        tooLarge.setDependencyIndicators("[\"" + "A".repeat(20_000) + "\"]");

        assertThatThrownBy(() ->
            IndicatorValidationSignature.compute(tooLarge, objectMapper, repository)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("长度");
        verify(repository, never()).findFirstByCodeIgnoreCase(anyString());

        GovIndicatorDefinition padded = completeIndicator("TARGET");
        padded.setDependencyIndicators(" ".repeat(20_000) + "[\"GMV\"]");

        assertThatThrownBy(() ->
            IndicatorValidationSignature.compute(padded, objectMapper, repository)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("长度");
        verify(repository, never()).findFirstByCodeIgnoreCase(anyString());
    }

    @Test
    void repositoryFailureRemainsATechnicalException() {
        GovIndicatorDefinition target = completeIndicator("TARGET");
        DataAccessResourceFailureException databaseFailure =
            new DataAccessResourceFailureException("database unavailable");
        when(repository.findFirstByCodeIgnoreCase("GMV")).thenThrow(databaseFailure);

        assertThatThrownBy(() ->
            IndicatorValidationSignature.compute(target, objectMapper, repository)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("读取依赖指标状态失败")
            .hasCause(databaseFailure);
    }

    private static Mutation mutation(String label, Consumer<GovIndicatorDefinition> change) {
        return new Mutation(label, change);
    }

    private static GovIndicatorDefinition completeIndicator(String code) {
        GovIndicatorDefinition value = new GovIndicatorDefinition();
        value.setId(UUID.randomUUID());
        value.setIsDerived(true);
        value.setDatasetId("dataset-1");
        value.setName("Gross merchandise value");
        value.setCode(code);
        value.setDomain("sales");
        value.setAggregationType("SUM");
        value.setMeasureField("amount");
        value.setDateColumn("created_at");
        value.setSourceTable("fact_orders");
        value.setSourceLayer("DWD");
        value.setTargetLayer("ADS");
        value.setNumeratorExpression("sum(amount)");
        value.setDenominatorExpression("count(order_id)");
        value.setPrecisionScale(2);
        value.setStaticFilter("status = 'PAID'");
        value.setJoinConfig(
            "[{\"type\":\"LEFT\",\"table\":\"dim_region\",\"alias\":\"region\",\"on\":\"fact_orders.region_id = region.id\"}]"
        );
        value.setExpressionSql("SELECT SUM(amount) FROM fact_orders");
        value.setDimensionFields("[{\"field\":\"region_code\",\"displayName\":\"Region\"}]");
        value.setTimeGrain("MONTH");
        value.setWindowFunction("MOM");
        value.setUnit("元");
        value.setThresholdMin(new BigDecimal("1.5"));
        value.setThresholdMax(new BigDecimal("200.0"));
        value.setDependencyIndicators("[\"GMV\"]");
        value.setStatus("PUBLISHED");
        value.setVersion("1.0");
        value.setDataLevel("INTERNAL");
        value.setOwnerDept("D001");
        return value;
    }

    private record Mutation(String label, Consumer<GovIndicatorDefinition> change) {}
}
