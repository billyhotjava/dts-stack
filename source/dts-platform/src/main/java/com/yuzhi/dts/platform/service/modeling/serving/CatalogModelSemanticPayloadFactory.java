package com.yuzhi.dts.platform.service.modeling.serving;

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

    public CatalogModelSemanticPayloadFactory(
        ModelSpecReader modelSpecReader,
        CatalogModelSemanticIndicatorReadAdapter indicators,
        CatalogClassificationBoundary classifications
    ) {
        this.modelSpecReader = modelSpecReader;
        this.indicators = indicators;
        this.classifications = classifications;
    }

    public PublishPayload create(SyncCandidate candidate) {
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
        List<MetricPayload> metrics = indicators.findPublishedAtomicIndicators(model.id(), model.revision()).stream()
            .map(indicator -> metric(indicator, fieldsByName, securityLevel))
            .toList();
        String grain = model.grain() == null
            ? null
            : firstText(model.grain().statement(), String.join(", ", model.grain().keys()));
        return new PublishPayload(
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
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "PUBLIC", "DATA_PUBLIC" -> "PUBLIC";
            case "INTERNAL", "DATA_INTERNAL" -> "INTERNAL";
            case "SENSITIVE", "SECRET", "DATA_SENSITIVE", "DATA_SECRET" -> "SECRET";
            case "TOP_SECRET", "CONFIDENTIAL", "DATA_TOP_SECRET", "DATA_CONFIDENTIAL" -> "CONFIDENTIAL";
            default -> throw failure("CATALOG_MODEL_SEMANTIC_CLASSIFICATION_INVALID");
        };
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
