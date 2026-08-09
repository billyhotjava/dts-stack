package com.yuzhi.dts.platform.service.governance;

import static com.yuzhi.dts.platform.service.governance.IndicatorBusinessContextContract.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IndicatorBusinessContextContractTest {

    private static final UUID CATEGORY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_CATEGORY = UUID.fromString("11111111-1111-1111-1111-111111111112");
    private static final UUID DOMAIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_DOMAIN = UUID.fromString("22222222-2222-2222-2222-222222222223");
    private static final UUID PROCESS = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void acceptsCompleteAtomicContextWithVersionedPhysicalSource() {
        ValidationResult result = validateDeliverable(
            context(MetricType.ATOMIC, CATEGORY, DOMAIN, PROCESS, physicalSource("asset-1")),
            List.of()
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void rejectsAtomicContextWithoutStableProcessAndSource() {
        ValidationResult result = validateDeliverable(
            context(MetricType.ATOMIC, CATEGORY, DOMAIN, null),
            List.of()
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ValidationIssue::code)
            .contains("INDICATOR_BUSINESS_PROCESS_REQUIRED", "INDICATOR_SOURCE_REQUIRED");
    }

    @Test
    void acceptsDerivedContextOnlyWhenAllUpstreamSharesCategoryAndDomain() {
        BusinessContext upstreamA = context(MetricType.ATOMIC, CATEGORY, DOMAIN, PROCESS, physicalSource("asset-a"));
        BusinessContext upstreamB = context(MetricType.ATOMIC, CATEGORY, DOMAIN, PROCESS, physicalSource("asset-b"));
        BusinessContext target = context(
            MetricType.DERIVED,
            CATEGORY,
            DOMAIN,
            PROCESS,
            indicatorSource("indicator-a"),
            indicatorSource("indicator-b")
        );

        assertThat(validateDeliverable(target, List.of(upstreamA, upstreamB)).valid()).isTrue();
    }

    @Test
    void failsClosedForCrossCategoryDerivedOrCompositeIndicators() {
        BusinessContext upstreamA = context(MetricType.ATOMIC, CATEGORY, DOMAIN, PROCESS, physicalSource("asset-a"));
        BusinessContext upstreamB = context(MetricType.ATOMIC, OTHER_CATEGORY, OTHER_DOMAIN, PROCESS, physicalSource("asset-b"));
        BusinessContext target = context(
            MetricType.COMPOSITE,
            CATEGORY,
            null,
            PROCESS,
            indicatorSource("indicator-a"),
            indicatorSource("indicator-b")
        );

        ValidationResult result = validateDeliverable(target, List.of(upstreamA, upstreamB));

        assertThat(result.issues()).extracting(ValidationIssue::code)
            .contains("INDICATOR_CROSS_CATEGORY_NOT_SUPPORTED");
    }

    @Test
    void allowsSameCategoryCompositeAcrossDomainsWithoutInventingOneDomain() {
        BusinessContext upstreamA = context(MetricType.ATOMIC, CATEGORY, DOMAIN, PROCESS, physicalSource("asset-a"));
        BusinessContext upstreamB = context(MetricType.ATOMIC, CATEGORY, OTHER_DOMAIN, PROCESS, physicalSource("asset-b"));
        BusinessContext target = context(
            MetricType.COMPOSITE,
            CATEGORY,
            null,
            PROCESS,
            indicatorSource("indicator-a"),
            indicatorSource("indicator-b")
        );

        assertThat(validateDeliverable(target, List.of(upstreamA, upstreamB)).valid()).isTrue();
    }

    @Test
    void rejectsNaturalLanguageMetricGroupCodes() {
        BusinessContext context = new BusinessContext(CATEGORY, DOMAIN, PROCESS, MetricType.ATOMIC, "财务 核心", List.of());

        assertThat(validateDraft(context).issues()).extracting(ValidationIssue::code)
            .containsExactly("INDICATOR_METRIC_GROUP_CODE_INVALID");
    }

    private BusinessContext context(
        MetricType type,
        UUID category,
        UUID domain,
        UUID process,
        MetricSourceRef... refs
    ) {
        return new BusinessContext(category, domain, process, type, "core_metrics", List.of(refs));
    }

    private MetricSourceRef physicalSource(String id) {
        return new MetricSourceRef(SourceType.PHYSICAL_ASSET, id, "v1");
    }

    private MetricSourceRef indicatorSource(String id) {
        return new MetricSourceRef(SourceType.INDICATOR_VERSION, id, "v1");
    }
}
