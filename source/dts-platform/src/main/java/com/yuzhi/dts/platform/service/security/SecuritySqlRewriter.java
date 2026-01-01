package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 将用户提交的 SQL 包裹在受控子查询内，并追加密级过滤条件，确保在执行前完成行级安全校验。
 */
@Component
public class SecuritySqlRewriter {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[^a-zA-Z0-9_]");
    private static final Pattern SELECT_CLAUSE_PATTERN = Pattern.compile("(?is)^\\s*select\\s+(.*?)\\s+from\\s+", Pattern.DOTALL);
    private static final Pattern GROUP_BY_PATTERN = Pattern.compile("(?is)\\bgroup\\s+by\\s+(.*?)(?=\\border\\s+by\\b|\\blimit\\b|\\bhaving\\b|\\bunion\\b|$)", Pattern.DOTALL);
    private static final Logger LOG = LoggerFactory.getLogger(SecuritySqlRewriter.class);

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
        return guard(rawSql, dataset, null);
    }

    public String guard(String rawSql, CatalogDataset dataset, String activeDeptHeader) {
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

        boolean enforceDeptFilter = !SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);
        String activeDept = resolveActiveDept(activeDeptHeader);
        if (enforceDeptFilter && !StringUtils.hasText(activeDept)) {
            throw new SecurityGuardException("当前账号未配置所属部门，无法执行查询");
        }

        String sanitizedSql = stripTrailingSemicolon(rawSql);
        Optional<String> guardColumnOpt = metadataResolver.findDataLevelColumn(dataset);
        if (guardColumnOpt.isEmpty()) {
            throw new SecurityGuardException("数据集缺少数据密级字段，无法执行查询");
        }

        String alias = resolveAlias(dataset);
        Optional<String> predicateOpt = datasetSqlBuilder.resolveDataLevelPredicate(dataset, alias);
        if (predicateOpt.isEmpty()) {
            throw new SecurityGuardException("无法生成数据密级过滤条件，无法执行查询");
        }

        String guardColumn = guardColumnOpt.orElseThrow();
        ProjectionAdjustment adjustment = ensureGuardColumnProjection(sanitizedSql, guardColumn, dataset);
        sanitizedSql = adjustment.sql();
        if (adjustment.columnAdded()) {
            sanitizedSql = ensureGroupByContainsGuard(sanitizedSql, guardColumn, dataset);
        }

        Optional<String> deptColumnOpt = metadataResolver.findDeptColumn(dataset);
        String deptColumn = null;
        if (enforceDeptFilter) {
            deptColumn = deptColumnOpt.orElseThrow(() -> new SecurityGuardException("数据集缺少部门字段，无法执行查询"));
            ProjectionAdjustment deptAdjust = ensureGuardColumnProjection(sanitizedSql, deptColumn, dataset);
            sanitizedSql = deptAdjust.sql();
            if (deptAdjust.columnAdded()) {
                sanitizedSql = ensureGroupByContainsGuard(sanitizedSql, deptColumn, dataset);
            }
        }

        String predicate = predicateOpt.orElseThrow();
        if (enforceDeptFilter) {
            String deptPredicate = datasetSqlBuilder
                .resolveDeptPredicate(dataset, alias, activeDept)
                .orElseThrow(() -> new SecurityGuardException("无法生成部门过滤条件，无法执行查询"));
            predicate = predicate + " AND " + deptPredicate;
        }
        return "SELECT * FROM (" + sanitizedSql + ") " + alias + " WHERE " + predicate;
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

    private ProjectionAdjustment ensureGuardColumnProjection(String sql, String columnName, CatalogDataset dataset) {
        if (!StringUtils.hasText(sql) || !StringUtils.hasText(columnName)) {
            return new ProjectionAdjustment(sql, false);
        }
        Matcher matcher = SELECT_CLAUSE_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return new ProjectionAdjustment(sql, false);
        }
        String selectBody = matcher.group(1);
        if (containsWildcard(selectBody) || containsColumnReference(selectBody, columnName, dataset)) {
            return new ProjectionAdjustment(sql, false);
        }
        String guardExpression = datasetSqlBuilder.quoteColumn(dataset, columnName);
        String appended = selectBody.trim().isEmpty() ? guardExpression : selectBody + ", " + guardExpression;
        String rebuilt = sql.substring(0, matcher.start(1)) + appended + sql.substring(matcher.end(1));
        return new ProjectionAdjustment(rebuilt, true);
    }

    private String ensureGroupByContainsGuard(String sql, String columnName, CatalogDataset dataset) {
        if (!StringUtils.hasText(sql) || !StringUtils.hasText(columnName)) {
            return sql;
        }
        Matcher matcher = GROUP_BY_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return sql;
        }
        String groupBody = matcher.group(1);
        if (containsColumnReference(groupBody, columnName, dataset)) {
            return sql;
        }
        String guardExpression = datasetSqlBuilder.quoteColumn(dataset, columnName);
        String trimmed = groupBody.trim();
        String appended = trimmed.isEmpty() ? guardExpression : trimmed + ", " + guardExpression;
        return sql.substring(0, matcher.start(1)) + appended + sql.substring(matcher.end(1));
    }

    private boolean containsWildcard(String selectBody) {
        if (!StringUtils.hasText(selectBody)) {
            return false;
        }
        int index = selectBody.indexOf('*');
        while (index >= 0) {
            int previous = index - 1;
            while (previous >= 0 && Character.isWhitespace(selectBody.charAt(previous))) {
                previous--;
            }
            if (previous < 0 || selectBody.charAt(previous) != '(') {
                return true;
            }
            index = selectBody.indexOf('*', index + 1);
        }
        return false;
    }

    private boolean containsColumnReference(String fragment, String columnName, CatalogDataset dataset) {
        if (!StringUtils.hasText(fragment) || !StringUtils.hasText(columnName)) {
            return false;
        }
        String normalizedFragment = normalizeSqlFragment(fragment);
        String normalizedColumn = normalizeSqlFragment(columnName);
        if (normalizedFragment.contains(normalizedColumn)) {
            return true;
        }
        String quoted = datasetSqlBuilder.quoteColumn(dataset, columnName);
        String normalizedQuoted = normalizeSqlFragment(quoted);
        if (normalizedFragment.contains(normalizedQuoted)) {
            return true;
        }
        return false;
    }

    private String normalizeSqlFragment(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("`", "")
            .replace("\"", "")
            .replace("[", "")
            .replace("]", "")
            .replace("(", "")
            .replace(")", "")
            .replace(".", "")
            .replace(",", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace("\t", "")
            .replace(" ", "")
            .toLowerCase(Locale.ROOT);
    }

    private record ProjectionAdjustment(String sql, boolean columnAdded) {}

    private String resolveActiveDept(String activeDeptHeader) {
        if (StringUtils.hasText(activeDeptHeader)) {
            String trimmed = activeDeptHeader.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return claim("dept_code");
    }

    private String claim(String name) {
        try {
            org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get(name);
                return stringifyClaim(v);
            }
            if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
                Object v = principal.getAttribute(name);
                return stringifyClaim(v);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String stringifyClaim(Object raw) {
        Object flattened = flattenClaim(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Object flattenClaim(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Collection<?> collection) {
            return collection.stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            if (length > 0) {
                Object first = Array.get(raw, 0);
                if (first != null) return first;
            }
        }
        return raw;
    }
}
