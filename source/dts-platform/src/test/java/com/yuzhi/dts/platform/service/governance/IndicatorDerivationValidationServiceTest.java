package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IndicatorDerivationValidationServiceTest {

    private final GovIndicatorDefinitionRepository repository = mock(GovIndicatorDefinitionRepository.class);
    private final com.yuzhi.dts.platform.repository.governance.GovIndicatorVersionRepository versions = mock(com.yuzhi.dts.platform.repository.governance.GovIndicatorVersionRepository.class);
    private final IndicatorDerivationValidationService service = new IndicatorDerivationValidationService(
        repository,
        new ControlledIndicatorDerivationCompiler(),
        new ObjectMapper(),
        versions
    );

    private UUID targetId;

    @BeforeEach
    void setUp() {
        targetId = UUID.randomUUID();
    }

    @Test
    void validatesGovernanceIndicatorCodesAndReturnsCompiledExpression() {
        GovIndicatorDefinition target = indicator(targetId, "AVG_ORDER", "DRAFT", true);
        target.setDependencyIndicators("[\"GMV\",\"ORDER_COUNT\"]");
        target.setExpressionSql("{{metric:GMV}} / nullif({{metric:ORDER_COUNT}}, 0)");
        GovIndicatorDefinition gmv = indicator(UUID.randomUUID(), "GMV", "PUBLISHED", false);
        GovIndicatorDefinition count = indicator(UUID.randomUUID(), "ORDER_COUNT", "PUBLISHED", false);

        when(repository.findById(targetId)).thenReturn(Optional.of(target));
        PinnedIndicatorTestFixture.pin(target, repository, versions, gmv);
        PinnedIndicatorTestFixture.pin(target, repository, versions, gmv, count);

        IndicatorDerivationValidationResult result = service.validate(targetId);

        assertThat(result.valid()).isTrue();
        assertThat(result.compiledExpression()).isEqualTo("\"GMV\" / NULLIF((nullif(\"ORDER_COUNT\", 0)), 0)");
        assertThat(result.dependencyCodes()).containsExactly("GMV", "ORDER_COUNT");
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void stableMetricTypeOverridesStaleLegacyDerivedFlag() {
        GovIndicatorDefinition target = indicator(targetId, "AVG_ORDER", "DRAFT", false);
        target.setMetricType("DERIVED");
        target.setDependencyIndicators("[\"GMV\"]");
        target.setExpressionSql("{{metric:GMV}}");
        GovIndicatorDefinition gmv = indicator(UUID.randomUUID(), "GMV", "PUBLISHED", false);

        when(repository.findById(targetId)).thenReturn(Optional.of(target));
        PinnedIndicatorTestFixture.pin(target, repository, versions, gmv);

        IndicatorDerivationValidationResult result = service.validate(targetId);

        assertThat(result.valid()).isTrue();
        assertThat(result.issueCodes()).doesNotContain("DERIVATION_NOT_DERIVED");
    }

    @Test
    void rejectsMissingPinsInsteadOfResolvingTheLatestCode() {
        GovIndicatorDefinition target = indicator(targetId, "AVG_ORDER", "DRAFT", true);
        target.setDependencyIndicators("[\"GMV\"]"); target.setExpressionSql("{{metric:GMV}}");
        when(repository.findById(targetId)).thenReturn(Optional.of(target));
        var result = service.validate(targetId);
        assertThat(result.valid()).isFalse();
        assertThat(result.issueCodes()).contains("DERIVATION_DEPENDENCY_MISSING");
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findFirstByCodeIgnoreCase(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsUnpublishedPinnedSnapshot() {
        var target = indicator(targetId, "RATIO", "DRAFT", true);
        target.setDependencyIndicators("[\"GMV\"]"); target.setExpressionSql("{{metric:GMV}}");
        var dependency = indicator(UUID.randomUUID(), "GMV", "DRAFT", false);
        PinnedIndicatorTestFixture.pin(target, repository, versions, dependency);
        when(repository.findById(targetId)).thenReturn(Optional.of(target));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.validate(targetId)).hasMessageContaining("未发布");
    }

    @Test
    void rejectsExpressionDependencyDriftAndIncompatibleJoinGrain() {
        GovIndicatorDefinition target = indicator(targetId, "AVG_ORDER", "DRAFT", true);
        target.setDependencyIndicators("[\"GMV\",\"ORDER_COUNT\"]");
        target.setExpressionSql("{{metric:GMV}} + 1");
        target.setDimensionFields("[\"region_code\"]");
        target.setTimeGrain("MONTH");

        GovIndicatorDefinition gmv = indicator(UUID.randomUUID(), "GMV", "PUBLISHED", false);
        gmv.setDimensionFields("[\"region_code\"]");
        gmv.setTimeGrain("MONTH");
        GovIndicatorDefinition count = indicator(UUID.randomUUID(), "ORDER_COUNT", "PUBLISHED", false);
        count.setDimensionFields("[\"store_code\"]");
        count.setTimeGrain("DAY");

        when(repository.findById(targetId)).thenReturn(Optional.of(target));
        PinnedIndicatorTestFixture.pin(target, repository, versions, gmv);
        PinnedIndicatorTestFixture.pin(target, repository, versions, gmv, count);

        IndicatorDerivationValidationResult result = service.validate(targetId);

        assertThat(result.valid()).isFalse();
        assertThat(result.issueCodes())
            .contains("DERIVATION_EXPRESSION_DEPENDENCY_MISMATCH", "DERIVATION_GRAIN_INCOMPATIBLE");
    }

    @Test
    void rejectsDerivedMetricWhoseClassificationIsLowerThanDependency() {
        GovIndicatorDefinition target = indicator(targetId, "PUBLIC_RATIO", "DRAFT", true);
        target.setDataLevel("DATA_PUBLIC");
        target.setDependencyIndicators("[\"SECRET_GMV\"]");
        target.setExpressionSql("{{metric:SECRET_GMV}}");
        GovIndicatorDefinition dependency = indicator(UUID.randomUUID(), "SECRET_GMV", "PUBLISHED", false);
        dependency.setDataLevel("DATA_SECRET");

        when(repository.findById(targetId)).thenReturn(Optional.of(target));
        PinnedIndicatorTestFixture.pin(target, repository, versions, dependency);

        IndicatorDerivationValidationResult result = service.validate(targetId);

        assertThat(result.valid()).isFalse();
        assertThat(result.issueCodes()).contains("DERIVATION_CLASSIFICATION_DOWNGRADE");
    }

    private static GovIndicatorDefinition indicator(UUID id, String code, String status, boolean derived) {
        GovIndicatorDefinition value = new GovIndicatorDefinition();
        value.setId(id);
        value.setVersion("v1");
        value.setCode(code);
        value.setName(code);
        value.setStatus(status);
        value.setIsDerived(derived);
        return value;
    }
}
