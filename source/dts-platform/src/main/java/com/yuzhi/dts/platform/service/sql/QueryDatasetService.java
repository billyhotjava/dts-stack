package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetVersionRepository;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.sql.dto.CreateQueryDatasetFromExecutionRequest;
import com.yuzhi.dts.platform.service.sql.dto.CreateQueryDatasetVersionRequest;
import com.yuzhi.dts.platform.service.sql.dto.PublishQueryDatasetRequest;
import com.yuzhi.dts.platform.service.sql.dto.QueryDatasetResponse;
import com.yuzhi.dts.platform.service.sql.dto.QueryDatasetVersionResponse;
import java.lang.reflect.Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class QueryDatasetService {

    private final QueryDatasetAssetRepository assetRepository;
    private final QueryDatasetVersionRepository versionRepository;
    private final QueryExecutionRepository executionRepository;
    private final ResultSetRepository resultSetRepository;
    private final ModelingSqlModelRepository modelingSqlModelRepository;

    public QueryDatasetService(
        QueryDatasetAssetRepository assetRepository,
        QueryDatasetVersionRepository versionRepository,
        QueryExecutionRepository executionRepository,
        ResultSetRepository resultSetRepository,
        ModelingSqlModelRepository modelingSqlModelRepository
    ) {
        this.assetRepository = assetRepository;
        this.versionRepository = versionRepository;
        this.executionRepository = executionRepository;
        this.resultSetRepository = resultSetRepository;
        this.modelingSqlModelRepository = modelingSqlModelRepository;
    }

    @Transactional(readOnly = true)
    public List<QueryDatasetResponse> list(String activeDeptHeader) {
        String activeDept = resolveActiveDept(activeDeptHeader);
        Map<String, ModelingSqlModel> modelsByName = loadModelIndex();
        if (hasGlobalManageScope()) {
            return assetRepository.findByEnabledTrueOrderByLastModifiedDateDesc().stream().map(asset -> toDto(asset, modelsByName)).toList();
        }

        Map<UUID, QueryDatasetAsset> scoped = new LinkedHashMap<>();
        if (StringUtils.hasText(activeDept)) {
            // Avoid exact owner_dept matching. Dept codes may have format variants.
            for (QueryDatasetAsset asset : assetRepository.findByEnabledTrueOrderByLastModifiedDateDesc()) {
                if (asset == null || asset.getId() == null) {
                    continue;
                }
                String ownerDept = trimToNull(asset.getOwnerDept());
                if (ownerDept != null && DepartmentUtils.matches(ownerDept, activeDept)) {
                    scoped.put(asset.getId(), asset);
                }
            }
        }

        String login = currentLogin();
        List<QueryDatasetAsset> ownRows = assetRepository.findByCreatedByOrderByLastModifiedDateDesc(login)
            .stream()
            .filter(QueryDatasetAsset::getEnabled)
            .toList();
        for (QueryDatasetAsset asset : ownRows) {
            if (asset == null || asset.getId() == null) {
                continue;
            }
            if (isDeptVisible(asset.getOwnerDept(), activeDept)) {
                scoped.putIfAbsent(asset.getId(), asset);
            }
        }

        return new ArrayList<>(scoped.values()).stream().map(asset -> toDto(asset, modelsByName)).toList();
    }

    @Transactional(readOnly = true)
    public List<QueryDatasetVersionResponse> listVersions(UUID datasetId, String activeDeptHeader) {
        QueryDatasetAsset asset = requireAsset(datasetId);
        assertReadable(asset, resolveActiveDept(activeDeptHeader));
        return versionRepository.findByDataset_IdOrderByVersionNoDesc(datasetId).stream().map(this::toVersionDto).toList();
    }

    public QueryDatasetResponse createFromExecution(UUID executionId, CreateQueryDatasetFromExecutionRequest request, String activeDeptHeader) {
        QueryExecution execution = executionRepository
            .findById(executionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查询执行记录不存在"));
        if (!StringUtils.hasText(execution.getSqlText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "执行记录缺少 SQL 文本");
        }
        UUID resultSetId = execution.getResultSetId();
        if (resultSetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "执行记录尚未生成结果集");
        }
        ResultSet resultSet = resultSetRepository
            .findById(resultSetId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "执行结果集不存在"));

        QueryDatasetAsset asset = new QueryDatasetAsset();
        asset.setName(resolveDatasetName(request, executionId));
        asset.setDescription(trimToNull(request != null ? request.description() : null));
        asset.setRefreshStrategy(normalizeRefreshStrategy(request != null ? request.refreshStrategy() : null));
        asset.setSourceDatasourceId(parseUuid(execution.getDatasource()));
        asset.setSourceDatasourceName(trimToNull(execution.getDatasource()));
        asset.setOwnerDept(resolveActiveDept(activeDeptHeader));
        asset.setSqlText(execution.getSqlText());
        asset.setLatestExecutionId(execution.getId());
        asset.setLatestResultSetId(resultSet.getId());
        asset.setStatus("DRAFT");
        asset.setEnabled(Boolean.TRUE);
        asset = assetRepository.save(asset);

        QueryDatasetVersion version = new QueryDatasetVersion();
        version.setDataset(asset);
        version.setVersionNo(1);
        version.setStatus("DRAFT");
        version.setSqlText(execution.getSqlText());
        version.setResultSetId(resultSet.getId());
        version.setExecutionId(execution.getId());
        version.setChangeSummary(trimToNull(request != null ? request.changeSummary() : null));
        versionRepository.save(version);

        return toDto(asset);
    }

    public QueryDatasetVersionResponse createVersion(UUID datasetId, CreateQueryDatasetVersionRequest request, String activeDeptHeader) {
        QueryDatasetAsset asset = requireAsset(datasetId);
        assertWritable(asset, resolveActiveDept(activeDeptHeader));
        if (request == null || !StringUtils.hasText(request.sqlText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 不能为空");
        }
        int nextVersion = versionRepository.findMaxVersionNo(datasetId) + 1;

        QueryDatasetVersion version = new QueryDatasetVersion();
        version.setDataset(asset);
        version.setVersionNo(nextVersion);
        version.setStatus(normalizeVersionStatus(request.status()));
        version.setSqlText(request.sqlText().trim());
        version.setChangeSummary(trimToNull(request.changeSummary()));
        version = versionRepository.save(version);

        asset.setSqlText(version.getSqlText());
        asset.setStatus(normalizeAssetStatus(version.getStatus()));
        assetRepository.save(asset);

        return toVersionDto(version);
    }

    public QueryDatasetVersionResponse publish(UUID datasetId, PublishQueryDatasetRequest request, String activeDeptHeader) {
        QueryDatasetAsset asset = requireAsset(datasetId);
        assertWritable(asset, resolveActiveDept(activeDeptHeader));

        QueryDatasetVersion target = resolveTargetVersion(datasetId, request != null ? request.versionNo() : null);

        List<QueryDatasetVersion> versions = versionRepository.findByDataset_IdOrderByVersionNoDesc(datasetId);
        for (QueryDatasetVersion version : versions) {
            if (version.getId().equals(target.getId())) {
                continue;
            }
            if ("PUBLISHED".equalsIgnoreCase(version.getStatus())) {
                version.setStatus("ARCHIVED");
                versionRepository.save(version);
            }
        }

        target.setStatus("PUBLISHED");
        target.setPublishedAt(Instant.now());
        if (request != null && StringUtils.hasText(request.changeSummary())) {
            target.setChangeSummary(request.changeSummary().trim());
        }
        target = versionRepository.save(target);

        asset.setPublishedVersion(target.getVersionNo());
        asset.setStatus("PUBLISHED");
        asset.setSqlText(target.getSqlText());
        asset.setLatestExecutionId(target.getExecutionId());
        asset.setLatestResultSetId(target.getResultSetId());
        assetRepository.save(asset);

        return toVersionDto(target);
    }

    public QueryDatasetResponse archive(UUID datasetId, String activeDeptHeader) {
        QueryDatasetAsset asset = requireAsset(datasetId);
        assertWritable(asset, resolveActiveDept(activeDeptHeader));
        asset.setStatus("ARCHIVED");
        asset.setEnabled(Boolean.FALSE);
        asset = assetRepository.save(asset);
        return toDto(asset);
    }

    private QueryDatasetAsset requireAsset(UUID datasetId) {
        return assetRepository
            .findById(datasetId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查询数据集不存在"));
    }

    private QueryDatasetVersion resolveTargetVersion(UUID datasetId, Integer versionNo) {
        if (versionNo != null) {
            return versionRepository
                .findByDataset_IdAndVersionNo(datasetId, versionNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定版本不存在"));
        }
        return versionRepository
            .findByDataset_IdOrderByVersionNoDesc(datasetId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据集暂无可发布版本"));
    }

    private void assertReadable(QueryDatasetAsset asset, String activeDept) {
        if (hasGlobalManageScope()) {
            return;
        }
        String ownerDept = trimToNull(asset != null ? asset.getOwnerDept() : null);
        if (ownerDept != null) {
            if (isDeptVisible(ownerDept, activeDept)) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问该查询数据集");
        }
        String login = currentLogin();
        if (!login.equalsIgnoreCase(String.valueOf(asset.getCreatedBy()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问该查询数据集");
        }
    }

    private void assertWritable(QueryDatasetAsset asset, String activeDept) {
        assertReadable(asset, activeDept);
    }

    private boolean hasGlobalManageScope() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.CATALOG_MAINTAINERS) || SecurityUtils.isOpAdminAccount();
    }

    private String currentLogin() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }

    private QueryDatasetResponse toDto(QueryDatasetAsset asset) {
        return toDto(asset, loadModelIndex());
    }

    private QueryDatasetResponse toDto(QueryDatasetAsset asset, Map<String, ModelingSqlModel> modelsByName) {
        DatasetContractInfo contractInfo = resolveDatasetContractInfo(asset, modelsByName);
        return new QueryDatasetResponse(
            asset.getId(),
            asset.getName(),
            asset.getDescription(),
            asset.getSourceDatasourceId(),
            asset.getSourceDatasourceName(),
            asset.getOwnerDept(),
            asset.getStatus(),
            asset.getRefreshStrategy(),
            asset.getPublishedVersion(),
            asset.getLatestExecutionId(),
            asset.getLatestResultSetId(),
            asset.getEnabled(),
            asset.getCreatedBy(),
            asset.getCreatedDate(),
            asset.getLastModifiedDate(),
            contractInfo.version(),
            contractInfo.modelCount(),
            contractInfo.modelNames()
        );
    }

    private Map<String, ModelingSqlModel> loadModelIndex() {
        Map<String, ModelingSqlModel> index = new LinkedHashMap<>();
        for (ModelingSqlModel model : modelingSqlModelRepository.findAll()) {
            if (model == null || !StringUtils.hasText(model.getName())) {
                continue;
            }
            index.putIfAbsent(model.getName().trim().toLowerCase(Locale.ROOT), model);
        }
        return index;
    }

    private DatasetContractInfo resolveDatasetContractInfo(QueryDatasetAsset asset, Map<String, ModelingSqlModel> modelsByName) {
        if (asset == null || modelsByName == null || modelsByName.isEmpty()) {
            return new DatasetContractInfo(null, 0, List.of());
        }
        Set<String> modelNames = extractReferencedModels(asset.getSqlText());
        if (modelNames.isEmpty()) {
            return new DatasetContractInfo(null, 0, List.of());
        }
        Set<String> contractVersions = new LinkedHashSet<>();
        List<String> resolvedNames = new ArrayList<>();
        for (String name : modelNames) {
            ModelingSqlModel model = modelsByName.get(name);
            if (model == null) {
                continue;
            }
            resolvedNames.add(model.getName());
            if (StringUtils.hasText(model.getContractVersion())) {
                contractVersions.add(model.getContractVersion().trim());
            }
        }
        resolvedNames.sort(String.CASE_INSENSITIVE_ORDER);
        if (contractVersions.isEmpty()) {
            return new DatasetContractInfo(null, resolvedNames.size(), resolvedNames);
        }
        if (contractVersions.size() == 1) {
            return new DatasetContractInfo(contractVersions.iterator().next(), resolvedNames.size(), resolvedNames);
        }
        return new DatasetContractInfo("mixed(" + contractVersions.size() + ")", resolvedNames.size(), resolvedNames);
    }

    private Set<String> extractReferencedModels(String sqlText) {
        Set<String> names = new LinkedHashSet<>();
        String sql = trimToNull(sqlText);
        if (!StringUtils.hasText(sql)) {
            return names;
        }
        String lower = sql.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from >= 0 && from < lower.length()) {
            int refStart = lower.indexOf("ref(", from);
            if (refStart < 0) {
                break;
            }
            int quoteStart = findQuoteStart(lower, refStart + 4);
            if (quoteStart < 0) {
                from = refStart + 4;
                continue;
            }
            char quote = lower.charAt(quoteStart);
            int quoteEnd = lower.indexOf(quote, quoteStart + 1);
            if (quoteEnd < 0) {
                from = quoteStart + 1;
                continue;
            }
            String name = lower.substring(quoteStart + 1, quoteEnd).trim();
            if (StringUtils.hasText(name)) {
                names.add(name);
            }
            from = quoteEnd + 1;
        }
        return names;
    }

    private int findQuoteStart(String sql, int start) {
        for (int i = Math.max(0, start); i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"') {
                return i;
            }
            if (!Character.isWhitespace(c)) {
                return -1;
            }
        }
        return -1;
    }

    private QueryDatasetVersionResponse toVersionDto(QueryDatasetVersion version) {
        return new QueryDatasetVersionResponse(
            version.getId(),
            version.getDataset() == null ? null : version.getDataset().getId(),
            version.getVersionNo(),
            version.getStatus(),
            version.getSqlText(),
            version.getChangeSummary(),
            version.getResultSetId(),
            version.getExecutionId(),
            version.getPublishedAt(),
            version.getCreatedBy(),
            version.getCreatedDate()
        );
    }

    private String resolveDatasetName(CreateQueryDatasetFromExecutionRequest request, UUID executionId) {
        String preferred = trimToNull(request != null ? request.name() : null);
        if (preferred != null) {
            return preferred;
        }
        return "query_dataset_" + executionId.toString().substring(0, 8).toLowerCase(Locale.ROOT);
    }

    private String normalizeRefreshStrategy(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return "MANUAL";
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private String normalizeVersionStatus(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return "DRAFT";
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "DRAFT", "PUBLISHED", "ARCHIVED" -> upper;
            default -> "DRAFT";
        };
    }

    private String normalizeAssetStatus(String versionStatus) {
        if (!StringUtils.hasText(versionStatus)) {
            return "DRAFT";
        }
        return switch (versionStatus.toUpperCase(Locale.ROOT)) {
            case "PUBLISHED" -> "PUBLISHED";
            case "ARCHIVED" -> "ARCHIVED";
            default -> "DRAFT";
        };
    }

    private UUID parseUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isDeptVisible(String ownerDeptRaw, String activeDeptRaw) {
        String ownerDept = trimToNull(ownerDeptRaw);
        if (ownerDept == null) {
            return true;
        }
        String activeDept = trimToNull(activeDeptRaw);
        if (activeDept == null) {
            return false;
        }
        return DepartmentUtils.matches(ownerDept, activeDept);
    }

    private String resolveActiveDept(String activeDeptHeader) {
        String header = trimToNull(activeDeptHeader);
        if (header != null) {
            return header;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (auth instanceof JwtAuthenticationToken token) {
                String claim = toClaimText(token.getToken().getClaims().get("dept_code"));
                if (claim != null) {
                    return claim;
                }
            } else if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
                String claim = toClaimText(principal.getAttribute("dept_code"));
                if (claim != null) {
                    return claim;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String toClaimText(Object raw) {
        Object value = flattenClaim(raw);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    private Object flattenClaim(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof java.util.Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            for (int i = 0; i < length; i++) {
                Object item = Array.get(raw, i);
                if (item != null) {
                    return item;
                }
            }
            return null;
        }
        return raw;
    }

    private record DatasetContractInfo(String version, int modelCount, List<String> modelNames) {}
}
