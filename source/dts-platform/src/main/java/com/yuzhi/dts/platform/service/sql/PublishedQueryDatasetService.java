package com.yuzhi.dts.platform.service.sql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetVersionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.sql.dto.AnalysisDatasetDetail;
import com.yuzhi.dts.platform.service.sql.dto.AnalysisDatasetPage;
import com.yuzhi.dts.platform.service.sql.dto.AnalysisDatasetRuntimeContract;
import com.yuzhi.dts.platform.service.sql.dto.AnalysisDatasetSummary;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/** Read-only projection of immutable published query-dataset contracts for governed BI. */
@Service
@Transactional(readOnly = true)
public class PublishedQueryDatasetService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final TypeReference<List<Map<String, Object>>> OBJECT_LIST = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final QueryDatasetAssetRepository assetRepository;
    private final QueryDatasetVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    public PublishedQueryDatasetService(
        QueryDatasetAssetRepository assetRepository,
        QueryDatasetVersionRepository versionRepository,
        ObjectMapper objectMapper
    ) {
        this.assetRepository = assetRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
    }

    public AnalysisDatasetPage list(PublishedQueryDatasetQuery requested, String activeDept) {
        PublishedQueryDatasetQuery query = normalize(requested);
        List<AnalysisDatasetSummary> matched = new ArrayList<>();
        for (QueryDatasetAsset asset : assetRepository.findByEnabledTrueOrderByLastModifiedDateDesc()) {
            if (!isPublishedEnabled(asset) || !isReadable(asset, activeDept)) continue;
            QueryDatasetVersion version = versionRepository
                .findByDataset_IdAndVersionNo(asset.getId(), asset.getPublishedVersion())
                .orElse(null);
            if (version == null || !isReadyPublished(version)) continue;
            ContractView contract = contract(version);
            AnalysisDatasetSummary summary = summary(asset, version, contract);
            if (matches(summary, contract, query)) matched.add(summary);
        }

        long total = matched.size();
        int from = Math.min(query.page() * query.size(), matched.size());
        int to = Math.min(from + query.size(), matched.size());
        int totalPages = total == 0 ? 0 : (int) ((total + query.size() - 1) / query.size());
        return new AnalysisDatasetPage(List.copyOf(matched.subList(from, to)), query.page(), query.size(), total, totalPages);
    }

    public AnalysisDatasetDetail publishedDetail(UUID datasetId, int versionNo, String activeDept) {
        QueryDatasetAsset asset = requireAsset(datasetId);
        assertReadable(asset, activeDept);
        QueryDatasetVersion version = requirePublishedVersion(asset, versionNo);
        ContractView contract = contract(version);
        return new AnalysisDatasetDetail(
            summary(asset, version, contract),
            contract.dimensions(),
            contract.metrics(),
            contract.joins(),
            contract.policyRefs()
        );
    }

    public AnalysisDatasetRuntimeContract runtimeContract(UUID datasetId, int versionNo) {
        QueryDatasetAsset asset = requireAsset(datasetId);
        QueryDatasetVersion version = requirePublishedVersion(asset, versionNo);
        ContractView contract = contract(version);
        return new AnalysisDatasetRuntimeContract(
            asset.getId(),
            version.getVersionNo(),
            "PUBLISHED",
            asset.getSourceDatasourceId(),
            version.getSqlText(),
            contract.dimensions(),
            contract.metrics(),
            contract.joins(),
            contract.policyRefs(),
            contract.classification(),
            version.getSemanticContractVersion(),
            version.getSemanticContractChecksum()
        );
    }

    private QueryDatasetAsset requireAsset(UUID datasetId) {
        return assetRepository
            .findById(datasetId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查询数据集不存在"));
    }

    private QueryDatasetVersion requirePublishedVersion(QueryDatasetAsset asset, int versionNo) {
        if (!isPublishedEnabled(asset)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "查询数据集未处于可消费发布状态");
        }
        QueryDatasetVersion version = versionRepository
            .findByDataset_IdAndVersionNo(asset.getId(), versionNo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查询数据集版本不存在"));
        if (!Integer.valueOf(versionNo).equals(asset.getPublishedVersion()) || !isReadyPublished(version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "查询数据集版本未发布或契约快照不可用");
        }
        contract(version);
        return version;
    }

    private ContractView contract(QueryDatasetVersion version) {
        String contractJson = trimToNull(version.getSemanticContractJson());
        String checksum = trimToNull(version.getSemanticContractChecksum());
        if (contractJson == null || checksum == null || !checksum.equals(checksum(contractJson, version.getSqlText()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "查询数据集契约校验失败");
        }
        try {
            JsonNode root = objectMapper.readTree(contractJson);
            if (root == null || !root.isObject()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "查询数据集契约格式无效");
            }
            List<Map<String, Object>> dimensions = objectList(root.path("dimensions"));
            List<Map<String, Object>> metrics = objectList(root.path("metrics"));
            List<Map<String, Object>> joins = objectList(root.path("joins"));
            List<String> policyRefs = stringList(root.path("policyRefs"));
            List<String> modelNames = new ArrayList<>();
            List<String> modelReferences = new ArrayList<>();
            if (root.path("sourceModels").isArray()) {
                root.path("sourceModels").forEach(model -> {
                    if (StringUtils.hasText(model.path("modelName").asText(null))) {
                        modelNames.add(model.path("modelName").asText().trim());
                    }
                    if (StringUtils.hasText(model.path("reference").asText(null))) {
                        modelReferences.add(model.path("reference").asText().trim());
                    }
                });
            }
            String warehouseLayer = warehouseLayer(modelReferences, modelNames);
            return new ContractView(
                dimensions,
                metrics,
                joins,
                policyRefs,
                List.copyOf(modelNames),
                warehouseLayer,
                trimToNull(root.path("classificationFloor").asText(null)),
                trimToNull(root.path("bizDomain").asText(null))
            );
        } catch (ResponseStatusException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "查询数据集契约格式无效", failure);
        }
    }

    private AnalysisDatasetSummary summary(QueryDatasetAsset asset, QueryDatasetVersion version, ContractView contract) {
        return new AnalysisDatasetSummary(
            asset.getId(),
            version.getVersionNo(),
            asset.getName(),
            asset.getDescription(),
            asset.getOwnerDept(),
            asset.getSourceDatasourceId(),
            asset.getSourceDatasourceName(),
            contract.warehouseLayer(),
            contract.classification(),
            asset.getRefreshStrategy(),
            version.getSemanticContractVersion(),
            contract.modelNames(),
            version.getSemanticContractChecksum(),
            asset.getLastModifiedDate()
        );
    }

    private boolean matches(
        AnalysisDatasetSummary summary,
        ContractView contract,
        PublishedQueryDatasetQuery query
    ) {
        if (!List.of("DWS", "ADS").contains(summary.warehouseLayer())) return false;
        if (
            query.keyword() != null &&
            !containsIgnoreCase(summary.name(), query.keyword()) &&
            !containsIgnoreCase(summary.description(), query.keyword()) &&
            summary.semanticModelNames().stream().noneMatch(name -> containsIgnoreCase(name, query.keyword()))
        ) {
            return false;
        }
        if (query.ownerDept() != null && !DepartmentUtils.matches(summary.ownerDept(), query.ownerDept())) return false;
        if (query.warehouseLayer() != null && !query.warehouseLayer().equalsIgnoreCase(summary.warehouseLayer())) return false;
        if (query.classification() != null && !query.classification().equalsIgnoreCase(summary.classification())) return false;
        return query.bizDomain() == null || query.bizDomain().equalsIgnoreCase(contract.bizDomain());
    }

    private boolean isPublishedEnabled(QueryDatasetAsset asset) {
        return asset != null &&
        Boolean.TRUE.equals(asset.getEnabled()) &&
        "PUBLISHED".equalsIgnoreCase(asset.getStatus()) &&
        asset.getPublishedVersion() != null;
    }

    private boolean isReadyPublished(QueryDatasetVersion version) {
        return version != null &&
        "PUBLISHED".equalsIgnoreCase(version.getStatus()) &&
        "READY".equalsIgnoreCase(version.getContractSnapshotStatus()) &&
        QueryDatasetContractSnapshotAssembler.CONTRACT_SCHEMA.equals(version.getSemanticContractSchema());
    }

    private boolean isReadable(QueryDatasetAsset asset, String activeDept) {
        if (hasGlobalManageScope()) return true;
        String ownerDept = trimToNull(asset.getOwnerDept());
        if (ownerDept != null) return StringUtils.hasText(activeDept) && DepartmentUtils.matches(ownerDept, activeDept.trim());
        return SecurityUtils
            .getCurrentUserLogin()
            .map(login -> login.equalsIgnoreCase(String.valueOf(asset.getCreatedBy())))
            .orElse(false);
    }

    private void assertReadable(QueryDatasetAsset asset, String activeDept) {
        if (!isReadable(asset, activeDept)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问该查询数据集");
        }
    }

    private boolean hasGlobalManageScope() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.CATALOG_MAINTAINERS) ||
        SecurityUtils.isOpAdminAccount();
    }

    private PublishedQueryDatasetQuery normalize(PublishedQueryDatasetQuery requested) {
        int page = requested == null ? 0 : requested.page();
        int size = requested == null || requested.size() == 0 ? DEFAULT_PAGE_SIZE : requested.size();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数非法");
        }
        return new PublishedQueryDatasetQuery(
            page,
            size,
            trimToNull(requested == null ? null : requested.keyword()),
            trimToNull(requested == null ? null : requested.bizDomain()),
            trimToNull(requested == null ? null : requested.ownerDept()),
            upperToNull(requested == null ? null : requested.warehouseLayer()),
            upperToNull(requested == null ? null : requested.classification())
        );
    }

    private List<Map<String, Object>> objectList(JsonNode node) {
        return node != null && node.isArray() ? List.copyOf(objectMapper.convertValue(node, OBJECT_LIST)) : List.of();
    }

    private List<String> stringList(JsonNode node) {
        return node != null && node.isArray() ? List.copyOf(objectMapper.convertValue(node, STRING_LIST)) : List.of();
    }

    private static String warehouseLayer(List<String> references, List<String> modelNames) {
        String text = String.join(" ", references) + " " + String.join(" ", modelNames);
        String normalized = text.toUpperCase(Locale.ROOT);
        for (String layer : List.of("ADS", "DWS", "DWD", "ODS", "STG")) {
            if (normalized.matches(".*(^|[^A-Z0-9])" + layer + "([^A-Z0-9]|$).*") || normalized.contains(layer + "_")) {
                return layer;
            }
        }
        return "UNKNOWN";
    }

    private static boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private static String upperToNull(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String checksum(String contractJson, String sqlText) {
        try {
            String value = String.valueOf(contractJson) + "\n" + (sqlText == null ? "" : sqlText);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    public record PublishedQueryDatasetQuery(
        int page,
        int size,
        String keyword,
        String bizDomain,
        String ownerDept,
        String warehouseLayer,
        String classification
    ) {}

    private record ContractView(
        List<Map<String, Object>> dimensions,
        List<Map<String, Object>> metrics,
        List<Map<String, Object>> joins,
        List<String> policyRefs,
        List<String> modelNames,
        String warehouseLayer,
        String classification,
        String bizDomain
    ) {}
}
