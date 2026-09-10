package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.SyncCandidate;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary.ClassificationFact;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecReader;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.DimensionPayload;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.MetricPayload;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.PublishPayload;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticIndicatorReadAdapter.AtomicIndicator;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Builds a fail-closed Analytics contract from the immutable serving revision. */
@Component
public class CatalogModelSemanticPayloadFactory {

    private static final Set<String> AGGREGATIONS = Set.of("SUM", "COUNT", "AVG", "MIN", "MAX", "COUNT_DISTINCT");

    private final ModelSpecReader modelSpecReader;
    private final CatalogModelSemanticIndicatorReadAdapter indicators;
    private final CatalogClassificationBoundary classifications;
    private final com.yuzhi.dts.platform.service.governance.PublishedIndicatorVersionReader versions;
    private final com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository projections;

    public CatalogModelSemanticPayloadFactory(
        ModelSpecReader modelSpecReader,
        CatalogModelSemanticIndicatorReadAdapter indicators,
        CatalogClassificationBoundary classifications,
        com.yuzhi.dts.platform.service.governance.PublishedIndicatorVersionReader versions,
        com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository projections
    ) {
        this.modelSpecReader = modelSpecReader;
        this.indicators = indicators;
        this.classifications = classifications;
        this.versions = versions; this.projections = projections;
    }

    public PublishPayload create(SyncCandidate candidate) { return create(candidate, true); }

    private PublishPayload create(SyncCandidate candidate, boolean includeLegacyMetrics) {
        ModelServingProjection projection = candidate == null ? null : candidate.projection();
        ServingRef serving = projection == null ? null : projection.servingRef();
        if (projection == null || serving == null) {
            throw failure("CATALOG_MODEL_SEMANTIC_SERVING_REF_REQUIRED");
        }
        if (serving.sourceId() == null || !StringUtils.hasText(serving.schemaName()) || !StringUtils.hasText(serving.identifier())) {
            throw failure("CATALOG_MODEL_SEMANTIC_PHYSICAL_TARGET_REQUIRED");
        }
        ModelSpecView model = modelSpecReader.revision(
            projection.tenantId(),
            new ModelRevisionRef(serving.modelSpecId(), serving.modelRevision())
        );
        if (
            !Objects.equals(model.id(), projection.modelSpecId()) ||
            model.revision() != serving.modelRevision() ||
            !Objects.equals(model.checksum(), serving.modelChecksum())
        ) {
            throw failure("CATALOG_MODEL_SEMANTIC_REVISION_MISMATCH");
        }
        String expectedAssetKey = CatalogAssetKey.semanticModel(model.id().toString());
        if (!Objects.equals(expectedAssetKey, projection.catalogAssetKey())) {
            throw failure("CATALOG_MODEL_SEMANTIC_ASSET_IDENTITY_MISMATCH");
        }
        ClassificationFact classification = classifications.resolve("ASSET", expectedAssetKey).orElse(null);
        if (
            classification == null ||
            !classification.propagated() ||
            !StringUtils.hasText(classification.effectiveLevel())
        ) {
            throw failure("CATALOG_MODEL_SEMANTIC_CLASSIFICATION_NOT_READY");
        }
        String securityLevel = analyticsSecurityLevel(classification.effectiveLevel());
        List<ModelField> fields = model.fields() == null ? List.of() : model.fields();
        var fieldsByName = fields.stream()
            .filter(field -> field != null && StringUtils.hasText(field.name()))
            .collect(Collectors.toMap(field -> normalizeName(field.name()), Function.identity(), (left, right) -> left));
        List<DimensionPayload> dimensions = fields.stream()
            .filter(field -> field != null && StringUtils.hasText(field.name()) && field.role() != FieldRole.MEASURE)
            .map(field -> new DimensionPayload(
                field.name(),
                firstText(field.displayName(), field.name()),
                "dimension",
                field.role() == FieldRole.TIME ? "native" : null
            ))
            .toList();
        List<MetricPayload> metrics = (includeLegacyMetrics ? indicators.findPublishedAtomicIndicators(model.id(), model.revision()) : List.<AtomicIndicator>of()).stream()
            .map(indicator -> metric(indicator, fieldsByName, securityLevel))
            .toList();
        String grain = model.grain() == null
            ? null
            : firstText(model.grain().statement(), String.join(", ", model.grain().keys()));
        return new PublishPayload(
            projection.tenantId(),
            serving.sourceId().toString(),
            "model_spec_" + model.id().toString().replace("-", ""),
            serving.identifier(),
            serving.schemaName(),
            firstText(model.name(), serving.identifier()),
            model.description(),
            securityLevel,
            grain,
            "r" + model.revision(),
            true,
            metrics,
            dimensions,
            List.of()
        );
    }

