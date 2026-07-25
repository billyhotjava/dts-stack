package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.domain.service.SvcApi;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataAssetCacheRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.repository.service.SvcApiRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class CatalogAssetIdentityResolver {

    private final OpenMetadataAssetCacheRepository assetRepository;
    private final CatalogAssetMappingRepository mappingRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final GovIndicatorDefinitionRepository indicatorRepository;
    private final ModelingSqlModelRepository sqlModelRepository;
    private final DataStandardRepository dataStandardRepository;
    private final ModelingGlossaryTermRepository glossaryTermRepository;
    private final SvcApiRepository svcApiRepository;
    private final CatalogAssetIdentityResolutionAuditService resolutionAuditService;

    public CatalogAssetIdentityResolver(
        OpenMetadataAssetCacheRepository assetRepository,
        CatalogAssetMappingRepository mappingRepository,
        CatalogDatasetRepository datasetRepository,
        GovIndicatorDefinitionRepository indicatorRepository,
        ModelingSqlModelRepository sqlModelRepository,
        DataStandardRepository dataStandardRepository,
        ModelingGlossaryTermRepository glossaryTermRepository,
        SvcApiRepository svcApiRepository,
        CatalogAssetIdentityResolutionAuditService resolutionAuditService
    ) {
        this.assetRepository = assetRepository;
        this.mappingRepository = mappingRepository;
        this.datasetRepository = datasetRepository;
        this.indicatorRepository = indicatorRepository;
        this.sqlModelRepository = sqlModelRepository;
        this.dataStandardRepository = dataStandardRepository;
        this.glossaryTermRepository = glossaryTermRepository;
        this.svcApiRepository = svcApiRepository;
        this.resolutionAuditService = resolutionAuditService;
    }

    public Optional<ResolvedAsset> resolve(String ref) {
        if (!StringUtils.hasText(ref)) {
            return Optional.empty();
        }
        String trimmed = ref.trim();
        UUID uuid = parseUuid(trimmed);
        if (uuid != null) {
            Optional<OpenMetadataAssetCache> omAsset = assetRepository.findById(uuid);
            if (omAsset.isPresent()) {
                OpenMetadataAssetCache asset = omAsset.orElseThrow();
                CatalogAssetMapping mapping = mappingRepository.findFirstByFqnIgnoreCase(asset.getFqn()).orElse(null);
                CatalogDataset legacy = resolveLegacy(mapping);
                return Optional.of(new ResolvedAsset(asset, mapping, legacy, "om_asset_id"));
            }
            Optional<CatalogDataset> legacy = datasetRepository.findById(uuid);
            if (legacy.isPresent()) {
                CatalogDataset dataset = legacy.orElseThrow();
                CatalogAssetMapping mapping = mappingRepository.findFirstByLegacyDatasetId(dataset.getId()).orElse(null);
                OpenMetadataAssetCache asset = mapping != null && StringUtils.hasText(mapping.getFqn())
                    ? assetRepository.findFirstByFqnIgnoreCase(mapping.getFqn()).orElse(null)
                    : null;
                return Optional.of(new ResolvedAsset(asset, mapping, dataset, "legacy_dataset_id"));
            }
        }
        Optional<OpenMetadataAssetCache> byEntity = assetRepository.findFirstByOmEntityId(trimmed);
        if (byEntity.isPresent()) {
            OpenMetadataAssetCache asset = byEntity.orElseThrow();
            CatalogAssetMapping mapping = mappingRepository.findFirstByFqnIgnoreCase(asset.getFqn()).orElse(null);
            return Optional.of(new ResolvedAsset(asset, mapping, resolveLegacy(mapping), "om_entity_id"));
        }
        Optional<OpenMetadataAssetCache> byFqn = assetRepository.findFirstByFqnIgnoreCase(trimmed);
        if (byFqn.isPresent()) {
            OpenMetadataAssetCache asset = byFqn.orElseThrow();
            CatalogAssetMapping mapping = mappingRepository.findFirstByFqnIgnoreCase(asset.getFqn()).orElse(null);
            return Optional.of(new ResolvedAsset(asset, mapping, resolveLegacy(mapping), "fqn"));
        }
        return Optional.empty();
    }

    public Optional<CatalogAssetIdentity> resolveIdentity(String ref) {
        Optional<CatalogAssetIdentity> catalogIdentity = resolve(ref).map(this::toIdentity);
        if (catalogIdentity.isPresent()) {
            return catalogIdentity;
        }
        Optional<CatalogAssetIdentity> codeIdentity = resolveCodeAssetIdentity(ref);
        if (codeIdentity.isPresent()) {
            return codeIdentity;
        }
        resolutionAuditService.recordFailure(
            StringUtils.trimWhitespace(ref),
            "CatalogAssetIdentityResolver",
            failureReason(ref)
        );
        return Optional.empty();
    }

    private CatalogDataset resolveLegacy(CatalogAssetMapping mapping) {
        if (mapping == null || mapping.getLegacyDatasetId() == null) {
            return null;
        }
        return datasetRepository.findById(mapping.getLegacyDatasetId()).orElse(null);
    }

    private UUID parseUuid(String value) {
        try {
            String normalized = normalizeUuid(value);
            return normalized == null ? null : UUID.fromString(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.regionMatches(true, 0, "urn:uuid:", 0, "urn:uuid:".length())) {
            return trimmed.substring("urn:uuid:".length()).trim();
        }
        return trimmed;
    }

    private CatalogAssetIdentity toIdentity(ResolvedAsset resolved) {
        if (resolved.legacyDataset() != null) {
            CatalogDataset dataset = resolved.legacyDataset();
            CatalogAssetSourceReference sourceRef = CatalogAssetSourceReference.legacyDataset(dataset, resolved.resolvedBy());
            return new CatalogAssetIdentity(
                CatalogAssetType.DATASET,
                CatalogAssetKey.dataset(dataset),
                dataset.getId() == null ? null : dataset.getId().toString(),
                sourceRef.stableRef()
            );
        }
        if (resolved.omAsset() != null) {
            OpenMetadataAssetCache asset = resolved.omAsset();
            CatalogAssetSourceReference sourceRef = CatalogAssetSourceReference.openMetadata(asset, resolved.resolvedBy());
            return new CatalogAssetIdentity(
                CatalogAssetType.DATASET,
                CatalogAssetKey.openMetadataDataset(asset),
                asset.getId() == null ? null : asset.getId().toString(),
                sourceRef.stableRef()
            );
        }
        throw new IllegalArgumentException("resolved asset has no catalog identity");
    }

    private Optional<CatalogAssetIdentity> resolveCodeAssetIdentity(String ref) {
        if (!StringUtils.hasText(ref)) {
            return Optional.empty();
        }
        String trimmed = ref.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.contains("/metric-pack:") && normalized.contains("/version:")) {
            return Optional.of(new CatalogAssetIdentity(CatalogAssetType.METRIC_PACK, trimmed, trimmed, "metric-pack-ref"));
        }
        if (isScopedDatasetKey(trimmed)) {
            return Optional.of(new CatalogAssetIdentity(CatalogAssetType.DATASET, trimmed, trimmed, "scoped-dataset-ref"));
        }

        UUID uuid = parseUuid(trimmed);
        if (uuid != null) {
            Optional<CatalogAssetIdentity> byId = resolveCodeAssetById(uuid);
            if (byId.isPresent()) {
                return byId;
            }
        }

        String typeHint = typeHint(trimmed);
        String naturalKey = naturalKey(trimmed);
        return switch (typeHint) {
            case "glossary", "glossary_term" -> resolveGlossaryTerm(naturalKey);
            case "data_standard", "standard" -> resolveDataStandard(naturalKey);
            case "gov_indicator", "indicator", "metric" -> resolveGovIndicator(naturalKey);
            case "modeling_sql_model", "sql_model", "dbt_model" -> resolveModelingSqlModel(naturalKey);
            case "api_service", "svc_api", "api" -> resolveApiService(naturalKey);
            case "metric_pack" -> Optional.of(metricPackIdentity(trimmed));
            default -> resolveCodeAssetByCode(naturalKey);
        };
    }

    private Optional<CatalogAssetIdentity> resolveCodeAssetById(UUID id) {
        Optional<GovIndicatorDefinition> indicator = indicatorRepository.findById(id);
        if (indicator.isPresent()) {
            return Optional.of(govIndicatorIdentity(indicator.orElseThrow()));
        }
        Optional<ModelingSqlModel> sqlModel = sqlModelRepository.findById(id);
        if (sqlModel.isPresent()) {
            return Optional.of(modelingSqlModelIdentity(sqlModel.orElseThrow()));
        }
        Optional<DataStandard> standard = dataStandardRepository.findById(id);
        if (standard.isPresent()) {
            return Optional.of(dataStandardIdentity(standard.orElseThrow()));
        }
        Optional<ModelingGlossaryTerm> glossaryTerm = glossaryTermRepository.findById(id);
        if (glossaryTerm.isPresent()) {
            return glossaryTerm.map(this::glossaryTermIdentity);
        }
        Optional<SvcApi> api = svcApiRepository.findById(id);
        return api.map(this::apiServiceIdentity);
    }

    private Optional<CatalogAssetIdentity> resolveCodeAssetByCode(String code) {
        Optional<CatalogAssetIdentity> indicator = resolveGovIndicator(code);
        if (indicator.isPresent()) {
            return indicator;
        }
        Optional<CatalogAssetIdentity> model = resolveModelingSqlModel(code);
        if (model.isPresent()) {
            return model;
        }
        Optional<CatalogAssetIdentity> standard = resolveDataStandard(code);
        if (standard.isPresent()) {
            return standard;
        }
        Optional<CatalogAssetIdentity> glossary = resolveGlossaryTerm(code);
        if (glossary.isPresent()) {
            return glossary;
        }
        return resolveApiService(code);
    }

    private Optional<CatalogAssetIdentity> resolveGovIndicator(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        return indicatorRepository.findFirstByCodeIgnoreCase(code).map(this::govIndicatorIdentity);
    }

    private Optional<CatalogAssetIdentity> resolveModelingSqlModel(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        String expected = code.trim();
        Optional<ModelingSqlModel> byName = sqlModelRepository.findFirstByNameIgnoreCase(expected);
        if (byName.isPresent()) {
            return byName.map(this::modelingSqlModelIdentity);
        }
        return sqlModelRepository.findFirstByAliasIgnoreCase(expected).map(this::modelingSqlModelIdentity);
    }

    private Optional<CatalogAssetIdentity> resolveDataStandard(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        return dataStandardRepository.findByCodeIgnoreCase(code).map(this::dataStandardIdentity);
    }

    private Optional<CatalogAssetIdentity> resolveGlossaryTerm(String code) {
        List<ModelingGlossaryTerm> terms = glossaryTermRepository.findByCodeLowerIn(List.copyOf(codeCandidates(code)));
        if (terms == null || terms.isEmpty()) {
            return Optional.empty();
        }
        return terms.stream().findFirst().map(this::glossaryTermIdentity);
    }

    private Optional<CatalogAssetIdentity> resolveApiService(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        return svcApiRepository.findFirstByCodeIgnoreCase(code).map(this::apiServiceIdentity);
    }

    private CatalogAssetIdentity govIndicatorIdentity(GovIndicatorDefinition indicator) {
        String naturalKey = firstText(indicator.getCode(), idText(indicator.getId()));
        return codeAssetIdentity(CatalogAssetType.GOV_INDICATOR, naturalKey, idText(indicator.getId()), "gov-indicator:" + naturalKey);
    }

    private CatalogAssetIdentity modelingSqlModelIdentity(ModelingSqlModel model) {
        String naturalKey = firstText(model.getName(), model.getAlias(), idText(model.getId()));
        return codeAssetIdentity(CatalogAssetType.MODELING_SQL_MODEL, naturalKey, idText(model.getId()), "modeling-sql-model:" + naturalKey);
    }

    private CatalogAssetIdentity dataStandardIdentity(DataStandard standard) {
        String naturalKey = firstText(standard.getCode(), idText(standard.getId()));
        return codeAssetIdentity(CatalogAssetType.DATA_STANDARD, naturalKey, idText(standard.getId()), "data-standard:" + naturalKey);
    }

    private CatalogAssetIdentity glossaryTermIdentity(ModelingGlossaryTerm term) {
        String naturalKey = firstText(term.getCode(), idText(term.getId()));
        return codeAssetIdentity(CatalogAssetType.GLOSSARY_TERM, naturalKey, idText(term.getId()), "glossary-term:" + naturalKey);
    }

    private CatalogAssetIdentity apiServiceIdentity(SvcApi api) {
        String naturalKey = firstText(api.getCode(), idText(api.getId()));
        return codeAssetIdentity(CatalogAssetType.API_SERVICE, naturalKey, idText(api.getId()), "api-service:" + naturalKey);
    }

    private CatalogAssetIdentity codeAssetIdentity(CatalogAssetType type, String naturalKey, String assetId, String sourceRef) {
        return new CatalogAssetIdentity(type, CatalogAssetKey.codeAsset(type, "default", naturalKey), assetId, sourceRef);
    }

    private CatalogAssetIdentity metricPackIdentity(String ref) {
        return new CatalogAssetIdentity(CatalogAssetType.METRIC_PACK, ref, ref, "metric-pack-ref");
    }

    private static Set<String> codeCandidates(String ref) {
        String value = ref == null ? null : ref.trim();
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(normalized);
        int colon = normalized.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length()) {
            candidates.add(normalized.substring(colon + 1));
        }
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < normalized.length()) {
            candidates.add(normalized.substring(slash + 1));
        }
        if (normalized.startsWith("glossary.")) {
            candidates.add(normalized.substring("glossary.".length()));
        }
        if (normalized.startsWith("data_standard.")) {
            candidates.add(normalized.substring("data_standard.".length()));
        }
        return candidates;
    }

    private static String typeHint(String ref) {
        String normalized = ref.trim().toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        if (colon > 0) {
            return normalized.substring(0, colon).replace('-', '_');
        }
        int dot = normalized.indexOf('.');
        if (dot > 0) {
            return normalized.substring(0, dot).replace('-', '_');
        }
        return "";
    }

    private static boolean isScopedDatasetKey(String ref) {
        String normalized = ref.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("tenant:")
            && normalized.contains("/env:")
            && normalized.contains("/dialect:")
            && normalized.contains("/source:")
            && normalized.contains("/schema:")
            && normalized.contains("/table:");
    }

    private String failureReason(String ref) {
        if (!StringUtils.hasText(ref)) {
            return CatalogAssetIdentityResolutionAuditService.REASON_LEGACY_FORMAT_NOT_RECOGNIZED;
        }
        String trimmed = ref.trim();
        if (trimmed.regionMatches(true, 0, "urn:uuid:", 0, "urn:uuid:".length()) && parseUuid(trimmed) == null) {
            return CatalogAssetIdentityResolutionAuditService.REASON_LEGACY_FORMAT_NOT_RECOGNIZED;
        }
        String explicitHint = explicitColonTypeHint(trimmed);
        if (StringUtils.hasText(explicitHint) && !isKnownTypeHint(explicitHint)) {
            return CatalogAssetIdentityResolutionAuditService.REASON_UNKNOWN_TYPE_HINT;
        }
        return CatalogAssetIdentityResolutionAuditService.REASON_TYPE_REPOSITORY_MISS;
    }

    private static String explicitColonTypeHint(String ref) {
        String normalized = ref.trim().toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        if (colon > 0) {
            return normalized.substring(0, colon).replace('-', '_');
        }
        return "";
    }

    private static boolean isKnownTypeHint(String typeHint) {
        return switch (typeHint) {
            case "glossary",
                "glossary_term",
                "data_standard",
                "standard",
                "gov_indicator",
                "indicator",
                "metric",
                "modeling_sql_model",
                "sql_model",
                "dbt_model",
                "api_service",
                "svc_api",
                "api",
                "metric_pack" -> true;
            default -> false;
        };
    }

    private static String naturalKey(String ref) {
        String normalized = ref.trim();
        int colon = normalized.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length()) {
            return normalized.substring(colon + 1);
        }
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < normalized.length()) {
            return normalized.substring(slash + 1);
        }
        int dot = normalized.indexOf('.');
        if (dot > 0 && dot + 1 < normalized.length()) {
            return normalized.substring(dot + 1);
        }
        return normalized;
    }

    private static boolean matches(String expected, String value) {
        return StringUtils.hasText(value) && expected.equalsIgnoreCase(value.trim());
    }

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String idText(UUID id) {
        return id == null ? null : id.toString();
    }

    public record ResolvedAsset(
        OpenMetadataAssetCache omAsset,
        CatalogAssetMapping mapping,
        CatalogDataset legacyDataset,
        String resolvedBy
    ) {}
}
