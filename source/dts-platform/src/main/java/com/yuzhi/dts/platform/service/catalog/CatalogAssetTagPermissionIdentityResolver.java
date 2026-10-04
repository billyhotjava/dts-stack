package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionReadAdapter.BatchLookup;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionReadAdapter.BatchLookupRequest;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionReadAdapter.OpenMetadataDatasetIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionReadAdapter.PermissionIdentity;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class CatalogAssetTagPermissionIdentityResolver {

    private static final int MAX_BATCH_SIZE = 500;
    private static final String DEFAULT_TENANT = "default";
    private static final Pattern LEGACY_DATASET_KEY = Pattern.compile(
        "^source:(unknown|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/schema:([^/]+)/table:([^/]+)$"
    );
    private static final Pattern SCOPED_METRIC_KEY = Pattern.compile(
        "^tenant:([^/]+)/env:prod/dialect:generic/metric-pack:([^/]+)/metric:([^/]+)$"
    );
    private static final Pattern LOCAL_METRIC_KEY = Pattern.compile(
        "^metric:(local)/([^/]+)$"
    );
    private static final Pattern METRIC_PACK_KEY = Pattern.compile(
        "^tenant:([^/]+)/env:prod/dialect:generic/metric-pack:([^/]+)/version:([^/]+)$"
    );
    private static final Set<CatalogAssetType> SUPPORTED_TYPES = EnumSet.allOf(
        CatalogAssetType.class
    );

    private final CatalogAssetTagPermissionReadAdapter readAdapter;

    public CatalogAssetTagPermissionIdentityResolver(
        CatalogAssetTagPermissionReadAdapter readAdapter
    ) {
        this.readAdapter = readAdapter;
    }

    public ResolvedPermissionIdentity resolve(
        CatalogAssetType requestedType,
        String canonicalAssetKey
    ) {
        return resolveAll(
            List.of(
                new AssetRef(
                    requestedType == null ? null : requestedType.name(),
                    canonicalAssetKey
                )
            )
        ).getFirst();
    }

    public List<ResolvedPermissionIdentity> resolveAll(List<AssetRef> assets) {
        if (assets == null || assets.isEmpty()) {
            throw failure(null, null, "INVALID_PERMISSION_IDENTITY", "资产不能为空");
        }
        if (assets.size() > MAX_BATCH_SIZE) {
            throw new CatalogAssetTagPermissionException(
                HttpStatus.BAD_REQUEST,
                "BATCH_SIZE_EXCEEDED",
                null,
                null,
                "单次批量权限预检最多支持 500 个资产"
            );
        }
        List<RequestedIdentity> requested = assets
            .stream()
            .map(this::normalizeRequest)
            .toList();
        BatchLookup lookup = readAdapter.loadBatch(buildLookupRequest(requested));
        LookupIndex index = indexLookup(lookup);
        List<ResolvedPermissionIdentity> resolved = new ArrayList<>(requested.size());
        for (RequestedIdentity item : requested) {
            resolved.add(resolveOne(item, index));
        }
        return List.copyOf(resolved);
    }

    private RequestedIdentity normalizeRequest(AssetRef asset) {
        if (asset == null) {
            throw failure(null, null, "INVALID_PERMISSION_IDENTITY", "资产不能为空");
        }
        CatalogAssetType type;
        try {
            type = CatalogAssetType.from(asset.assetType());
        } catch (IllegalArgumentException exception) {
            throw new CatalogAssetTagPermissionException(
                HttpStatus.BAD_REQUEST,
                "INVALID_ASSET_TYPE",
                null,
                asset.assetKey(),
                "资产类型不合法"
            );
        }
        String key = StringUtils.hasText(asset.assetKey())
            ? asset.assetKey().trim()
            : null;
        if (!StringUtils.hasText(key)) {
            throw new CatalogAssetTagPermissionException(
                HttpStatus.BAD_REQUEST,
                "INVALID_ASSET_KEY",
                type,
                key,
                "资产标识不能为空"
            );
        }
        if (!SUPPORTED_TYPES.contains(type)) {
            throw failure(
                type,
                key,
                "UNSUPPORTED_PERMISSION_IDENTITY",
                "该资产类型尚无可靠的写权限身份链"
            );
        }
        return new RequestedIdentity(type, key);
    }

    private BatchLookupRequest buildLookupRequest(List<RequestedIdentity> requested) {
        Set<String> legacyDatasetKeys = new LinkedHashSet<>();
        Set<String> openMetadataDatasetKeys = new LinkedHashSet<>();
        Map<CatalogAssetType, Set<String>> naturalKeys = new EnumMap<>(
            CatalogAssetType.class
        );
        for (RequestedIdentity item : requested) {
            if (item.type() == CatalogAssetType.DATASET) {
                if (item.key().startsWith("tenant:")) {
                    throw failure(
                        item.type(),
                        item.key(),
                        "UNSUPPORTED_PERMISSION_IDENTITY",
                        "scoped dataset 尚无可验证的 legacy 写权限身份"
                    );
                }
                if (item.key().startsWith("om:")) {
                    validateOpenMetadataKey(item);
                    openMetadataDatasetKeys.add(item.key());
                } else {
                    validateLegacyDatasetKey(item);
                    legacyDatasetKeys.add(item.key());
                }
                continue;
            }
            String naturalKey = extractLookupKey(item);
            naturalKeys
                .computeIfAbsent(item.type(), ignored -> new LinkedHashSet<>())
                .add(naturalKey);
        }
        return new BatchLookupRequest(
            legacyDatasetKeys,
            openMetadataDatasetKeys,
            naturalKeys
        );
    }

    private LookupIndex indexLookup(BatchLookup lookup) {
        BatchLookup safeLookup = lookup == null
            ? new BatchLookup(List.of(), List.of(), Map.of())
            : lookup;
        Map<CatalogAssetType, Map<String, List<PermissionIdentity>>> identities = new EnumMap<>(
            CatalogAssetType.class
        );
        safeLookup
            .identities()
            .forEach((type, values) ->
                identities.put(type, groupPermissionIdentities(type, safeList(values)))
            );
        return new LookupIndex(
            groupByCanonicalKey(
                safeLookup.legacyDatasets(),
                CatalogAssetKey::dataset
            ),
            groupByCanonicalKey(
                safeLookup.openMetadataDatasets(),
                identity -> CatalogAssetKey.openMetadataDataset(identity.fqn())
            ),
            Collections.unmodifiableMap(identities)
        );
    }

    private Map<String, List<PermissionIdentity>> groupPermissionIdentities(
        CatalogAssetType type,
        List<PermissionIdentity> values
    ) {
        return groupByCanonicalKey(
            values,
            identity -> canonicalIdentityKey(type, identity)
        );
    }

    private ResolvedPermissionIdentity resolveOne(
        RequestedIdentity requested,
        LookupIndex index
    ) {
        return switch (requested.type()) {
            case CATALOG_DOMAIN ->
                resolveCodeIdentity(requested, index, "catalog-domain-code");
            case DATASET -> resolveDataset(requested, index);
            case DBT_MODEL ->
                resolveExactIdentity(requested, index, "dbt-model-unique-id");
            case BI_DATASET ->
                resolveExactIdentity(requested, index, "bi-dataset-id");
            case SCREEN -> resolveScreen(requested, index);
            case METRIC ->
                resolveExactIdentity(requested, index, "metric-registry");
            case METRIC_PACK ->
                resolveExactIdentity(requested, index, "metric-pack-registry");
            case SEMANTIC_MODEL ->
                resolveExactIdentity(requested, index, "semantic-model-id");
            case DATA_PRODUCT ->
                resolveCodeIdentity(requested, index, "data-product-code");
            case DATA_STANDARD ->
                resolveCodeIdentity(requested, index, "data-standard-code");
            case METADATA_STANDARD ->
                resolveCodeIdentity(requested, index, "metadata-standard-id");
            case GLOSSARY_TERM ->
                resolveCodeIdentity(requested, index, "glossary-term-code");
            case GOV_INDICATOR ->
                resolveCodeIdentity(requested, index, "indicator-code");
            case GOV_INDICATOR_TEMPLATE ->
                resolveCodeIdentity(
                    requested,
                    index,
                    "indicator-template-code"
                );
            case QUALITY_RULE ->
                resolveCodeIdentity(requested, index, "quality-rule-code");
            case SECURITY_POLICY ->
                resolveExactIdentity(requested, index, "dataset-security-policy");
            case API_SERVICE ->
                resolveCodeIdentity(requested, index, "api-code");
            case BACKFILL_REQUEST ->
                resolveCodeIdentity(requested, index, "backfill-request-id");
        };
    }

    private ResolvedPermissionIdentity resolveCodeIdentity(
        RequestedIdentity requested,
        LookupIndex index,
        String reason
    ) {
        PermissionIdentity identity = requireUnique(
            index
                .identities()
                .getOrDefault(requested.type(), Map.of())
                .getOrDefault(requested.key(), List.of()),
            requested
        );
        return codeIdentity(
            requested,
            identity,
            reason
        );
    }

    private ResolvedPermissionIdentity resolveExactIdentity(
        RequestedIdentity requested,
        LookupIndex index,
        String reason
    ) {
        PermissionIdentity identity = requireUnique(
            index
                .identities()
                .getOrDefault(requested.type(), Map.of())
                .getOrDefault(requested.key(), List.of()),
            requested
        );
        return permissionIdentity(requested, identity, reason);
    }

    private ResolvedPermissionIdentity resolveDataset(
        RequestedIdentity requested,
        LookupIndex index
    ) {
        List<CatalogDataset> legacyMatches = index
            .legacyByKey()
            .getOrDefault(requested.key(), List.of());
        if (!legacyMatches.isEmpty()) {
            CatalogDataset dataset = requireUnique(legacyMatches, requested);
            return resolved(
                requested,
                dataset.getId(),
                dataset,
                "legacy-dataset"
            );
        }
        if (!requested.key().startsWith("om:")) {
            throw notFound(requested);
        }
        OpenMetadataDatasetIdentity identity = requireUnique(
            index
                .openMetadataByKey()
                .getOrDefault(requested.key(), List.of()),
            requested
        );
        CatalogDataset legacy = identity.legacyDataset();
        if (
            identity.mappingId() == null ||
            identity.legacyDatasetId() == null ||
            legacy == null ||
            legacy.getId() == null ||
            !identity.legacyDatasetId().equals(legacy.getId())
        ) {
            throw failure(
                requested.type(),
                requested.key(),
                "UNMAPPED_DATASET_PERMISSION_IDENTITY",
                "OpenMetadata 数据集未映射到有效的 legacy dataset"
            );
        }
        return resolved(
            requested,
            legacy.getId(),
            legacy,
            "mapped-openmetadata-dataset"
        );
    }

    private ResolvedPermissionIdentity resolveScreen(
        RequestedIdentity requested,
        LookupIndex index
    ) {
        String screenId = extractScreenId(requested);
        PermissionIdentity identity = requireUnique(
            index
                .identities()
                .getOrDefault(CatalogAssetType.SCREEN, Map.of())
                .getOrDefault(requested.key(), List.of()),
            requested
        );
        if (
            !screenId.equals(identity.id()) ||
            !requested.key().equals(CatalogAssetKey.screen(identity.naturalKey()))
        ) {
            throw invalid(requested);
        }
        return new ResolvedPermissionIdentity(
            requested.type(),
            requested.key(),
            requested.type().name(),
            screenId,
            null,
            "enabled-screen"
        );
    }

    private String extractScreenId(RequestedIdentity requested) {
        String prefix = "screen:";
        if (
            !requested.key().startsWith(prefix) ||
            requested.key().length() == prefix.length()
        ) {
            throw invalid(requested);
        }
        String screenId = requested.key().substring(prefix.length());
        if (!requested.key().equals(CatalogAssetKey.screen(screenId))) {
            throw invalid(requested);
        }
        return screenId;
    }

    private String extractLookupKey(RequestedIdentity requested) {
        return switch (requested.type()) {
            case DBT_MODEL -> extractDbtUniqueId(requested);
            case BI_DATASET -> extractBiDatasetId(requested);
            case SCREEN -> extractScreenId(requested);
            case METRIC -> validateMetricKey(requested);
            case METRIC_PACK -> validateMetricPackKey(requested);
            case SEMANTIC_MODEL -> extractSemanticModelId(requested);
            default -> extractCodeNaturalKey(requested);
        };
    }

    private String extractDbtUniqueId(RequestedIdentity requested) {
        String prefix = "dbt:";
        if (
            !requested.key().startsWith(prefix) ||
            requested.key().length() == prefix.length()
        ) {
            throw invalid(requested);
        }
        String uniqueId = requested.key().substring(prefix.length());
        if (!requested.key().equals(CatalogAssetKey.dbtModel(uniqueId, null))) {
            throw invalid(requested);
        }
        return uniqueId;
    }

    private String extractBiDatasetId(RequestedIdentity requested) {
        String prefix = "bi-dataset:";
        if (
            !requested.key().startsWith(prefix) ||
            requested.key().length() == prefix.length()
        ) {
            throw invalid(requested);
        }
        String id = requireUuid(
            requested,
            requested.key().substring(prefix.length())
        );
        if (
            !requested
                .key()
                .equals(CatalogAssetKey.biDataset(UUID.fromString(id)))
        ) {
            throw invalid(requested);
        }
        return id;
    }

    private String extractSemanticModelId(RequestedIdentity requested) {
        String prefix = "semantic-model:";
        if (
            !requested.key().startsWith(prefix) ||
            requested.key().length() == prefix.length()
        ) {
            throw invalid(requested);
        }
        String id = requireUuid(
            requested,
            requested.key().substring(prefix.length())
        );
        if (!requested.key().equals(CatalogAssetKey.semanticModel(id))) {
            throw invalid(requested);
        }
        return id;
    }

    private String validateMetricKey(RequestedIdentity requested) {
        Matcher scopedMatcher = SCOPED_METRIC_KEY.matcher(requested.key());
        if (
            scopedMatcher.matches() &&
            requested
                .key()
                .equals(
                    CatalogAssetKey.metric(
                        scopedMatcher.group(1),
                        scopedMatcher.group(2),
                        scopedMatcher.group(3)
                    )
                )
        ) {
            return requested.key();
        }
        Matcher localMatcher = LOCAL_METRIC_KEY.matcher(requested.key());
        if (
            localMatcher.matches() &&
            requested
                .key()
                .equals(
                    CatalogAssetKey.metric(
                        localMatcher.group(1),
                        localMatcher.group(2)
                    )
                )
        ) {
            return requested.key();
        }
        throw invalid(requested);
    }

    private String validateMetricPackKey(RequestedIdentity requested) {
        Matcher matcher = METRIC_PACK_KEY.matcher(requested.key());
        if (
            !matcher.matches() ||
            !requested
                .key()
                .equals(
                    CatalogAssetKey.metricPack(
                        matcher.group(1),
                        matcher.group(2),
                        matcher.group(3)
                    )
                )
        ) {
            throw invalid(requested);
        }
        return requested.key();
    }

    private String extractCodeNaturalKey(RequestedIdentity requested) {
        String marker = "x";
        String template = CatalogAssetKey.codeAsset(
            requested.type(),
            DEFAULT_TENANT,
            marker
        );
        String prefix = template.substring(
            0,
            template.length() - marker.length()
        );
        if (
            !requested.key().startsWith(prefix) ||
            requested.key().length() == prefix.length()
        ) {
            throw invalid(requested);
        }
        String naturalKey = requested.key().substring(prefix.length());
        if (naturalKey.contains("/")) {
            throw invalid(requested);
        }
        if (
            EnumSet
                .of(
                    CatalogAssetType.METADATA_STANDARD,
                    CatalogAssetType.SECURITY_POLICY,
                    CatalogAssetType.BACKFILL_REQUEST
                )
                .contains(requested.type())
        ) {
            return requireUuid(requested, naturalKey);
        }
        return naturalKey;
    }

    private String requireUuid(
        RequestedIdentity requested,
        String candidate
    ) {
        try {
            return UUID.fromString(candidate).toString();
        } catch (IllegalArgumentException exception) {
            throw invalid(requested);
        }
    }

    private void validateOpenMetadataKey(RequestedIdentity requested) {
        String fqn = requested.key().substring("om:".length());
        if (
            !StringUtils.hasText(fqn) ||
            !requested.key().equals(CatalogAssetKey.openMetadataDataset(fqn))
        ) {
            throw invalid(requested);
        }
    }

    private void validateLegacyDatasetKey(RequestedIdentity requested) {
        Matcher matcher = LEGACY_DATASET_KEY.matcher(requested.key());
        if (!matcher.matches()) {
            throw invalid(requested);
        }
        UUID sourceId = "unknown".equals(matcher.group(1))
            ? null
            : UUID.fromString(matcher.group(1));
        String schema = matcher.group(2);
        String table = matcher.group(3);
        String regenerated = CatalogAssetKey.dataset(
            sourceId,
            schema,
            schema,
            table,
            table
        );
        if (!requested.key().equals(regenerated)) {
            throw invalid(requested);
        }
    }

    private ResolvedPermissionIdentity codeIdentity(
        RequestedIdentity requested,
        PermissionIdentity identity,
        String reason
    ) {
        if (
            identity == null ||
            !StringUtils.hasText(identity.naturalKey()) ||
            !StringUtils.hasText(identity.id())
        ) {
            throw invalid(requested);
        }
        String regenerated = CatalogAssetKey.codeAsset(
            requested.type(),
            DEFAULT_TENANT,
            identity.naturalKey()
        );
        if (!requested.key().equals(regenerated)) {
            throw invalid(requested);
        }
        return permissionIdentity(requested, identity, reason);
    }

    private ResolvedPermissionIdentity permissionIdentity(
        RequestedIdentity requested,
        PermissionIdentity identity,
        String reason
    ) {
        if (
            identity == null ||
            !StringUtils.hasText(identity.id()) ||
            !requested
                .key()
                .equals(canonicalIdentityKey(requested.type(), identity))
        ) {
            throw invalid(requested);
        }
        return new ResolvedPermissionIdentity(
            requested.type(),
            requested.key(),
            StringUtils.hasText(identity.grantAssetType())
                ? identity.grantAssetType()
                : requested.type().name(),
            identity.id(),
            identity.dataset(),
            reason
        );
    }

    private String canonicalIdentityKey(
        CatalogAssetType type,
        PermissionIdentity identity
    ) {
        if (StringUtils.hasText(identity.canonicalAssetKey())) {
            return identity.canonicalAssetKey().trim();
        }
        if (!StringUtils.hasText(identity.naturalKey())) {
            throw new IllegalArgumentException("identity natural key is required");
        }
        return switch (type) {
            case DBT_MODEL ->
                CatalogAssetKey.dbtModel(identity.naturalKey(), null);
            case BI_DATASET ->
                CatalogAssetKey.biDataset(
                    UUID.fromString(identity.naturalKey())
                );
            case SCREEN -> CatalogAssetKey.screen(identity.naturalKey());
            case SEMANTIC_MODEL ->
                CatalogAssetKey.semanticModel(identity.naturalKey());
            case METRIC, METRIC_PACK, DATASET ->
                throw new IllegalArgumentException(
                    "canonical identity key is required for " + type
                );
            default ->
                CatalogAssetKey.codeAsset(
                    type,
                    DEFAULT_TENANT,
                    identity.naturalKey()
                );
        };
    }

    private ResolvedPermissionIdentity resolved(
        RequestedIdentity requested,
        UUID id,
        CatalogDataset dataset,
        String reason
    ) {
        if (id == null) {
            throw invalid(requested);
        }
        return new ResolvedPermissionIdentity(
            requested.type(),
            requested.key(),
            requested.type().name(),
            id.toString(),
            dataset,
            reason
        );
    }

    private <T> Map<String, List<T>> groupByCanonicalKey(
        List<T> values,
        Function<T, String> keyFactory
    ) {
        Map<String, List<T>> result = new LinkedHashMap<>();
        for (T value : safeList(values)) {
            if (value == null) {
                continue;
            }
            try {
                String key = keyFactory.apply(value);
                result
                    .computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(value);
            } catch (IllegalArgumentException ignored) {
                // A malformed row cannot become an authorized identity.
            }
        }
        result.replaceAll((key, matches) -> List.copyOf(matches));
        return Collections.unmodifiableMap(result);
    }

    private <T> T requireUnique(
        List<T> matches,
        RequestedIdentity requested
    ) {
        if (matches.isEmpty()) {
            throw notFound(requested);
        }
        if (matches.size() != 1) {
            throw failure(
                requested.type(),
                requested.key(),
                "AMBIGUOUS_PERMISSION_IDENTITY",
                "资产标识存在多个候选实体"
            );
        }
        return matches.getFirst();
    }

    private CatalogAssetTagPermissionException invalid(
        RequestedIdentity requested
    ) {
        return failure(
            requested.type(),
            requested.key(),
            "INVALID_PERMISSION_IDENTITY",
            "资产标识未通过 canonical 全串校验"
        );
    }

    private CatalogAssetTagPermissionException notFound(
        RequestedIdentity requested
    ) {
        return failure(
            requested.type(),
            requested.key(),
            "ASSET_NOT_FOUND",
            "未找到对应的资产实体"
        );
    }

    private CatalogAssetTagPermissionException failure(
        CatalogAssetType type,
        String key,
        String reasonCode,
        String message
    ) {
        return new CatalogAssetTagPermissionException(
            HttpStatus.FORBIDDEN,
            reasonCode,
            type,
            key,
            message
        );
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record RequestedIdentity(CatalogAssetType type, String key) {}

    private record LookupIndex(
        Map<String, List<CatalogDataset>> legacyByKey,
        Map<String, List<OpenMetadataDatasetIdentity>> openMetadataByKey,
        Map<
            CatalogAssetType,
            Map<String, List<PermissionIdentity>>
        > identities
    ) {}

    public record ResolvedPermissionIdentity(
        CatalogAssetType requestedType,
        String canonicalAssetKey,
        String grantAssetType,
        String grantAssetId,
        CatalogDataset dataset,
        String resolutionReason
    ) {}
}