    public PublishPayload createIndicator(com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.IndicatorSyncCandidate candidate) {
        var versionRef = new com.yuzhi.dts.platform.service.governance.IndicatorAnalysisContract.VersionRef(candidate.indicatorId(), candidate.indicatorVersion());
        var indicator = versions.read(versionRef);
        var host = host(versionRef, new java.util.HashSet<>(), new int[]{0}, 0);
        var projection = projections.findProjection(candidate.tenantId(), host.modelSpecId())
            .orElseThrow(() -> failure("CATALOG_MODEL_SEMANTIC_SERVING_REF_REQUIRED"));
        if (projection.servingRef() == null || projection.servingRef().modelRevision() != host.modelRevision()) throw failure("CATALOG_MODEL_SEMANTIC_REVISION_MISMATCH");
        var base = create(new SyncCandidate(projection, 0), false);
        String name = "indicator_" + candidate.indicatorId().toString().replace("-", "") + "_" + candidate.indicatorVersion();
        String assetKey = CatalogAssetKey.codeAsset(com.yuzhi.dts.platform.service.catalog.CatalogAssetType.GOV_INDICATOR, "default", indicator.getCode());
        var metric = new MetricPayload(name, indicator.getName() + " · " + candidate.indicatorVersion(), "GOVERNED", null,
            indicator.getUnit(), indicator.getDateColumn(), indicator.getTimeGrain(), indicator.getTags(),
            analyticsSecurityLevel(indicator.getDataLevel()), indicator.getDefinition(), candidate.indicatorId(), candidate.indicatorVersion(), "GOV_INDICATOR", assetKey, indicator.getAnalysisConfig());
        return new PublishPayload(base.tenantId(), base.platformDataSourceId(), base.modelName(), base.tableName(), base.schemaName(),
            base.label(), base.description(), base.securityLevel(), base.grain(), base.specVersion(), base.exposedToModeler(),
            List.of(metric), base.dimensions(), base.joins());
    }

    private com.yuzhi.dts.platform.service.governance.IndicatorImplementationRef host(
        com.yuzhi.dts.platform.service.governance.IndicatorAnalysisContract.VersionRef ref,
        java.util.Set<com.yuzhi.dts.platform.service.governance.IndicatorAnalysisContract.VersionRef> visiting, int[] expanded, int depth
    ) {
        if (++expanded[0] > 256 || depth > 32 || !visiting.add(ref)) throw failure("INDICATOR_DEPENDENCY_CYCLE");
        try {
            var indicator = versions.read(ref);
            if (!"FORMULA".equals(indicator.getExecutionMode())) {
                var implementation = versions.implementation(indicator);
                if (implementation == null) throw failure("INDICATOR_IMPLEMENTATION_REQUIRED");
                return implementation;
            }
            var dependencies = versions.dependencies(indicator);
            if (dependencies.isEmpty()) throw failure("INDICATOR_DEPENDENCY_REQUIRED");
            var hosts = dependencies.stream().map(dep -> host(dep, visiting, expanded, depth + 1)).toList();
            var first = hosts.get(0);
            var firstProjection = projections.findProjection("default", first.modelSpecId()).orElseThrow(() -> failure("INDICATOR_HOST_NOT_READY"));
            for (var item : hosts) {
                var projection = projections.findProjection("default", item.modelSpecId()).orElseThrow(() -> failure("INDICATOR_HOST_NOT_READY"));
                if (projection.servingRef() == null || firstProjection.servingRef() == null || projection.servingRef().modelRevision() != item.modelRevision()
                    || !Objects.equals(projection.servingRef().sourceId(), firstProjection.servingRef().sourceId())) throw failure("INDICATOR_CROSS_SOURCE_UNSUPPORTED");
            }
            return first;
        } finally { visiting.remove(ref); }
    }

    private static MetricPayload metric(
        AtomicIndicator indicator,
        java.util.Map<String, ModelField> fieldsByName,
        String modelSecurityLevel
    ) {
        if (indicator == null || !StringUtils.hasText(indicator.code()) || !StringUtils.hasText(indicator.measureField())) {
            throw failure("CATALOG_MODEL_SEMANTIC_ATOMIC_INDICATOR_INVALID");
        }
        String fieldName = normalizeName(indicator.measureField());
        ModelField measure = fieldsByName.get(fieldName);
        if (measure == null || measure.role() != FieldRole.MEASURE) {
            throw failure("CATALOG_MODEL_SEMANTIC_METRIC_FIELD_NOT_FOUND");
        }
        if (StringUtils.hasText(indicator.dateColumn()) && !fieldsByName.containsKey(normalizeName(indicator.dateColumn()))) {
            throw failure("CATALOG_MODEL_SEMANTIC_TIME_FIELD_NOT_FOUND");
        }
        String aggregation = normalizeAggregation(indicator.aggregationType());
        String metricLevel = StringUtils.hasText(indicator.dataLevel())
            ? analyticsSecurityLevel(indicator.dataLevel())
            : modelSecurityLevel;
        return new MetricPayload(
            indicator.code().trim(),
            firstText(indicator.name(), indicator.code()),
            aggregation,
            measure.name(),
            indicator.unit(),
            indicator.dateColumn(),
            normalizeTimeGrain(indicator.timeGrain()),
            indicator.tags(),
            metricLevel,
            indicator.definition()
        );
    }

    private static String normalizeAggregation(String value) {
        String aggregation = firstText(value, "SUM").toUpperCase(Locale.ROOT);
        if (!AGGREGATIONS.contains(aggregation)) {
            throw failure("CATALOG_MODEL_SEMANTIC_AGGREGATION_UNSUPPORTED");
        }
        return aggregation;
    }

    private static String normalizeTimeGrain(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private static String analyticsSecurityLevel(String value) {
        if (!StringUtils.hasText(value)) {
            throw failure("CATALOG_MODEL_SEMANTIC_CLASSIFICATION_INVALID");
        }
        // Canonical ladder and aliases live in SecurityLevelCatalog; unknown values still fail closed.
        String canonical = SecurityLevelCatalog.normalizeDataCode(value);
        if (canonical == null) {
            throw failure("CATALOG_MODEL_SEMANTIC_CLASSIFICATION_INVALID");
        }
        return canonical;
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstText(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return null;
    }

    private static SemanticPayloadException failure(String code) {
        return new SemanticPayloadException(code);
    }

    public static class SemanticPayloadException extends RuntimeException {

        public SemanticPayloadException(String code) {
            super(code);
        }
    }
}
