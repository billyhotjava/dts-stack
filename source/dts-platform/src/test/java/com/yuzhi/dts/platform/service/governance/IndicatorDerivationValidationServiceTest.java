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
    private final IndicatorDerivationValidationService service = new IndicatorDerivationValidationService(
        repository,
        new ControlledIndicatorDerivationCompiler(),
        new ObjectMapper()
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
        when(repository.findFirstByCodeIgnoreCase("GMV")).thenReturn(Optional.of(gmv));
        when(repository.findFirstByCodeIgnoreCase("ORDER_COUNT")).thenReturn(Optional.of(count));

        IndicatorDerivationValidationResult result = service.validate(targetId);

        assertThat(result.valid()).isTrue();
        assertThat(result.compiledExpression()).isEqualTo("\"GMV\" / nullif(\"ORDER_COUNT\", 0)");
        assertThat(result.dependencyCodes()).containsExactly("GMV", "ORDER_COUNT");
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void reportsMissingUnpublishedAndCircularDependenciesAgainstGovernanceIndicators() {
        GovIndicatorDefinition target = indicator(targetId, "AVG_ORDER", "DRAFT", true);
        target.setDependencyIndicators("[\"DRAFT_METRIC\",\"MISSING\",\"CYCLE_A\"]");
        target.setExpressionSql("{{metric:DRAFT_METRIC}} + {{metric:MISSING}} + {{metric:CYCLE_A}}");
        GovIndicatorDefinition draft = indicator(UUID.randomUUID(), "DRAFT_METRIC", "DRAFT", false);
        GovIndicatorDefinition cycle = indicator(UUID.randomUUID(), "CYCLE_A", "PUBLISHED", true);
        cycle.setDependencyIndicators("[\"AVG_ORDER\"]");

        when(repository.findById(targetId)).thenReturn(Optional.of(target));
        when(repository.findFirstByCodeIgnoreCase("DRAFT_METRIC")).thenReturn(Optional.of(draft));
        when(repository.findFirstByCodeIgnoreCase("MISSING")).thenReturn(Optional.empty());
        when(repository.findFirstByCodeIgnoreCase("CYCLE_A")).thenReturn(Optional.of(cycle));
        when(repository.findFirstByCodeIgnoreCase("AVG_ORDER")).thenReturn(Optional.of(target));

        IndicatorDerivationValidationResult result = service.validate(targetId);

        assertThat(result.valid()).isFalse();
        assertThat(result.issueCodes())
            .contains("DERIVATION_DEPENDENCY_NOT_PUBLISHED", "DERIVATION_DEPENDENCY_MISSING", "DERIVATION_CYCLE");
    }

    private static GovIndicatorDefinition indicator(UUID id, String code, String status, boolean derived) {
        GovIndicatorDefinition value = new GovIndicatorDefinition();
        value.setId(id);
        value.setCode(code);
        value.setName(code);
        value.setStatus(status);
        value.setIsDerived(derived);
        return value;
    }
}
