package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 将用户提交的 SQL 包裹在受控子查询内，并追加密级过滤条件，确保在执行前完成行级安全校验。
 */
@Component
public class SecuritySqlRewriter {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[^a-zA-Z0-9_]");

    private final AccessChecker accessChecker;
    private final DatasetSecurityMetadataResolver metadataResolver;
    private final DatasetSqlBuilder datasetSqlBuilder;

    public SecuritySqlRewriter(
        AccessChecker accessChecker,
        DatasetSecurityMetadataResolver metadataResolver,
        DatasetSqlBuilder datasetSqlBuilder
    ) {
        this.accessChecker = accessChecker;
        this.metadataResolver = metadataResolver;
        this.datasetSqlBuilder = datasetSqlBuilder;
    }

    /**
     * 根据给定的数据集和 SQL 语句构造带密级过滤的查询。
     *
     * @param rawSql  原始 SQL
     * @param dataset 目标数据集
     * @return 重写后的 SQL；若无需处理则返回原始 SQL
     * @throws SecurityGuardException 当无法安全执行 SQL 时抛出，例如缺失密级字段或账号未配置密级
     */
    public String guard(String rawSql, CatalogDataset dataset) {
        if (!StringUtils.hasText(rawSql)) {
            return rawSql;
        }
        if (dataset == null) {
            return stripTrailingSemicolon(rawSql);
        }

        List<DataLevel> allowedLevels = accessChecker.resolveAllowedDataLevels();
        if (allowedLevels == null || allowedLevels.isEmpty()) {
            throw new SecurityGuardException("当前账号未配置可访问的数据密级，无法执行查询");
        }
        if (metadataResolver.findDataLevelColumn(dataset).isEmpty()) {
            throw new SecurityGuardException("当前数据集未配置密级字段，无法执行查询");
        }

        String alias = resolveAlias(dataset);
        Optional<String> predicateOpt = datasetSqlBuilder.resolveDataLevelPredicate(dataset, alias);
        if (predicateOpt.isEmpty()) {
            throw new SecurityGuardException("无法构建密级过滤条件，请联系管理员检查数据集配置");
        }

        String sanitizedSql = stripTrailingSemicolon(rawSql);
        return "SELECT * FROM (" + sanitizedSql + ") " + alias + " WHERE " + predicateOpt.get();
    }

    private String resolveAlias(CatalogDataset dataset) {
        String base = dataset != null ? dataset.getHiveTable() : null;
        if (!StringUtils.hasText(base)) {
            base = dataset != null ? dataset.getName() : null;
        }
        if (!StringUtils.hasText(base) && dataset != null && dataset.getId() != null) {
            base = dataset.getId().toString();
        }
        if (!StringUtils.hasText(base)) {
            base = "security_guard";
        }
        String normalized = IDENTIFIER_PATTERN.matcher(base).replaceAll("_");
        if (normalized.isBlank()) {
            normalized = "security_guard";
        }
        normalized = normalized.toLowerCase();
        if (!Character.isLetter(normalized.charAt(0))) {
            normalized = "sg_" + normalized;
        }
        if (dataset != null && dataset.getId() != null) {
            UUID id = dataset.getId();
            String suffix = Integer.toHexString(Math.abs(id.hashCode()));
            normalized = normalized + "_" + suffix;
        }
        return normalized;
    }

    private String stripTrailingSemicolon(String sql) {
        String candidate = sql.trim();
        while (candidate.endsWith(";")) {
            candidate = candidate.substring(0, candidate.length() - 1).trim();
        }
        return candidate;
    }
}
